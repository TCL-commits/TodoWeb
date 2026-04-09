package com.example.demo.entity;

import jakarta.persistence.*;
import jakarta.persistence.Index;
import lombok.Data;
import org.hibernate.annotations.Nationalized;

import java.time.LocalDateTime;

@Entity
@Data
@Table(name = "notifications", indexes = {
        @Index(name = "idx_notification_user_read", columnList = "user_id,is_read"),
        @Index(name = "idx_notification_project", columnList = "project_id"),
        @Index(name = "idx_notification_task", columnList = "task_id")
})
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Nationalized
    @Column(nullable = false, columnDefinition = "nvarchar(255)")
    private String title;

    @Nationalized
    @Column(nullable = false, length = 1000, columnDefinition = "nvarchar(1000)")
    private String message;

    @Column(nullable = false)
    private String type;

    @Column(name = "project_id")
    private Long projectId;

    @Column(name = "task_id")
    private Long taskId;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
