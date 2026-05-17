package com.example.demo.controller;

import com.example.demo.entity.Channel;
import com.example.demo.entity.ChannelMessage;
import com.example.demo.entity.User;
import com.example.demo.repository.ChannelMessageRepository;
import com.example.demo.repository.ChannelRepository;
import com.example.demo.service.MentionService;
import com.example.demo.service.RealtimeEventService;
import com.example.demo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;

import java.util.HashMap;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class ChatMessageEndpoint {

    private final ChannelRepository channelRepository;
    private final ChannelMessageRepository messageRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final MentionService mentionService;
    private final RealtimeEventService realtimeEventService;

    private User getCurrentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username).orElseThrow();
    }

    @MessageMapping("/projects/{projectId}/chat/channels/{channelId}/message")
    public void receiveMessage(@DestinationVariable Long projectId,
            @DestinationVariable Long channelId,
            @Payload Map<String, String> payload) {

        String content = payload.get("content");
        if (content == null || content.trim().isEmpty())
            return;

        Channel channel = channelRepository.findById(channelId).orElseThrow();

        ChannelMessage msg = new ChannelMessage();
        msg.setChannel(channel);
        msg.setAuthor(getCurrentUser());
        msg.setContent(content.trim());
        messageRepository.save(msg);

        Map<String, Object> out = new HashMap<>();
        out.put("id", msg.getId());
        out.put("author", msg.getAuthor().getUsername());
        out.put("content", msg.getContent());
        out.put("createdAt", msg.getCreatedAt());

        // broadcast to topic for this project/channel
        String destination = String.format("/topic/projects.%d.channels.%d", projectId, channelId);
        messagingTemplate.convertAndSend(destination, (Object) out);

        // notify mentions (creates Notification rows)
        mentionService.notifyMentionsForProject(channel.getProject(), getCurrentUser(), msg.getContent());

        // publish lightweight realtime notification via SSE
        realtimeEventService.publish("notification", Map.of(
                "type", "mention_check",
                "projectId", projectId,
                "channelId", channelId,
                "messageId", msg.getId()));
    }
}
