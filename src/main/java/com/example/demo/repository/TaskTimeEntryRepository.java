package com.example.demo.repository;

import com.example.demo.entity.TaskTimeEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TaskTimeEntryRepository extends JpaRepository<TaskTimeEntry, Long> {
    List<TaskTimeEntry> findByTaskIdOrderByStartedAtDesc(Long taskId);

    Optional<TaskTimeEntry> findFirstByTaskIdAndUserIdAndEndedAtIsNullOrderByStartedAtDesc(Long taskId, Long userId);
}
