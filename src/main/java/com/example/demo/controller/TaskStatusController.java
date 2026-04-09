package com.example.demo.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import com.example.demo.entity.Project;
import com.example.demo.entity.TaskStatus;
import com.example.demo.entity.User;
import com.example.demo.repository.ProjectRepository;
import com.example.demo.repository.TaskStatusRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.WorkspacePermissionService;
import org.springframework.security.core.Authentication;

@Controller
@RequiredArgsConstructor
public class TaskStatusController {

        private final ProjectRepository projectRepository;
        private final TaskStatusRepository taskStatusRepository;
        private final UserRepository userRepository;
        private final WorkspacePermissionService workspacePermissionService;

        @PostMapping("/workspaces/{wid}/projects/{pid}/status")
        public String addStatus(@PathVariable Long wid,
                        @PathVariable Long pid,
                        @RequestParam String name,
                        Authentication authentication) {

                User currentUser = userRepository.findByUsername(authentication.getName()).orElseThrow();
                if (!workspacePermissionService.canManageProjects(wid, currentUser)) {
                        return "redirect:/workspaces/" + wid + "/projects/" + pid;
                }

                Project project = projectRepository.findById(pid)
                                .orElseThrow();

                int nextPosition = taskStatusRepository
                                .findByProjectIdOrderByPosition(pid)
                                .size();

                TaskStatus status = new TaskStatus();
                status.setName(name);
                status.setProject(project);
                status.setPosition(nextPosition);

                taskStatusRepository.save(status);

                return "redirect:/workspaces/" + wid + "/projects/" + pid;
        }
}