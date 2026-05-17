package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Data
@Table(name = "channel_messages")
public class ChannelMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "channel_id")
    private Channel channel;

    @ManyToOne(optional = false)
    @JoinColumn(name = "author_id")
    private User author;

    @Column(nullable = false, length = 2000)
    private String content;

    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null)
            createdAt = LocalDateTime.now();
    }
}
