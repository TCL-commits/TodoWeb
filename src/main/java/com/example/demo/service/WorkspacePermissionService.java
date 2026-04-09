package com.example.demo.service;

import com.example.demo.entity.User;
import com.example.demo.entity.WorkspaceMember;
import com.example.demo.entity.WorkspaceRole;
import com.example.demo.repository.WorkspaceMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WorkspacePermissionService {

    private final WorkspaceMemberRepository workspaceMemberRepository;

    public WorkspaceRole getRole(Long workspaceId, User user) {
        if (user == null) {
            return WorkspaceRole.VIEWER;
        }

        WorkspaceMember member = workspaceMemberRepository
                .findByWorkspaceIdAndUserId(workspaceId, user.getId())
                .orElse(null);

        if (member == null) {
            return WorkspaceRole.VIEWER;
        }

        return WorkspaceRole.from(member.getRole());
    }

    public boolean canManageProjects(Long workspaceId, User user) {
        return getRole(workspaceId, user).canManageProjects();
    }

    public boolean canManageMembers(Long workspaceId, User user) {
        return getRole(workspaceId, user).canManageMembers();
    }

    public boolean isMember(Long workspaceId, User user) {
        return getRole(workspaceId, user) != WorkspaceRole.VIEWER;
    }
}
