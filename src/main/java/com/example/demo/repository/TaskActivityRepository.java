package com.example.demo.repository;

import com.example.demo.entity.TaskActivity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaskActivityRepository extends JpaRepository<TaskActivity, Long> {
    List<TaskActivity> findTop20ByTaskIdOrderByCreatedAtDesc(Long taskId);
}
