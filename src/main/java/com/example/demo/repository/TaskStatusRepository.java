package com.example.demo.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.example.demo.entity.TaskStatus;
import java.util.List;

public interface TaskStatusRepository
        extends JpaRepository<TaskStatus, Long> {

    List<TaskStatus> findByProjectIdOrderByPosition(Long projectId);

}