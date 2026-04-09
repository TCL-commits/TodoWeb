package com.example.demo.controller;

import com.example.demo.entity.*;
import com.example.demo.repository.*;
import com.example.demo.service.AuditLogService;
import com.example.demo.service.WorkspacePermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/advanced")
public class AdvancedPlanningController {

    private final SprintRepository sprintRepository;
    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;
    private final TaskStatusRepository taskStatusRepository;
    private final UserRepository userRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspacePermissionService workspacePermissionService;
    private final WorkspaceCustomFieldRepository workspaceCustomFieldRepository;
    private final TaskCustomFieldValueRepository taskCustomFieldValueRepository;
    private final TaskTimeEntryRepository taskTimeEntryRepository;
    private final AuditLogRepository auditLogRepository;
    private final AuditLogService auditLogService;

    private User currentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username).orElseThrow();
    }

    private ResponseEntity<Map<String, Object>> forbidden() {
        return ResponseEntity.status(403).body(Map.of("message", "Access denied"));
    }

    @PostMapping("/projects/{projectId}/sprints")
    public ResponseEntity<?> createSprint(@PathVariable Long projectId, @RequestBody Map<String, String> payload) {
        Project project = projectRepository.findById(projectId).orElseThrow();
        User user = currentUser();

        if (!workspacePermissionService.canManageProjects(project.getWorkspace().getId(), user)) {
            return forbidden();
        }

        Sprint sprint = new Sprint();
        sprint.setProject(project);
        sprint.setName(payload.getOrDefault("name", "Sprint"));
        sprint.setGoal(payload.getOrDefault("goal", ""));
        sprint.setStartDate(parseDate(payload.get("startDate"), LocalDate.now()));
        sprint.setEndDate(parseDate(payload.get("endDate"), LocalDate.now().plusDays(14)));
        sprint.setStatus(parseEnum(payload.get("status"), SprintStatus.class, SprintStatus.PLANNED));
        sprintRepository.save(sprint);

        return ResponseEntity.ok(sprint);
    }

    @GetMapping("/projects/{projectId}/sprints")
    public ResponseEntity<?> listSprints(@PathVariable Long projectId) {
        Project project = projectRepository.findById(projectId).orElseThrow();
        User user = currentUser();
        if (!workspacePermissionService.isMember(project.getWorkspace().getId(), user)) {
            return forbidden();
        }
        return ResponseEntity.ok(sprintRepository.findByProjectIdOrderByStartDateAsc(projectId));
    }

    @PostMapping("/tasks/{taskId}/sprint/{sprintId}")
    public ResponseEntity<?> assignTaskToSprint(@PathVariable Long taskId, @PathVariable Long sprintId) {
        Task task = taskRepository.findById(taskId).orElseThrow();
        Sprint sprint = sprintRepository.findById(sprintId).orElseThrow();
        User user = currentUser();

        if (!workspacePermissionService.canManageProjects(task.getProject().getWorkspace().getId(), user)) {
            return forbidden();
        }
        if (!sprint.getProject().getId().equals(task.getProject().getId())) {
            return ResponseEntity.badRequest().body(Map.of("message", "Sprint must belong to same project"));
        }

        Sprint before = task.getSprint();
        task.setSprint(sprint);
        taskRepository.save(task);
        auditLogService.save(task, user, "SPRINT_ASSIGN", before == null ? null : String.valueOf(before.getId()),
                String.valueOf(sprint.getId()), null);
        return ResponseEntity.ok(Map.of("taskId", taskId, "sprintId", sprintId));
    }

    @GetMapping("/sprints/{sprintId}/burndown")
    public ResponseEntity<?> burndown(@PathVariable Long sprintId) {
        Sprint sprint = sprintRepository.findById(sprintId).orElseThrow();
        User user = currentUser();
        if (!workspacePermissionService.isMember(sprint.getProject().getWorkspace().getId(), user)) {
            return forbidden();
        }

        LocalDate start = sprint.getStartDate() == null ? LocalDate.now() : sprint.getStartDate();
        LocalDate end = sprint.getEndDate() == null ? start.plusDays(14) : sprint.getEndDate();
        long days = Math.max(1, ChronoUnit.DAYS.between(start, end) + 1);

        List<Task> tasks = taskRepository.findBySprintIdOrderByCreatedAtAsc(sprintId);
        long total = tasks.size();

        List<Map<String, Object>> points = new ArrayList<>();
        for (int i = 0; i < days; i++) {
            LocalDate day = start.plusDays(i);
            double ideal = Math.max(0, total - ((double) total / (days - 1 == 0 ? 1 : (days - 1))) * i);
            long remaining = tasks.stream().filter(t -> !Boolean.TRUE.equals(t.getCompleted())).count();

            points.add(Map.of(
                    "date", day,
                    "idealRemaining", Math.round(ideal * 100.0) / 100.0,
                    "actualRemaining", remaining));
        }

        return ResponseEntity.ok(Map.of("sprintId", sprintId, "points", points));
    }

    @PostMapping("/tasks/{taskId}/dependencies/{blockedByTaskId}")
    public ResponseEntity<?> addDependency(@PathVariable Long taskId, @PathVariable Long blockedByTaskId) {
        Task task = taskRepository.findById(taskId).orElseThrow();
        Task blockedBy = taskRepository.findById(blockedByTaskId).orElseThrow();
        User user = currentUser();

        if (!workspacePermissionService.canManageProjects(task.getProject().getWorkspace().getId(), user)) {
            return forbidden();
        }
        if (!task.getProject().getId().equals(blockedBy.getProject().getId())) {
            return ResponseEntity.badRequest().body(Map.of("message", "Dependency must be in same project"));
        }

        task.getBlockedBy().add(blockedBy);
        taskRepository.save(task);
        auditLogService.save(task, user, "DEPENDENCY_ADD", null, String.valueOf(blockedByTaskId), null);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/tasks/{taskId}/dependencies/{blockedByTaskId}")
    public ResponseEntity<?> removeDependency(@PathVariable Long taskId, @PathVariable Long blockedByTaskId) {
        Task task = taskRepository.findById(taskId).orElseThrow();
        User user = currentUser();
        if (!workspacePermissionService.canManageProjects(task.getProject().getWorkspace().getId(), user)) {
            return forbidden();
        }

        task.getBlockedBy().removeIf(t -> t.getId().equals(blockedByTaskId));
        taskRepository.save(task);
        auditLogService.save(task, user, "DEPENDENCY_REMOVE", String.valueOf(blockedByTaskId), null, null);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/tasks/{taskId}/dependencies/graph")
    public ResponseEntity<?> dependencyGraph(@PathVariable Long taskId) {
        Task task = taskRepository.findById(taskId).orElseThrow();
        User user = currentUser();
        if (!workspacePermissionService.isMember(task.getProject().getWorkspace().getId(), user)) {
            return forbidden();
        }

        Set<Task> blockedBy = task.getBlockedBy();
        List<Map<String, Object>> nodes = new ArrayList<>();
        List<Map<String, Object>> edges = new ArrayList<>();

        nodes.add(Map.of("id", task.getId(), "label", task.getTitle(), "root", true));
        for (Task dep : blockedBy) {
            nodes.add(Map.of("id", dep.getId(), "label", dep.getTitle(), "root", false));
            edges.add(Map.of("from", task.getId(), "to", dep.getId(), "type", "blockedBy"));
        }

        return ResponseEntity.ok(Map.of("nodes", nodes, "edges", edges));
    }

    @PatchMapping("/tasks/{taskId}/priority-sla")
    public ResponseEntity<?> updatePriorityAndSla(@PathVariable Long taskId, @RequestBody Map<String, String> payload) {
        Task task = taskRepository.findById(taskId).orElseThrow();
        User user = currentUser();
        if (!workspacePermissionService.canManageProjects(task.getProject().getWorkspace().getId(), user)) {
            return forbidden();
        }

        String before = task.getPriority() + "|" + task.getSlaTargetAt();
        task.setPriority(parseEnum(payload.get("priority"), TaskPriority.class, task.getPriority()));
        if (payload.containsKey("slaTargetAt")) {
            String raw = payload.get("slaTargetAt");
            task.setSlaTargetAt(raw == null || raw.isBlank() ? null : LocalDateTime.parse(raw));
        }
        taskRepository.save(task);
        String after = task.getPriority() + "|" + task.getSlaTargetAt();
        auditLogService.save(task, user, "PRIORITY_SLA_UPDATE", before, after, null);

        return ResponseEntity.ok(task);
    }

    @GetMapping("/projects/{projectId}/quality-report")
    public ResponseEntity<?> qualityReport(@PathVariable Long projectId) {
        Project project = projectRepository.findById(projectId).orElseThrow();
        User user = currentUser();
        if (!workspacePermissionService.isMember(project.getWorkspace().getId(), user)) {
            return forbidden();
        }

        List<Task> tasks = taskRepository.findByProject(project);
        long slaBreaches = tasks.stream()
                .filter(t -> t.getSlaTargetAt() != null && !Boolean.TRUE.equals(t.getCompleted())
                        && LocalDateTime.now().isAfter(t.getSlaTargetAt()))
                .count();

        List<Task> completed = tasks.stream().filter(t -> Boolean.TRUE.equals(t.getCompleted())).toList();
        double avgLeadTimeHours = completed.isEmpty() ? 0.0
                : completed.stream()
                        .filter(t -> t.getCreatedAt() != null && t.getUpdatedAt() != null)
                        .mapToLong(t -> ChronoUnit.HOURS.between(t.getCreatedAt(), t.getUpdatedAt()))
                        .average()
                        .orElse(0.0);

        return ResponseEntity.ok(Map.of(
                "projectId", projectId,
                "slaBreaches", slaBreaches,
                "avgLeadTimeHours", Math.round(avgLeadTimeHours * 100.0) / 100.0));
    }

    @PostMapping("/workspaces/{workspaceId}/custom-fields")
    public ResponseEntity<?> createCustomField(@PathVariable Long workspaceId,
            @RequestBody Map<String, String> payload) {
        Workspace workspace = workspaceRepository.findById(workspaceId).orElseThrow();
        User user = currentUser();
        if (!workspacePermissionService.canManageProjects(workspaceId, user)) {
            return forbidden();
        }

        WorkspaceCustomField field = new WorkspaceCustomField();
        field.setWorkspace(workspace);
        field.setFieldKey(payload.getOrDefault("fieldKey", "field_" + System.currentTimeMillis()));
        field.setDisplayName(payload.getOrDefault("displayName", field.getFieldKey()));
        field.setFieldType(parseEnum(payload.get("fieldType"), CustomFieldType.class, CustomFieldType.TEXT));
        field.setOptionsJson(payload.get("optionsJson"));
        field.setRequired(Boolean.parseBoolean(payload.getOrDefault("required", "false")));
        workspaceCustomFieldRepository.save(field);

        return ResponseEntity.ok(field);
    }

    @GetMapping("/workspaces/{workspaceId}/custom-fields")
    public ResponseEntity<?> listCustomFields(@PathVariable Long workspaceId) {
        User user = currentUser();
        if (!workspacePermissionService.isMember(workspaceId, user)) {
            return forbidden();
        }
        return ResponseEntity.ok(workspaceCustomFieldRepository.findByWorkspaceIdOrderByIdAsc(workspaceId));
    }

    @PutMapping("/tasks/{taskId}/custom-fields/{fieldId}")
    public ResponseEntity<?> upsertTaskCustomField(@PathVariable Long taskId,
            @PathVariable Long fieldId,
            @RequestBody Map<String, String> payload) {

        Task task = taskRepository.findById(taskId).orElseThrow();
        WorkspaceCustomField field = workspaceCustomFieldRepository.findById(fieldId).orElseThrow();
        User user = currentUser();
        if (!workspacePermissionService.canManageProjects(task.getProject().getWorkspace().getId(), user)) {
            return forbidden();
        }

        TaskCustomFieldValue value = taskCustomFieldValueRepository
                .findByTaskIdAndCustomFieldId(taskId, fieldId)
                .orElseGet(TaskCustomFieldValue::new);
        value.setTask(task);
        value.setCustomField(field);
        value.setValue(payload.get("value"));
        taskCustomFieldValueRepository.save(value);

        return ResponseEntity.ok(value);
    }

    @GetMapping("/tasks/{taskId}/custom-fields")
    public ResponseEntity<?> getTaskCustomFields(@PathVariable Long taskId) {
        Task task = taskRepository.findById(taskId).orElseThrow();
        User user = currentUser();
        if (!workspacePermissionService.isMember(task.getProject().getWorkspace().getId(), user)) {
            return forbidden();
        }

        List<Map<String, Object>> values = taskCustomFieldValueRepository.findByTaskId(taskId).stream().map(v -> {
            Map<String, Object> row = new HashMap<>();
            row.put("fieldId", v.getCustomField().getId());
            row.put("fieldKey", v.getCustomField().getFieldKey());
            row.put("displayName", v.getCustomField().getDisplayName());
            row.put("value", v.getValue());
            return row;
        }).toList();

        return ResponseEntity.ok(values);
    }

    @PostMapping("/tasks/{taskId}/time/start")
    public ResponseEntity<?> startTimer(@PathVariable Long taskId) {
        Task task = taskRepository.findById(taskId).orElseThrow();
        User user = currentUser();
        if (!workspacePermissionService.isMember(task.getProject().getWorkspace().getId(), user)) {
            return forbidden();
        }

        if (taskTimeEntryRepository.findFirstByTaskIdAndUserIdAndEndedAtIsNullOrderByStartedAtDesc(taskId, user.getId())
                .isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Timer already running"));
        }

        TaskTimeEntry entry = new TaskTimeEntry();
        entry.setTask(task);
        entry.setUser(user);
        entry.setStartedAt(LocalDateTime.now());
        taskTimeEntryRepository.save(entry);

        return ResponseEntity.ok(entry);
    }

    @PostMapping("/tasks/{taskId}/time/stop")
    public ResponseEntity<?> stopTimer(@PathVariable Long taskId) {
        Task task = taskRepository.findById(taskId).orElseThrow();
        User user = currentUser();
        if (!workspacePermissionService.isMember(task.getProject().getWorkspace().getId(), user)) {
            return forbidden();
        }

        TaskTimeEntry entry = taskTimeEntryRepository
                .findFirstByTaskIdAndUserIdAndEndedAtIsNullOrderByStartedAtDesc(taskId, user.getId())
                .orElse(null);
        if (entry == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "No active timer"));
        }

        entry.setEndedAt(LocalDateTime.now());
        long minutes = Math.max(1, ChronoUnit.MINUTES.between(entry.getStartedAt(), entry.getEndedAt()));
        entry.setDurationMinutes(minutes);
        taskTimeEntryRepository.save(entry);

        task.setSpentMinutes((task.getSpentMinutes() == null ? 0L : task.getSpentMinutes()) + minutes);
        taskRepository.save(task);

        return ResponseEntity.ok(Map.of("durationMinutes", minutes, "spentMinutes", task.getSpentMinutes()));
    }

    @GetMapping("/projects/{projectId}/time-report")
    public ResponseEntity<?> timeReport(@PathVariable Long projectId) {
        Project project = projectRepository.findById(projectId).orElseThrow();
        User user = currentUser();
        if (!workspacePermissionService.isMember(project.getWorkspace().getId(), user)) {
            return forbidden();
        }

        List<Task> tasks = taskRepository.findByProject(project);
        Map<String, Long> byMember = new HashMap<>();

        for (Task task : tasks) {
            long spent = task.getSpentMinutes() == null ? 0L : task.getSpentMinutes();
            String owner = task.getCreatedBy() != null ? task.getCreatedBy().getUsername() : "System";
            byMember.put(owner, byMember.getOrDefault(owner, 0L) + spent);
        }

        return ResponseEntity.ok(byMember);
    }

    @PostMapping("/tasks/{taskId}/archive")
    public ResponseEntity<?> archiveTask(@PathVariable Long taskId) {
        Task task = taskRepository.findById(taskId).orElseThrow();
        User user = currentUser();
        if (!workspacePermissionService.canManageProjects(task.getProject().getWorkspace().getId(), user)) {
            return forbidden();
        }
        task.setArchived(true);
        task.setArchivedAt(LocalDateTime.now());
        taskRepository.save(task);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/tasks/{taskId}/restore")
    public ResponseEntity<?> restoreTask(@PathVariable Long taskId) {
        Task task = taskRepository.findById(taskId).orElseThrow();
        User user = currentUser();
        if (!workspacePermissionService.canManageProjects(task.getProject().getWorkspace().getId(), user)) {
            return forbidden();
        }
        task.setArchived(false);
        task.setArchivedAt(null);
        task.setDeletedAt(null);
        taskRepository.save(task);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/tasks/{taskId}")
    public ResponseEntity<?> softDeleteTask(@PathVariable Long taskId) {
        Task task = taskRepository.findById(taskId).orElseThrow();
        User user = currentUser();
        if (!workspacePermissionService.canManageProjects(task.getProject().getWorkspace().getId(), user)) {
            return forbidden();
        }
        task.setDeletedAt(LocalDateTime.now());
        taskRepository.save(task);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/projects/{projectId}/recycle-bin")
    public ResponseEntity<?> recycleBin(@PathVariable Long projectId) {
        Project project = projectRepository.findById(projectId).orElseThrow();
        User user = currentUser();
        if (!workspacePermissionService.isMember(project.getWorkspace().getId(), user)) {
            return forbidden();
        }

        List<Map<String, Object>> deletedTasks = taskRepository.findByProject(project).stream()
                .filter(t -> t.getDeletedAt() != null || Boolean.TRUE.equals(t.getArchived()))
                .map(t -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", t.getId());
                    row.put("title", t.getTitle());
                    row.put("deletedAt", t.getDeletedAt());
                    row.put("archived", Boolean.TRUE.equals(t.getArchived()));
                    return row;
                })
                .toList();

        return ResponseEntity.ok(deletedTasks);
    }

    @PostMapping("/tasks/{taskId}/approval")
    public ResponseEntity<?> updateApproval(@PathVariable Long taskId, @RequestBody Map<String, String> payload) {
        Task task = taskRepository.findById(taskId).orElseThrow();
        User user = currentUser();
        if (!workspacePermissionService.canManageProjects(task.getProject().getWorkspace().getId(), user)) {
            return forbidden();
        }

        ApprovalStatus next = parseEnum(payload.get("status"), ApprovalStatus.class, task.getApprovalStatus());
        String comment = payload.get("comment");

        if (next == ApprovalStatus.REJECTED && (comment == null || comment.isBlank())) {
            return ResponseEntity.badRequest().body(Map.of("message", "Reject requires comment"));
        }

        ApprovalStatus before = task.getApprovalStatus();
        task.setApprovalStatus(next);
        task.setRejectionReason(next == ApprovalStatus.REJECTED ? comment : null);
        taskRepository.save(task);
        auditLogService.save(task, user, "APPROVAL_STATUS", String.valueOf(before), String.valueOf(next), comment);

        return ResponseEntity.ok(task);
    }

    @GetMapping("/tasks/{taskId}/audit")
    public ResponseEntity<?> listAudit(@PathVariable Long taskId,
            @RequestParam(required = false) String action) {

        Task task = taskRepository.findById(taskId).orElseThrow();
        User user = currentUser();
        if (!workspacePermissionService.isMember(task.getProject().getWorkspace().getId(), user)) {
            return forbidden();
        }

        if (action == null || action.isBlank()) {
            return ResponseEntity.ok(auditLogRepository.findByTaskIdOrderByCreatedAtDesc(taskId));
        }
        return ResponseEntity
                .ok(auditLogRepository.findByTaskIdAndActionContainingIgnoreCaseOrderByCreatedAtDesc(taskId, action));
    }

    @GetMapping("/tasks/{taskId}/audit/export")
    public ResponseEntity<String> exportAuditCsv(@PathVariable Long taskId) {
        Task task = taskRepository.findById(taskId).orElseThrow();
        User user = currentUser();
        if (!workspacePermissionService.isMember(task.getProject().getWorkspace().getId(), user)) {
            return ResponseEntity.status(403).body("Access denied");
        }

        List<AuditLog> logs = auditLogRepository.findByTaskIdOrderByCreatedAtDesc(taskId);
        StringBuilder csv = new StringBuilder("id,action,before,after,comment,actor,createdAt\n");
        for (AuditLog log : logs) {
            csv.append(log.getId()).append(',')
                    .append(escape(log.getAction())).append(',')
                    .append(escape(log.getBeforeValue())).append(',')
                    .append(escape(log.getAfterValue())).append(',')
                    .append(escape(log.getComment())).append(',')
                    .append(escape(log.getActor() != null ? log.getActor().getUsername() : "System")).append(',')
                    .append(log.getCreatedAt())
                    .append('\n');
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=task-" + taskId + "-audit.csv")
                .body(csv.toString());
    }

    @GetMapping("/projects/{projectId}/fulltext")
    public ResponseEntity<?> fullTextSearch(@PathVariable Long projectId,
            @RequestParam String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Project project = projectRepository.findById(projectId).orElseThrow();
        User user = currentUser();
        if (!workspacePermissionService.isMember(project.getWorkspace().getId(), user)) {
            return forbidden();
        }

        String keyword = q == null ? "" : q.trim().toLowerCase();
        List<Task> matching = taskRepository
                .findByProjectIdAndArchivedFalseAndDeletedAtIsNull(projectId)
                .stream()
                .filter(t -> containsText(t.getTitle(), keyword)
                        || containsText(t.getDescription(), keyword)
                        || containsText(t.getNotes(), keyword))
                .skip((long) page * size)
                .limit(size)
                .toList();

        List<Map<String, Object>> rows = matching.stream().map(t -> {
            Map<String, Object> row = new HashMap<>();
            row.put("id", t.getId());
            row.put("title", highlight(t.getTitle(), keyword));
            row.put("description", highlight(Optional.ofNullable(t.getDescription()).orElse(""), keyword));
            row.put("score", score(t, keyword));
            return row;
        }).sorted((a, b) -> Integer.compare((Integer) b.get("score"), (Integer) a.get("score"))).toList();

        return ResponseEntity.ok(rows);
    }

    @GetMapping("/projects/{projectId}/column-tasks")
    public ResponseEntity<?> columnTasks(@PathVariable Long projectId,
            @RequestParam Long statusId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Project project = projectRepository.findById(projectId).orElseThrow();
        User user = currentUser();
        if (!workspacePermissionService.isMember(project.getWorkspace().getId(), user)) {
            return forbidden();
        }

        List<Task> rows = taskRepository
                .findByProjectIdAndStatusIdAndArchivedFalseAndDeletedAtIsNullOrderByUpdatedAtDesc(
                        projectId,
                        statusId,
                        PageRequest.of(page, size));

        return ResponseEntity.ok(rows);
    }

    @GetMapping("/workspaces/{workspaceId}/export")
    public ResponseEntity<?> exportWorkspace(@PathVariable Long workspaceId,
            @RequestParam(defaultValue = "json") String format) {
        Workspace workspace = workspaceRepository.findById(workspaceId).orElseThrow();
        User user = currentUser();
        if (!workspacePermissionService.isMember(workspaceId, user)) {
            return forbidden();
        }

        List<Project> projects = projectRepository.findByWorkspace(workspace);

        if ("csv".equalsIgnoreCase(format)) {
            StringBuilder csv = new StringBuilder("workspace,project,task,status,dueDate\n");
            for (Project project : projects) {
                List<Task> tasks = taskRepository.findByProject(project);
                for (Task task : tasks) {
                    csv.append(escape(workspace.getName())).append(',')
                            .append(escape(project.getName())).append(',')
                            .append(escape(task.getTitle())).append(',')
                            .append(escape(task.getStatus() != null ? task.getStatus().getName() : "")).append(',')
                            .append(task.getDueDate())
                            .append('\n');
                }
            }
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=workspace-" + workspaceId + ".csv")
                    .body(csv.toString());
        }

        List<Map<String, Object>> payload = projects.stream().map(project -> {
            Map<String, Object> row = new HashMap<>();
            row.put("id", project.getId());
            row.put("name", project.getName());
            row.put("tasks", taskRepository.findByProject(project));
            return row;
        }).toList();

        return ResponseEntity.ok(Map.of("workspace", workspace.getName(), "projects", payload));
    }

    @PostMapping("/workspaces/{workspaceId}/import-template")
    public ResponseEntity<?> importTemplate(@PathVariable Long workspaceId,
            @RequestBody Map<String, Object> payload) {

        Workspace workspace = workspaceRepository.findById(workspaceId).orElseThrow();
        User user = currentUser();
        if (!workspacePermissionService.canManageProjects(workspaceId, user)) {
            return forbidden();
        }

        String projectName = String.valueOf(payload.getOrDefault("projectName", "Imported Template"));
        Project project = new Project();
        project.setWorkspace(workspace);
        project.setName(projectName);
        project.setDescription(String.valueOf(payload.getOrDefault("description", "Imported from template")));
        projectRepository.save(project);

        Object statusesRaw = payload.get("statuses");
        if (statusesRaw instanceof List<?> statuses) {
            int position = 0;
            for (Object statusObj : statuses) {
                TaskStatus status = new TaskStatus();
                status.setProject(project);
                status.setName(String.valueOf(statusObj));
                status.setPosition(position++);
                taskStatusRepository.save(status);
            }
        }

        return ResponseEntity.ok(Map.of("projectId", project.getId()));
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    private boolean containsText(String value, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        return value != null && value.toLowerCase().contains(keyword);
    }

    private String highlight(String value, String keyword) {
        if (keyword == null || keyword.isBlank() || value == null) {
            return value;
        }
        return value.replaceAll("(?i)" + java.util.regex.Pattern.quote(keyword), "<mark>$0</mark>");
    }

    private int score(Task task, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return 0;
        }

        int score = 0;
        if (containsText(task.getTitle(), keyword)) {
            score += 5;
        }
        if (containsText(task.getDescription(), keyword)) {
            score += 3;
        }
        if (containsText(task.getNotes(), keyword)) {
            score += 2;
        }
        return score;
    }

    private LocalDate parseDate(String raw, LocalDate fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return LocalDate.parse(raw);
        } catch (Exception ex) {
            return fallback;
        }
    }

    private <E extends Enum<E>> E parseEnum(String raw, Class<E> type, E fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase());
        } catch (Exception ex) {
            return fallback;
        }
    }
}
