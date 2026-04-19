package com.example.demo.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.example.demo.entity.Project;
import com.example.demo.repository.ProjectRepository;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;

    public Project createProject(Project project) {
        return projectRepository.save(project);
    }
}