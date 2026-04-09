package com.example.demo.controller;

import com.example.demo.entity.User;

import com.example.demo.entity.Project;
import com.example.demo.entity.Task;
import com.example.demo.entity.TaskStatus;
import com.example.demo.entity.Workspace;
import com.example.demo.entity.WorkspaceMember;
import com.example.demo.repository.ProjectRepository;
import com.example.demo.repository.TaskRepository;
import com.example.demo.repository.TaskStatusRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.repository.WorkspaceMemberRepository;
import com.example.demo.repository.WorkspaceRepository;
import com.example.demo.service.ProjectService;
import com.example.demo.service.WorkspacePermissionService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
@RequestMapping("/workspaces/{workspaceId}/projects")
public class ProjectController {

        private final ProjectService projectService;
        private final WorkspaceRepository workspaceRepository;
        private final ProjectRepository projectRepository;
        private final TaskRepository taskRepository;
        private final TaskStatusRepository taskStatusRepository;
        private final UserRepository userRepository;
        private final WorkspaceMemberRepository workspaceMemberRepository;
        private final WorkspacePermissionService workspacePermissionService;

        // ==========================
        // Hiển thị danh sách project
        // ==========================
        @GetMapping
        public String listProjects(@PathVariable Long workspaceId, Model model, Authentication authentication) {

                Workspace workspace = workspaceRepository
                                .findById(workspaceId)
                                .orElseThrow(() -> new RuntimeException("Workspace not found"));

                List<Project> projects = projectRepository.findByWorkspace(workspace);

                model.addAttribute("workspace", workspace);
                model.addAttribute("projects", projects);
                model.addAttribute("project", new Project());
                model.addAttribute("members", workspace.getMembers());
                model.addAttribute("username", authentication.getName());
                model.addAttribute("projectTemplates", List.of("kanban-basic", "sprint", "marketing"));
                User user = userRepository
                                .findByUsername(authentication.getName())
                                .orElseThrow();

                boolean isMember = workspaceMemberRepository
                                .existsByWorkspaceIdAndUserId(workspaceId, user.getId());

                if (!isMember) {
                        throw new RuntimeException("Access Denied");
                }

                model.addAttribute("canManage", workspacePermissionService.canManageProjects(workspaceId, user));
                return "project/list";
        }

        // ==========================
        // Tạo project mới
        // ==========================
        @PostMapping
        public String createProject(@PathVariable Long workspaceId,
                        @ModelAttribute Project project,
                        @RequestParam(defaultValue = "kanban-basic") String templateKey,
                        Authentication authentication) {

                User currentUser = userRepository.findByUsername(authentication.getName()).orElseThrow();
                if (!workspacePermissionService.canManageProjects(workspaceId, currentUser)) {
                        return "redirect:/workspaces/" + workspaceId + "/projects?projectError=forbidden";
                }

                Workspace workspace = workspaceRepository
                                .findById(workspaceId)
                                .orElseThrow(() -> new RuntimeException("Workspace not found"));

                project.setWorkspace(workspace);
                projectService.createProject(project);
                createDefaultStatuses(project, templateKey);

                return "redirect:/workspaces/" + workspaceId + "/projects";
        }

        @GetMapping("/{projectId}")
        public String viewProject(@PathVariable Long workspaceId,
                        @PathVariable Long projectId,
                        Model model,
                        Authentication authentication) {

                Workspace workspace = workspaceRepository
                                .findById(workspaceId)
                                .orElseThrow(() -> new RuntimeException("Workspace not found"));

                Project project = projectRepository
                                .findById(projectId)
                                .orElseThrow(() -> new RuntimeException("Project not found"));

                User user = userRepository
                                .findByUsername(authentication.getName())
                                .orElseThrow();

                boolean isMember = workspaceMemberRepository
                                .existsByWorkspaceIdAndUserId(workspaceId, user.getId());

                if (!isMember) {
                        throw new RuntimeException("Access Denied");
                }

                // ✅ LẤY workspace member đúng cách
                List<WorkspaceMember> members = workspaceMemberRepository.findByWorkspaceId(workspaceId);

                model.addAttribute("workspace", workspace);
                model.addAttribute("project", project);
                model.addAttribute("members", members);
                List<Task> tasks = taskRepository.findByProject(project);
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

                model.addAttribute("tasks", tasks);
                model.addAttribute("statuses", statuses);
                model.addAttribute("tasksByStatus", tasksByStatus);
                model.addAttribute("username", authentication.getName());
                model.addAttribute("canManage", workspacePermissionService.canManageProjects(workspaceId, user));

                return "task/list";
        }

        private void createDefaultStatuses(Project project, String templateKey) {
                List<String> names;

                if ("sprint".equalsIgnoreCase(templateKey)) {
                        names = List.of("Backlog", "Sprint", "Review", "Done");
                } else if ("marketing".equalsIgnoreCase(templateKey)) {
                        names = List.of("Idea", "Content", "Approval", "Published");
                } else {
                        names = List.of("Todo", "Doing", "Done");
                }

                for (int i = 0; i < names.size(); i++) {
                        TaskStatus status = new TaskStatus();
                        status.setProject(project);
                        status.setName(names.get(i));
                        status.setPosition(i);
                        taskStatusRepository.save(status);
                }
        }
}
