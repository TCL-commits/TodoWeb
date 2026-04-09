package com.example.demo.repository;

import com.example.demo.entity.TaskChecklistItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaskChecklistItemRepository extends JpaRepository<TaskChecklistItem, Long> {
    List<TaskChecklistItem> findByTaskIdOrderByPositionAscIdAsc(Long taskId);

    long countByTaskId(Long taskId);

    long countByTaskIdAndCompletedTrue(Long taskId);
}
