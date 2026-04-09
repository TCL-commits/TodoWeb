package com.example.demo.repository;

import com.example.demo.entity.TaskCustomFieldValue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TaskCustomFieldValueRepository extends JpaRepository<TaskCustomFieldValue, Long> {
    List<TaskCustomFieldValue> findByTaskId(Long taskId);

    Optional<TaskCustomFieldValue> findByTaskIdAndCustomFieldId(Long taskId, Long customFieldId);
}
