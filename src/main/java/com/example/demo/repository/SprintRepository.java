package com.example.demo.repository;

import com.example.demo.entity.Sprint;
import com.example.demo.entity.SprintStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface SprintRepository extends JpaRepository<Sprint, Long> {
    List<Sprint> findByProjectIdOrderByStartDateAsc(Long projectId);

    List<Sprint> findByStatusAndEndDateBefore(SprintStatus status, LocalDate endDate);
}
