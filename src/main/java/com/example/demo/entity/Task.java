package com.example.demo.entity;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Data
@Table(name = "task", indexes = {
        @Index(name = "idx_task_project_status", columnList = "project_id,status_id"),
        @Index(name = "idx_task_due_completed", columnList = "due_date,is_completed"),
        @Index(name = "idx_task_created_at", columnList = "created_at"),
        @Index(name = "idx_task_sprint", columnList = "sprint_id")
})
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    private String description;

    private LocalDate dueDate;

    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority")
    private TaskPriority priority = TaskPriority.MEDIUM;

    @Enumerated(EnumType.STRING)
    @Column(name = "approval_status")
    private ApprovalStatus approvalStatus = ApprovalStatus.NONE;

    private LocalDateTime slaTargetAt;

    private Long estimateMinutes;

    private Long spentMinutes = 0L;

    @Enumerated(EnumType.STRING)
    @Column(name = "recurrence_type")
    private RecurrenceType recurrenceType = RecurrenceType.NONE;

    private Integer recurrenceInterval = 1;

    private LocalDate nextRecurrenceDate;

    @Column(length = 1200)
    private String rejectionReason;

    @Column(name = "archived")
    private Boolean archived;

    private LocalDateTime archivedAt;

    private LocalDateTime deletedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @Column(name = "is_completed")
    private Boolean completed = false;

    @ManyToOne
    private TaskStatus status;

    @JsonIgnore
    @ManyToOne
    private Project project;

    @ManyToOne
    @JoinColumn(name = "sprint_id")
    private Sprint sprint;

    @ManyToMany
    @JoinTable(name = "task_members", joinColumns = @JoinColumn(name = "task_id"), inverseJoinColumns = @JoinColumn(name = "user_id"))
    private Set<User> members = new HashSet<>();

    @ManyToOne
    @JoinColumn(name = "created_by")
    private User createdBy;

    @ManyToMany
    @JoinTable(name = "task_dependencies", joinColumns = @JoinColumn(name = "task_id"), inverseJoinColumns = @JoinColumn(name = "blocked_by_task_id"))
    private Set<Task> blockedBy = new HashSet<>();

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (spentMinutes == null) {
            spentMinutes = 0L;
        }
        if (recurrenceInterval == null) {
            recurrenceInterval = 1;
        }
        if (archived == null) {
            archived = false;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
