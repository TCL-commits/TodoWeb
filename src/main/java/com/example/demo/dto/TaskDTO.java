package com.example.demo.dto;

import java.time.LocalDate;

import com.example.demo.entity.Task;

public class TaskDTO {

    private Long id;
    private String title;
    private String description;
    private LocalDate dueDate;
    private String notes;
    private Boolean completed;
    private String createdBy;

    public TaskDTO(Task task) {
        this.id = task.getId();
        this.title = task.getTitle();
        this.description = task.getDescription();
        this.dueDate = task.getDueDate();
        this.notes = task.getNotes();
        this.completed = task.getCompleted();
        this.createdBy = task.getCreatedBy() != null
                ? task.getCreatedBy().getUsername()
                : null;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public String getNotes() {
        return notes;
    }

    public Boolean getCompleted() {
        return completed;
    }

    public String getCreatedBy() {
        return createdBy;
    }
}