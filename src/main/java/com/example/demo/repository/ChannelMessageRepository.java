package com.example.demo.repository;

import com.example.demo.entity.ChannelMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChannelMessageRepository extends JpaRepository<ChannelMessage, Long> {
    List<ChannelMessage> findTop50ByChannelIdOrderByCreatedAtDesc(Long channelId);
}
