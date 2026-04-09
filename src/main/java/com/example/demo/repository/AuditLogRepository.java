package com.example.demo.repository;

import com.example.demo.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    List<AuditLog> findByTaskIdOrderByCreatedAtDesc(Long taskId);

    List<AuditLog> findByTaskIdAndActionContainingIgnoreCaseOrderByCreatedAtDesc(Long taskId, String action);
}
