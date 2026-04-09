package com.example.demo.controller;

import com.example.demo.dto.TaskDTO;
import com.example.demo.dto.UserDTO;
import com.example.demo.entity.*;
import com.example.demo.repository.*;
import com.example.demo.service.WorkspacePermissionService;
import lombok.RequiredArgsConstructor;

import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
@RequestMapping("/projects/{projectId}/tasks")
public class TaskController {

    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;
    private final TaskStatusRepository taskStatusRepository;
    private final UserRepository userRepo;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final TaskChecklistItemRepository checklistRepository;
    private final TaskCommentRepository taskCommentRepository;
    private final TaskActivityRepository taskActivityRepository;
    private final NotificationRepository notificationRepository;
    private final TaskAttachmentRepository taskAttachmentRepository;
    private final WorkspacePermissionService workspacePermissionService;

    private User getCurrentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepo.findByUsername(username).orElseThrow();
    }

    private boolean canManageProject(Project project) {
        if (project == null || project.getWorkspace() == null) {
            return false;
        }
        return workspacePermissionService.canManageProjects(project.getWorkspace().getId(), getCurrentUser());
    }

    private void createActivity(Task task, User actor, String action, String detail) {
        TaskActivity activity = new TaskActivity();
        activity.setTask(task);
        activity.setActor(actor);
        activity.setAction(action);
        activity.setDetail(detail);
        taskActivityRepository.save(activity);
    }

    private void notifyUser(User user, String title, String message, String type) {
        Notification notification = new Notification();
        notification.setUser(user);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setType(type);
        notification.setRead(false);
        notificationRepository.save(notification);
    }

    @GetMapping
    public String listTasks(@PathVariable Long projectId,
            @RequestParam(required = false, defaultValue = "all") String view,
            Model model) {

        Project project = projectRepository.findById(projectId).orElseThrow();
        User currentUser = getCurrentUser();

        Workspace workspace = project.getWorkspace();

        List<Task> tasks = taskRepository.findByProject(project);
        LocalDate today = LocalDate.now();

        if ("overdue".equalsIgnoreCase(view)) {
            tasks = tasks.stream()
                    .filter(t -> !Boolean.TRUE.equals(t.getCompleted()) && t.getDueDate() != null
                            && t.getDueDate().isBefore(today))
                    .toList();
        } else if ("today".equalsIgnoreCase(view)) {
            tasks = tasks.stream()
                    .filter(t -> t.getDueDate() != null && t.getDueDate().isEqual(today))
                    .toList();
        } else if ("open".equalsIgnoreCase(view)) {
            tasks = tasks.stream()
                    .filter(t -> !Boolean.TRUE.equals(t.getCompleted()))
                    .toList();
        } else if ("done".equalsIgnoreCase(view)) {
            tasks = tasks.stream()
                    .filter(t -> Boolean.TRUE.equals(t.getCompleted()))
                    .toList();
        }
        List<TaskStatus> statuses = project.getStatuses() != null ? project.getStatuses() : List.of();
        Map<Long, List<Task>> tasksByStatus = new LinkedHashMap<>();

        for (TaskStatus status : statuses) {
            tasksByStatus.put(status.getId(), new ArrayList<>());
        }

        for (Task task : tasks) {
            if (task.getStatus() != null) {
                tasksByStatus
                        .computeIfAbsent(task.getStatus().getId(), ignored -> new ArrayList<>())
                        .add(task);
            }
        }

        model.addAttribute("project", project);
        model.addAttribute("workspace", workspace);
        model.addAttribute("task", new Task());
        model.addAttribute("members", workspace.getMembers());
        model.addAttribute("statuses", statuses);
        model.addAttribute("tasks", tasks);
        model.addAttribute("tasksByStatus", tasksByStatus);
        model.addAttribute("username", SecurityContextHolder.getContext().getAuthentication().getName());
        model.addAttribute("canManage", workspacePermissionService.canManageProjects(workspace.getId(), currentUser));
        model.addAttribute("today", today);
        model.addAttribute("view", view);

        return "task/list";
    }

    @PostMapping
    public String createTask(@PathVariable Long projectId,
            @ModelAttribute Task task) {

        Project project = projectRepository.findById(projectId).orElseThrow();
        if (!canManageProject(project)) {
            return "redirect:/projects/" + projectId + "/tasks";
        }

        TaskStatus status = taskStatusRepository
                .findById(task.getStatus().getId())
                .orElseThrow();

        // Lấy user đang login
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();

        User user = userRepo.findByUsername(username).orElseThrow();

        task.setProject(project);
        task.setStatus(status);
        task.setCreatedBy(user); // ⭐ QUAN TRỌNG
        task.setCompleted(false); // mặc định

        taskRepository.save(task);
        createActivity(task, user, "CREATE_TASK", "Tạo task mới: " + task.getTitle());

        return "redirect:/projects/" + projectId + "/tasks";
    }

    @GetMapping("/{taskId}/move")
    public String moveTask(@PathVariable Long projectId,
            @PathVariable Long taskId,
            @RequestParam Long statusId) {

        Project project = projectRepository.findById(projectId).orElseThrow();
        if (!canManageProject(project)) {
            return "redirect:/projects/" + projectId + "/tasks";
        }

        Task task = taskRepository.findById(taskId).orElseThrow();
        TaskStatus status = taskStatusRepository.findById(statusId).orElseThrow();

        task.setStatus(status);
        taskRepository.save(task);
        createActivity(task, getCurrentUser(), "MOVE_TASK", "Di chuyển task sang list: " + status.getName());

        return "redirect:/projects/" + projectId + "/tasks";
    }

    @PostMapping("/{taskId}/move")
    @ResponseBody
    public ResponseEntity<?> moveTaskAsync(@PathVariable Long projectId,
            @PathVariable Long taskId,
            @RequestParam Long statusId) {

        Project project = projectRepository.findById(projectId).orElseThrow();
        if (!canManageProject(project)) {
            return ResponseEntity.status(403).build();
        }
        Task task = taskRepository.findById(taskId).orElseThrow();
        TaskStatus status = taskStatusRepository.findById(statusId).orElseThrow();

        if (task.getProject() == null || !task.getProject().getId().equals(project.getId())) {
            return ResponseEntity.badRequest().build();
        }

        task.setStatus(status);
        taskRepository.save(task);
        createActivity(task, getCurrentUser(), "MOVE_TASK", "Di chuyển task sang list: " + status.getName());

        return ResponseEntity.ok().build();
    }

    @PostMapping("/{taskId}/toggle")
    @ResponseBody
    public TaskDTO toggleTask(@PathVariable Long taskId) {

        Task task = taskRepository.findById(taskId).orElseThrow();
        if (!canManageProject(task.getProject())) {
            throw new RuntimeException("Access denied");
        }
        task.setCompleted(!task.getCompleted());
        taskRepository.save(task);
        createActivity(task, getCurrentUser(), "TOGGLE_COMPLETE",
                task.getCompleted() ? "Đánh dấu hoàn thành" : "Mở lại task");

        return new TaskDTO(task);
    }

    @PostMapping("/{taskId}/members/{userId}")
    @ResponseBody
    public ResponseEntity<?> addMember(
            @PathVariable Long taskId,
            @PathVariable Long userId) {
        Task task = taskRepository.findById(taskId).orElseThrow();
        if (!canManageProject(task.getProject())) {
            return ResponseEntity.status(403).build();
        }
        User user = userRepo.findById(userId).orElseThrow();

        task.getMembers().add(user);
        taskRepository.save(task);
        createActivity(task, getCurrentUser(), "ASSIGN_MEMBER", "Gán thành viên: " + user.getUsername());
        notifyUser(user, "Bạn được giao task", "Task: " + task.getTitle(), "assignment");

        return ResponseEntity.ok().build();
    }

    @PostMapping("/{taskId}/members/by-email")
    @ResponseBody
    public ResponseEntity<?> addMemberByEmail(
            @PathVariable Long projectId,
            @PathVariable Long taskId,
            @RequestParam String email) {

        String normalizedEmail = email == null ? "" : email.trim().toLowerCase();
        if (normalizedEmail.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Email is required"));
        }

        Task task = taskRepository.findById(taskId).orElseThrow();
        Project project = projectRepository.findById(projectId).orElseThrow();

        if (task.getProject() == null || !task.getProject().getId().equals(project.getId())) {
            return ResponseEntity.badRequest().body(Map.of("message", "Task does not belong to this project"));
        }
        if (!canManageProject(project)) {
            return ResponseEntity.status(403).body(Map.of("message", "Bạn không có quyền giao việc trong project"));
        }

        User user = userRepo.findByEmail(normalizedEmail).orElse(null);
        if (user == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Email chưa tồn tại trong hệ thống"));
        }

        Workspace workspace = project.getWorkspace();
        boolean isWorkspaceMember = workspaceMemberRepository.existsByWorkspaceIdAndUserId(workspace.getId(),
                user.getId());

        if (!isWorkspaceMember) {
            WorkspaceMember member = new WorkspaceMember();
            member.setWorkspace(workspace);
            member.setUser(user);
            member.setRole(WorkspaceRole.MEMBER.name());
            workspaceMemberRepository.save(member);
        }

        task.getMembers().add(user);
        taskRepository.save(task);
        createActivity(task, getCurrentUser(), "ASSIGN_MEMBER", "Gán thành viên: " + user.getUsername());
        notifyUser(user, "Bạn được giao task", "Task: " + task.getTitle(), "assignment");

        Map<String, Object> response = new HashMap<>();
        response.put("id", user.getId());
        response.put("initial", user.getUsername().length() >= 2
                ? user.getUsername().substring(0, 2).toUpperCase()
                : user.getUsername().toUpperCase());
        response.put("username", user.getUsername());
        response.put("email", user.getEmail());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{taskId}/members")
    @ResponseBody
    public List<UserDTO> getMembers(@PathVariable Long taskId) {

        Task task = taskRepository.findById(taskId).orElseThrow();

        return task.getMembers().stream()
                .map(u -> {
                    String username = u.getUsername();

                    String shortName = username.length() >= 2
                            ? username.substring(0, 2).toUpperCase()
                            : username.toUpperCase();

                    return new UserDTO(u.getId(), shortName);
                })
                .toList();
    }

    @PutMapping("/{taskId}/description")
    @ResponseBody
    public TaskDTO updateDescription(@PathVariable Long taskId,
            @RequestBody Task updatedTask) {

        Task task = taskRepository.findById(taskId).orElseThrow();
        if (!canManageProject(task.getProject())) {
            throw new RuntimeException("Access denied");
        }
        task.setDescription(updatedTask.getDescription());
        taskRepository.save(task);
        createActivity(task, getCurrentUser(), "UPDATE_DESCRIPTION", "Cập nhật mô tả task");

        return new TaskDTO(task);
    }

    @PutMapping("/{taskId}/title")
    @ResponseBody
    public ResponseEntity<TaskDTO> updateTitle(@PathVariable Long taskId,
            @RequestBody Map<String, String> payload) {

        String title = payload.get("title");
        if (title == null || title.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        Task task = taskRepository.findById(taskId).orElseThrow();
        if (!canManageProject(task.getProject())) {
            return ResponseEntity.status(403).build();
        }
        task.setTitle(title.trim());
        taskRepository.save(task);
        createActivity(task, getCurrentUser(), "UPDATE_TITLE", "Đổi tiêu đề task thành: " + task.getTitle());

        return ResponseEntity.ok(new TaskDTO(task));
    }

    @PutMapping("/{taskId}/notes")
    @ResponseBody
    public TaskDTO updateNotes(@PathVariable Long taskId,
            @RequestBody Map<String, String> payload) {

        Task task = taskRepository.findById(taskId).orElseThrow();
        if (!canManageProject(task.getProject())) {
            throw new RuntimeException("Access denied");
        }
        String notes = payload.get("notes");
        task.setNotes(notes == null ? null : notes.trim());
        taskRepository.save(task);
        createActivity(task, getCurrentUser(), "UPDATE_NOTES", "Cập nhật ghi chú nhanh");

        return new TaskDTO(task);
    }

    @PutMapping("/{taskId}/due-date")
    @ResponseBody
    public ResponseEntity<TaskDTO> updateDueDate(@PathVariable Long taskId,
            @RequestBody Map<String, String> payload) {

        Task task = taskRepository.findById(taskId).orElseThrow();
        if (!canManageProject(task.getProject())) {
            return ResponseEntity.status(403).build();
        }
        String dueDateValue = payload.get("dueDate");

        if (dueDateValue == null || dueDateValue.trim().isEmpty()) {
            task.setDueDate(null);
        } else {
            task.setDueDate(LocalDate.parse(dueDateValue.trim()));
        }

        taskRepository.save(task);
        createActivity(task, getCurrentUser(), "UPDATE_DUE_DATE",
                task.getDueDate() == null ? "Xóa ngày hết hạn" : "Đặt hạn: " + task.getDueDate());

        return ResponseEntity.ok(new TaskDTO(task));
    }

    @PostMapping("/bulk-move")
    @ResponseBody
    public ResponseEntity<?> bulkMove(@PathVariable Long projectId,
            @RequestParam Long statusId,
            @RequestBody List<Long> taskIds) {

        Project project = projectRepository.findById(projectId).orElseThrow();
        if (!canManageProject(project)) {
            return ResponseEntity.status(403).build();
        }

        TaskStatus status = taskStatusRepository.findById(statusId).orElseThrow();

        for (Long taskId : taskIds) {
            Task task = taskRepository.findById(taskId).orElse(null);
            if (task == null || task.getProject() == null || !task.getProject().getId().equals(projectId)) {
                continue;
            }
            task.setStatus(status);
            taskRepository.save(task);
            createActivity(task, getCurrentUser(), "BULK_MOVE", "Bulk move sang list: " + status.getName());
        }

        return ResponseEntity.ok().build();
    }

    @GetMapping("/{taskId}/checklist")
    @ResponseBody
    public List<TaskChecklistItem> getChecklist(@PathVariable Long taskId) {
        return checklistRepository.findByTaskIdOrderByPositionAscIdAsc(taskId);
    }

    @PostMapping("/{taskId}/checklist")
    @ResponseBody
    public ResponseEntity<?> addChecklistItem(@PathVariable Long taskId,
            @RequestBody Map<String, String> payload) {

        Task task = taskRepository.findById(taskId).orElseThrow();
        if (!canManageProject(task.getProject())) {
            return ResponseEntity.status(403).build();
        }

        String title = payload.get("title");
        if (title == null || title.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Checklist title is required"));
        }

        TaskChecklistItem item = new TaskChecklistItem();
        item.setTask(task);
        item.setTitle(title.trim());
        item.setPosition((int) checklistRepository.countByTaskId(taskId));
        checklistRepository.save(item);
        createActivity(task, getCurrentUser(), "CHECKLIST_ADD", "Thêm checklist: " + item.getTitle());

        return ResponseEntity.ok(item);
    }

    @PostMapping("/{taskId}/checklist/{itemId}/toggle")
    @ResponseBody
    public ResponseEntity<?> toggleChecklistItem(@PathVariable Long taskId,
            @PathVariable Long itemId) {

        TaskChecklistItem item = checklistRepository.findById(itemId).orElseThrow();
        if (item.getTask() == null || !item.getTask().getId().equals(taskId)) {
            return ResponseEntity.badRequest().build();
        }
        if (!canManageProject(item.getTask().getProject())) {
            return ResponseEntity.status(403).build();
        }

        item.setCompleted(!item.isCompleted());
        checklistRepository.save(item);
        createActivity(item.getTask(), getCurrentUser(), "CHECKLIST_TOGGLE",
                "Checklist " + item.getTitle() + (item.isCompleted() ? " đã hoàn thành" : " mở lại"));

        return ResponseEntity.ok(item);
    }

    @DeleteMapping("/{taskId}/checklist/{itemId}")
    @ResponseBody
    public ResponseEntity<?> deleteChecklistItem(@PathVariable Long taskId,
            @PathVariable Long itemId) {

        TaskChecklistItem item = checklistRepository.findById(itemId).orElseThrow();
        if (item.getTask() == null || !item.getTask().getId().equals(taskId)) {
            return ResponseEntity.badRequest().build();
        }
        if (!canManageProject(item.getTask().getProject())) {
            return ResponseEntity.status(403).build();
        }

        checklistRepository.delete(item);
        createActivity(item.getTask(), getCurrentUser(), "CHECKLIST_DELETE", "Xóa checklist: " + item.getTitle());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{taskId}/comments")
    @ResponseBody
    public List<Map<String, Object>> listComments(@PathVariable Long taskId) {
        return taskCommentRepository.findByTaskIdOrderByCreatedAtDesc(taskId)
                .stream()
                .map(c -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", c.getId());
                    row.put("content", c.getContent());
                    row.put("author", c.getAuthor().getUsername());
                    row.put("createdAt", c.getCreatedAt());
                    row.put("updatedAt", c.getUpdatedAt());
                    return row;
                })
                .toList();
    }

    @PostMapping("/{taskId}/comments")
    @ResponseBody
    public ResponseEntity<?> addComment(@PathVariable Long taskId,
            @RequestBody Map<String, String> payload) {

        Task task = taskRepository.findById(taskId).orElseThrow();
        if (!canManageProject(task.getProject())) {
            return ResponseEntity.status(403).build();
        }

        String content = payload.get("content");
        if (content == null || content.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Comment content is required"));
        }

        User actor = getCurrentUser();
        TaskComment comment = new TaskComment();
        comment.setTask(task);
        comment.setAuthor(actor);
        comment.setContent(content.trim());
        taskCommentRepository.save(comment);
        createActivity(task, actor, "COMMENT_ADD", "Thêm bình luận mới");

        return ResponseEntity.ok(Map.of(
                "id", comment.getId(),
                "content", comment.getContent(),
                "author", actor.getUsername(),
                "createdAt", comment.getCreatedAt(),
                "updatedAt", comment.getUpdatedAt()));
    }

    @DeleteMapping("/{taskId}/comments/{commentId}")
    @ResponseBody
    public ResponseEntity<?> deleteComment(@PathVariable Long taskId,
            @PathVariable Long commentId) {

        TaskComment comment = taskCommentRepository.findById(commentId).orElseThrow();
        if (comment.getTask() == null || !comment.getTask().getId().equals(taskId)) {
            return ResponseEntity.badRequest().build();
        }

        User actor = getCurrentUser();
        boolean isOwner = comment.getAuthor() != null && comment.getAuthor().getId().equals(actor.getId());
        if (!isOwner && !canManageProject(comment.getTask().getProject())) {
            return ResponseEntity.status(403).build();
        }

        taskCommentRepository.delete(comment);
        createActivity(comment.getTask(), actor, "COMMENT_DELETE", "Xóa một bình luận");
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{taskId}/activities")
    @ResponseBody
    public List<Map<String, Object>> listActivities(@PathVariable Long taskId) {
        return taskActivityRepository.findTop20ByTaskIdOrderByCreatedAtDesc(taskId)
                .stream()
                .map(a -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", a.getId());
                    row.put("action", a.getAction());
                    row.put("detail", a.getDetail());
                    row.put("actor", a.getActor() != null ? a.getActor().getUsername() : "System");
                    row.put("createdAt", a.getCreatedAt());
                    return row;
                })
                .toList();
    }

    @GetMapping("/{taskId}/attachments")
    @ResponseBody
    public List<Map<String, Object>> listAttachments(@PathVariable Long taskId) {
        return taskAttachmentRepository.findByTaskIdOrderByCreatedAtDesc(taskId)
                .stream()
                .map(a -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", a.getId());
                    row.put("name", a.getOriginalFilename());
                    row.put("size", a.getFileSize());
                    row.put("contentType", a.getContentType());
                    row.put("createdAt", a.getCreatedAt());
                    return row;
                })
                .toList();
    }

    @PostMapping("/{taskId}/attachments")
    @ResponseBody
    public ResponseEntity<?> uploadAttachment(@PathVariable Long taskId,
            @RequestParam("file") MultipartFile file) throws IOException {

        Task task = taskRepository.findById(taskId).orElseThrow();
        if (!canManageProject(task.getProject())) {
            return ResponseEntity.status(403).build();
        }
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "File is empty"));
        }
        if (file.getSize() > 10 * 1024 * 1024) {
            return ResponseEntity.badRequest().body(Map.of("message", "File too large. Max 10MB"));
        }

        Path uploadDir = Paths.get("uploads", "task-attachments");
        Files.createDirectories(uploadDir);

        String originalFilename = file.getOriginalFilename() == null ? "attachment.bin" : file.getOriginalFilename();
        String storedFilename = UUID.randomUUID() + "_" + originalFilename;
        Path storedPath = uploadDir.resolve(storedFilename);

        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, storedPath, StandardCopyOption.REPLACE_EXISTING);
        }

        TaskAttachment attachment = new TaskAttachment();
        attachment.setTask(task);
        attachment.setUploadedBy(getCurrentUser());
        attachment.setOriginalFilename(originalFilename);
        attachment.setStoredFilename(storedFilename);
        attachment.setContentType(file.getContentType() == null ? "application/octet-stream" : file.getContentType());
        attachment.setFileSize(file.getSize());
        taskAttachmentRepository.save(attachment);

        createActivity(task, getCurrentUser(), "ATTACHMENT_ADD", "Tải lên file: " + originalFilename);

        return ResponseEntity.ok(Map.of("id", attachment.getId(), "name", originalFilename));
    }

    @GetMapping("/{taskId}/attachments/{attachmentId}")
    public ResponseEntity<Resource> downloadAttachment(@PathVariable Long taskId,
            @PathVariable Long attachmentId) throws IOException {

        TaskAttachment attachment = taskAttachmentRepository.findById(attachmentId).orElseThrow();
        if (attachment.getTask() == null || !attachment.getTask().getId().equals(taskId)) {
            return ResponseEntity.badRequest().build();
        }

        Path filePath = Paths.get("uploads", "task-attachments", attachment.getStoredFilename());
        if (!Files.exists(filePath)) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = new InputStreamResource(Files.newInputStream(filePath));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + attachment.getOriginalFilename() + "\"")
                .header(HttpHeaders.CONTENT_TYPE, attachment.getContentType())
                .body(resource);
    }

    @GetMapping("/{taskId}")
    @ResponseBody
    public TaskDTO getTask(@PathVariable Long taskId) {
        Task task = taskRepository.findById(taskId).orElseThrow();
        return new TaskDTO(task);
    }
}
