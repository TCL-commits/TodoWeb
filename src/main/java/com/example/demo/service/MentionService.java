package com.example.demo.service;

import com.example.demo.entity.Notification;
import com.example.demo.entity.Project;
import com.example.demo.entity.Task;
import com.example.demo.entity.User;
import com.example.demo.repository.NotificationRepository;
import com.example.demo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class MentionService {

    private static final Pattern MENTION_PATTERN = Pattern.compile("@([a-zA-Z0-9_.-]{2,50})");

    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;

    public Set<String> extractMentions(String text) {
        Set<String> mentions = new HashSet<>();
        if (text == null || text.isBlank()) {
            return mentions;
        }

        Matcher matcher = MENTION_PATTERN.matcher(text);
        while (matcher.find()) {
            mentions.add(matcher.group(1));
        }

        return mentions;
    }

    public int notifyMentions(Task task, User actor, String content) {
        Set<String> usernames = extractMentions(content);
        int count = 0;

        for (String username : usernames) {
            User user = userRepository.findByUsername(username).orElse(null);
            if (user == null || (actor != null && user.getId().equals(actor.getId()))) {
                continue;
            }

            Notification notification = new Notification();
            notification.setUser(user);
            notification.setTitle("Bạn được mention trong task");
            notification.setMessage(
                    "Task: " + task.getTitle() + " bởi " + (actor != null ? actor.getUsername() : "System"));
            notification.setType("mention");
            notification.setProjectId(task.getProject() != null ? task.getProject().getId() : null);
            notification.setTaskId(task.getId());
            notification.setRead(false);
            notificationRepository.save(notification);
            count++;
        }

        return count;
    }

    public int notifyMentionsForProject(Project project, User actor, String content) {
        Set<String> usernames = extractMentions(content);
        int count = 0;

        for (String username : usernames) {
            User user = userRepository.findByUsername(username).orElse(null);
            if (user == null || (actor != null && user.getId().equals(actor.getId()))) {
                continue;
            }

            Notification notification = new Notification();
            notification.setUser(user);
            notification.setTitle("Bạn được mention trong project");
            notification.setMessage(
                    "Project: " + (project != null ? project.getName() : "") + " bởi "
                            + (actor != null ? actor.getUsername() : "System"));
            notification.setType("mention");
            notification.setProjectId(project != null ? project.getId() : null);
            notification.setTaskId(null);
            notification.setRead(false);
            notificationRepository.save(notification);
            count++;
        }

        return count;
    }
}
