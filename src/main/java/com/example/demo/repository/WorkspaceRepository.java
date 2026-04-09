package com.example.demo.repository;

import com.example.demo.entity.User;

import com.example.demo.entity.Workspace;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface WorkspaceRepository extends JpaRepository<Workspace, Long> {
    boolean existsByIdAndMembers_Id(Long workspaceId, Long userId);

    List<Workspace> findByCreatedBy(User user);

    List<Workspace> findDistinctByMembersUserId(Long userId);

    List<Workspace> findByCreatedByAndNameContainingIgnoreCase(User user, String keyword);

}
