package com.example.demo.controller;

import com.example.demo.entity.Notification;
import com.example.demo.entity.User;
import com.example.demo.repository.NotificationRepository;
import com.example.demo.repository.TaskRepository;
import com.example.demo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/notifications")
public class NotificationController {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final TaskRepository taskRepository;

    private User currentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username).orElseThrow();
    }

    @GetMapping
    public Map<String, Object> list() {
        User user = currentUser();
        List<Notification> items = notificationRepository.findTop10ByUserIdOrderByCreatedAtDesc(user.getId());
        long unreadCount = notificationRepository.countByUserIdAndReadFalse(user.getId());

        return Map.of("items", items, "unreadCount", unreadCount);
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<?> markRead(@PathVariable Long id) {
        Notification notification = notificationRepository.findById(id).orElseThrow();
        User user = currentUser();

        if (!notification.getUser().getId().equals(user.getId())) {
            return ResponseEntity.status(403).build();
        }

        notification.setRead(true);
        notificationRepository.save(notification);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/digest/overdue")
    public Map<String, Object> generateOverdueDigest() {
        User user = currentUser();
        long overdueCount = taskRepository
                .findByProjectWorkspaceCreatedByAndDueDateBeforeAndCompletedFalse(user, LocalDate.now()).size();

        Notification digest = new Notification();
        digest.setUser(user);
        digest.setTitle("Daily overdue digest");
        digest.setMessage("Bạn đang có " + overdueCount + " task quá hạn cần xử lý.");
        digest.setType("digest");
        digest.setRead(false);
        notificationRepository.save(digest);

        return Map.of(
                "status", "created",
                "message", "Đã tạo thông báo digest trong hệ thống. Có thể nối SMTP để gửi email thật.",
                "overdueCount", overdueCount);
    }
}
