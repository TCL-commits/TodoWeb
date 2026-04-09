package com.example.demo.repository;

import com.example.demo.entity.Project;
import com.example.demo.entity.Workspace;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProjectRepository extends JpaRepository<Project, Long> {

    List<Project> findByWorkspace(Workspace workspace);

    List<Project> findByWorkspace_CreatedByAndNameContainingIgnoreCase(com.example.demo.entity.User user,
            String keyword);

}
