package com.example.demo.entity;

public enum WorkspaceRole {
    OWNER,
    ADMIN,
    MEMBER,
    VIEWER;

    public static WorkspaceRole from(String value) {
        if (value == null || value.isBlank()) {
            return MEMBER;
        }

        try {
            return WorkspaceRole.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return MEMBER;
        }
    }

    public boolean canManageMembers() {
        return this == OWNER || this == ADMIN;
    }

    public boolean canManageProjects() {
        return this == OWNER || this == ADMIN;
    }

    public boolean canViewOnly() {
        return this == VIEWER;
    }
}
