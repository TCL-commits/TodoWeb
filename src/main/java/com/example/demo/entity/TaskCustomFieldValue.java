package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
@Table(name = "task_custom_field_values", indexes = {
        @Index(name = "idx_tcf_task_field", columnList = "task_id,customField_id", unique = true)
})
public class TaskCustomFieldValue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "task_id")
    private Task task;

    @ManyToOne(optional = false)
    @JoinColumn(name = "customField_id")
    private WorkspaceCustomField customField;

    @Column(length = 2000)
    private String value;
}
