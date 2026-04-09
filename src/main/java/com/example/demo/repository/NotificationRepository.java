package com.example.demo.repository;

import com.example.demo.entity.Notification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findTop10ByUserIdOrderByCreatedAtDesc(Long userId);

    List<Notification> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    long countByUserIdAndReadFalse(Long userId);

    long countByUserIdAndProjectIdAndReadFalse(Long userId, Long projectId);

    long countByUserIdAndTaskIdAndReadFalse(Long userId, Long taskId);
}
