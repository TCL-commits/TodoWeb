package com.example.demo.controller;

import com.example.demo.entity.Notification;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Controller
public class HomeController {

    private static final Pattern DIGIT_PATTERN = Pattern.compile("(\\d+)");

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
            latestNotifications.stream()
                    .filter(Notification.class::isInstance)
                    .map(Notification.class::cast)
                    .forEach(n -> n.setMessage(repairDigestMessage(n.getType(), n.getMessage())));
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

    private String repairDigestMessage(String type, String message) {
        if (message == null || !"digest".equalsIgnoreCase(type)) {
            return message;
        }

        if (!(message.contains("?") || message.contains("h?n") || message.contains("x? l"))) {
            return message;
        }

        Matcher matcher = DIGIT_PATTERN.matcher(message);
        String count = matcher.find() ? matcher.group(1) : "0";
        return "Bạn đang có " + count + " task quá hạn cần xử lý.";
    }
}
