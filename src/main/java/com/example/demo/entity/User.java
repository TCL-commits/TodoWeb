package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.*;
import java.util.List;

@Entity
@Data
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String username;

    @Column(unique = true, nullable = false)
    private String email;

    private String fullName;

    private String phone;

    private String avatarFilename;

    private String avatarContentType;

    @Lob
    @Basic(fetch = FetchType.LAZY)
    private byte[] avatarData;

    private String password;

    @OneToMany(mappedBy = "owner")
    private List<Workspace> ownedWorkspaces;
}
