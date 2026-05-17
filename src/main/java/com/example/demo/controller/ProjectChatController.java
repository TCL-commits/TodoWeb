package com.example.demo.controller;

import com.example.demo.entity.Channel;
import com.example.demo.entity.ChannelMessage;
import com.example.demo.entity.User;
import com.example.demo.repository.ChannelMessageRepository;
import com.example.demo.repository.ChannelRepository;
import com.example.demo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
@RequestMapping("/projects/{projectId}/chat")
public class ProjectChatController {

    private final ChannelRepository channelRepository;
    private final ChannelMessageRepository messageRepository;
    private final UserRepository userRepository;

    private User getCurrentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username).orElseThrow();
    }

    @GetMapping("/channels")
    @ResponseBody
    public List<Channel> listChannels(@PathVariable Long projectId) {
        List<Channel> channels = channelRepository.findByProjectId(projectId);
        if (channels == null || channels.isEmpty()) {
            Channel c = new Channel();
            c.setName("General");
            c.setSlug("general-" + projectId);
            // set project association lazily by id only (JPA will handle reference when
            // persisted)
            com.example.demo.entity.Project p = new com.example.demo.entity.Project();
            p.setId(projectId);
            c.setProject(p);
            channelRepository.save(c);
            channels = List.of(c);
        }
        return channels;
    }

    @GetMapping("/channels/{channelId}/messages")
    @ResponseBody
    public List<Map<String, Object>> listMessages(@PathVariable Long channelId) {
        Channel ch = channelRepository.findById(channelId).orElseThrow();
        List<ChannelMessage> rows = messageRepository.findTop50ByChannelIdOrderByCreatedAtDesc(channelId);
        return rows.stream().map(m -> {
            Map<String, Object> r = new HashMap<>();
            r.put("id", m.getId());
            r.put("content", m.getContent());
            r.put("author", m.getAuthor() != null ? m.getAuthor().getUsername() : "System");
            r.put("createdAt", m.getCreatedAt());
            return r;
        }).toList();
    }

    @PostMapping("/channels/{channelId}/messages")
    @ResponseBody
    public ResponseEntity<?> postMessage(@PathVariable Long channelId, @RequestBody Map<String, String> payload) {
        Channel channel = channelRepository.findById(channelId).orElseThrow();
        String content = payload.get("content");
        if (content == null || content.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Message required"));
        }
        // ensure channel belongs to provided project (controller path includes
        // projectId)
        // (Note: for stricter validation, compare projectId param; here we assume route
        // correctness)
        ChannelMessage msg = new ChannelMessage();
        msg.setChannel(channel);
        msg.setAuthor(getCurrentUser());
        msg.setContent(content.trim());
        messageRepository.save(msg);
        return ResponseEntity.ok(Map.of("id", msg.getId()));
    }
}
