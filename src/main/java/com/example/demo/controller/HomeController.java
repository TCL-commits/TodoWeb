package com.example.demo.controller;

import com.example.demo.entity.User;
import com.example.demo.entity.Workspace;
import com.example.demo.repository.NotificationRepository;
import com.example.demo.repository.TaskRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.repository.WorkspaceRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;
import java.util.Collections;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Controller
public class HomeController {

    private final UserRepository userRepository;
    private final WorkspaceRepository workspaceRepository;
    private final TaskRepository taskRepository;
    private final NotificationRepository notificationRepository;

    public HomeController(UserRepository userRepository,
            WorkspaceRepository workspaceRepository,
            TaskRepository taskRepository,
            NotificationRepository notificationRepository) {
        this.userRepository = userRepository;
        this.workspaceRepository = workspaceRepository;
        this.taskRepository = taskRepository;
        this.notificationRepository = notificationRepository;
    }

    @GetMapping("/dashboard")
    public String dashboard(Authentication authentication, Model model) {
        String username = authentication.getName();
        User user = userRepository.findByUsername(username).orElseThrow();
        List<Workspace> workspaces = workspaceRepository.findDistinctByMembersUserId(user.getId());

        long totalTasks = taskRepository.countByProjectWorkspaceCreatedBy(user);
        long completedTasks = taskRepository.countByProjectWorkspaceCreatedByAndCompletedTrue(user);
        long overdueTasks = taskRepository.countByProjectWorkspaceCreatedByAndDueDateBeforeAndCompletedFalse(user,
                LocalDate.now());
        long tasksThisWeek = taskRepository.countByProjectWorkspaceCreatedByAndCreatedAtAfter(user,
                LocalDateTime.now().minusDays(7));
        long unreadNotifications = 0;
        List<?> latestNotifications = Collections.emptyList();
        try {
            unreadNotifications = notificationRepository.countByUserIdAndReadFalse(user.getId());
            latestNotifications = notificationRepository.findTop10ByUserIdOrderByCreatedAtDesc(user.getId());
        } catch (Exception ignored) {
            // Keep dashboard available even if notification schema is temporarily out of
            // sync.
        }

        model.addAttribute("username", username);
        model.addAttribute("workspaces", workspaces);
        model.addAttribute("totalTasks", totalTasks);
        model.addAttribute("completedTasks", completedTasks);
        model.addAttribute("overdueTasks", overdueTasks);
        model.addAttribute("tasksThisWeek", tasksThisWeek);
        model.addAttribute("unreadNotifications", unreadNotifications);
        model.addAttribute("latestNotifications", latestNotifications);
        return "dashboard";
    }

    @GetMapping("/")
    public String home() {
        return "redirect:/dashboard";
    }
}
