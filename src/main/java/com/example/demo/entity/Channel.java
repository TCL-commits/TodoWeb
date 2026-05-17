package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Data
@Table(name = "channels")
public class Channel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private String slug;

    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null)
            createdAt = LocalDateTime.now();
    }
}
