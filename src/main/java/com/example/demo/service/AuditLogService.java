package com.example.demo.service;

import com.example.demo.entity.AuditLog;
import com.example.demo.entity.Task;
import com.example.demo.entity.User;
import com.example.demo.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    public void save(Task task, User actor, String action, String beforeValue, String afterValue, String comment) {
        AuditLog log = new AuditLog();
        log.setTask(task);
        log.setActor(actor);
        log.setAction(action);
        log.setBeforeValue(beforeValue);
        log.setAfterValue(afterValue);
        log.setComment(comment);
        auditLogRepository.save(log);
    }
}
