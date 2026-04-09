package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
@Table(name = "workspace_custom_fields", indexes = {
        @Index(name = "idx_cf_workspace_key", columnList = "workspace_id,fieldKey", unique = true)
})
public class WorkspaceCustomField {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "workspace_id")
    private Workspace workspace;

    @Column(nullable = false)
    private String fieldKey;

    @Column(nullable = false)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CustomFieldType fieldType;

    @Column(length = 1000)
    private String optionsJson;

    @Column(nullable = false)
    private boolean required;
}
