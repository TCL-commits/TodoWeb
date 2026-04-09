package com.example.demo.controller;

import com.example.demo.entity.Project;
import com.example.demo.entity.Task;
import com.example.demo.entity.TaskStatus;
import com.example.demo.entity.User;
import com.example.demo.entity.Workspace;
import com.example.demo.repository.ProjectRepository;
import com.example.demo.repository.TaskRepository;
import com.example.demo.repository.TaskStatusRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.repository.WorkspaceMemberRepository;
import com.example.demo.repository.WorkspaceRepository;
import com.example.demo.service.WebhookIntegrationService;
import com.example.demo.service.WorkspacePermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class ApiV1Controller {

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;
    private final TaskStatusRepository taskStatusRepository;
    private final UserRepository userRepository;
    private final WorkspacePermissionService workspacePermissionService;
    private final WebhookIntegrationService webhookIntegrationService;

    private User currentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username).orElseThrow();
    }

    private boolean isWorkspaceMember(Long workspaceId, User user) {
        return workspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, user.getId());
    }

    @GetMapping("/workspaces")
    public List<Map<String, Object>> listWorkspaces() {
        User user = currentUser();

        return workspaceRepository.findDistinctByMembersUserId(user.getId())
                .stream()
                .map(w -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("id", w.getId());
                    item.put("name", w.getName());
                    item.put("type", w.getType());
                    item.put("role", workspacePermissionService.getRole(w.getId(), user).name());
                    return item;
                })
                .toList();
    }

    @GetMapping("/workspaces/{workspaceId}/projects")
    public ResponseEntity<?> listProjects(@PathVariable Long workspaceId) {
        User user = currentUser();
        if (!isWorkspaceMember(workspaceId, user)) {
            return ResponseEntity.status(403).body(Map.of("message", "Access denied"));
        }

        Workspace workspace = workspaceRepository.findById(workspaceId).orElseThrow();
        List<Map<String, Object>> projects = projectRepository.findByWorkspace(workspace)
                .stream()
                .map(p -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("id", p.getId());
                    item.put("name", p.getName());
                    item.put("description", p.getDescription());
                    item.put("statusCount", p.getStatuses() == null ? 0 : p.getStatuses().size());
                    item.put("taskCount", p.getTasks() == null ? 0 : p.getTasks().size());
                    return item;
                })
                .toList();

        return ResponseEntity.ok(projects);
    }

    @GetMapping("/projects/{projectId}/tasks")
    public ResponseEntity<?> listTasks(@PathVariable Long projectId) {
        User user = currentUser();
        Project project = projectRepository.findById(projectId).orElseThrow();
        Long workspaceId = project.getWorkspace().getId();

        if (!isWorkspaceMember(workspaceId, user)) {
            return ResponseEntity.status(403).body(Map.of("message", "Access denied"));
        }

        List<Map<String, Object>> tasks = taskRepository.findByProject(project)
                .stream()
                .map(t -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", t.getId());
                    row.put("title", t.getTitle());
                    row.put("description", t.getDescription());
                    row.put("completed", t.getCompleted());
                    row.put("dueDate", t.getDueDate());
                    row.put("status", t.getStatus() != null ? t.getStatus().getName() : null);
                    row.put("createdBy", t.getCreatedBy() != null ? t.getCreatedBy().getUsername() : null);
                    return row;
                })
                .toList();

        return ResponseEntity.ok(tasks);
    }

    @PostMapping("/projects/{projectId}/tasks")
    public ResponseEntity<?> createTask(@PathVariable Long projectId,
            @RequestBody Map<String, String> payload) {

        User user = currentUser();
        Project project = projectRepository.findById(projectId).orElseThrow();
        Long workspaceId = project.getWorkspace().getId();

        if (!workspacePermissionService.canManageProjects(workspaceId, user)) {
            return ResponseEntity.status(403).body(Map.of("message", "Access denied"));
        }

        String title = payload.get("title");
        if (title == null || title.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "title is required"));
        }

        Task task = new Task();
        task.setTitle(title.trim());
        task.setDescription(payload.getOrDefault("description", null));
        task.setProject(project);
        task.setCreatedBy(user);
        task.setCompleted(false);

        String statusIdValue = payload.get("statusId");
        if (statusIdValue != null && !statusIdValue.isBlank()) {
            Long statusId = Long.parseLong(statusIdValue);
            TaskStatus status = taskStatusRepository.findById(statusId).orElseThrow();
            if (status.getProject() != null && status.getProject().getId().equals(projectId)) {
                task.setStatus(status);
            }
        }

        String dueDateValue = payload.get("dueDate");
        if (dueDateValue != null && !dueDateValue.isBlank()) {
            task.setDueDate(LocalDate.parse(dueDateValue));
        }

        taskRepository.save(task);

        webhookIntegrationService.publish("task.created", Map.of(
                "projectId", projectId,
                "taskId", task.getId(),
                "title", task.getTitle(),
                "createdBy", user.getUsername()));

        return ResponseEntity.ok(Map.of("id", task.getId(), "title", task.getTitle()));
    }

    @PatchMapping("/projects/{projectId}/tasks/{taskId}")
    public ResponseEntity<?> updateTask(@PathVariable Long projectId,
            @PathVariable Long taskId,
            @RequestBody Map<String, String> payload) {

        User user = currentUser();
        Project project = projectRepository.findById(projectId).orElseThrow();
        if (!workspacePermissionService.canManageProjects(project.getWorkspace().getId(), user)) {
            return ResponseEntity.status(403).body(Map.of("message", "Access denied"));
        }

        Task task = taskRepository.findById(taskId).orElseThrow();
        if (task.getProject() == null || !task.getProject().getId().equals(projectId)) {
            return ResponseEntity.badRequest().body(Map.of("message", "Task does not belong to project"));
        }

        if (payload.containsKey("title")) {
            String title = payload.get("title");
            if (title != null && !title.isBlank()) {
                task.setTitle(title.trim());
            }
        }

        if (payload.containsKey("description")) {
            task.setDescription(payload.get("description"));
        }

        if (payload.containsKey("completed")) {
            task.setCompleted(Boolean.parseBoolean(payload.get("completed")));
        }

        if (payload.containsKey("dueDate")) {
            String dueDateValue = payload.get("dueDate");
            task.setDueDate((dueDateValue == null || dueDateValue.isBlank()) ? null : LocalDate.parse(dueDateValue));
        }

        if (payload.containsKey("statusId")) {
            String statusIdValue = payload.get("statusId");
            if (statusIdValue == null || statusIdValue.isBlank()) {
                task.setStatus(null);
            } else {
                Long statusId = Long.parseLong(statusIdValue);
                TaskStatus status = taskStatusRepository.findById(statusId).orElseThrow();
                if (status.getProject() == null || !status.getProject().getId().equals(projectId)) {
                    return ResponseEntity.badRequest().body(Map.of("message", "Status does not belong to project"));
                }
                task.setStatus(status);
            }
        }

        taskRepository.save(task);

        webhookIntegrationService.publish("task.updated", Map.of(
                "projectId", projectId,
                "taskId", task.getId(),
                "title", task.getTitle(),
                "updatedBy", user.getUsername()));

        return ResponseEntity.ok(Map.of(
                "id", task.getId(),
                "title", task.getTitle(),
                "description", task.getDescription(),
                "completed", task.getCompleted(),
                "dueDate", task.getDueDate(),
                "status", task.getStatus() != null ? task.getStatus().getName() : null));
    }

    @PostMapping("/integrations/webhook/test")
    public Map<String, Object> testWebhook() {
        User user = currentUser();
        webhookIntegrationService.publish("integration.webhook.test", Map.of(
                "triggeredBy", user.getUsername(),
                "message", "Webhook connectivity test"));

        return Map.of("status", "sent");
    }
}
