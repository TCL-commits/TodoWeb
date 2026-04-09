package com.example.demo.repository;

import com.example.demo.entity.WorkspaceCustomField;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorkspaceCustomFieldRepository extends JpaRepository<WorkspaceCustomField, Long> {
    List<WorkspaceCustomField> findByWorkspaceIdOrderByIdAsc(Long workspaceId);

    Optional<WorkspaceCustomField> findByWorkspaceIdAndFieldKey(Long workspaceId, String fieldKey);
}
