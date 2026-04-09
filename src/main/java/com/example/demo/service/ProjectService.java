package com.example.demo.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.example.demo.entity.Project;
import com.example.demo.entity.TaskStatus;
import com.example.demo.repository.ProjectRepository;
import com.example.demo.repository.TaskStatusRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final TaskStatusRepository taskStatusRepository;

    public Project createProject(Project project) {

        // 1. Lưu project trước
        Project savedProject = projectRepository.save(project);

        // 2. Tạo 3 status mặc định giống Trello
        createDefaultStatuses(savedProject);

        return savedProject;
    }

    private void createDefaultStatuses(Project project) {

        List<String> defaults = List.of("Todo", "Doing", "Done");

        int pos = 0;
        for (String name : defaults) {
            TaskStatus status = new TaskStatus();
            status.setName(name);
            status.setProject(project);
            status.setPosition(pos++);
            taskStatusRepository.save(status);
        }
    }
}