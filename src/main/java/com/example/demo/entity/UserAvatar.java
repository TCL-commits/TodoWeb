package com.example.demo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Data
@Table(name = "user_avatars")
public class UserAvatar {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "filename")
    private String filename;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "data", columnDefinition = "bytea", nullable = false)
    private byte[] data;
}
