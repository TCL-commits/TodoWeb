package com.example.demo.service;

import com.example.demo.entity.RecurrenceType;
import com.example.demo.entity.Task;
import com.example.demo.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecurringTaskService {

    private final TaskRepository taskRepository;

    @Scheduled(cron = "0 0 1 * * *")
    public void generateRecurringTasks() {
        LocalDate today = LocalDate.now();
        List<Task> recurring = taskRepository
                .findByRecurrenceTypeNotAndNextRecurrenceDateLessThanEqual(RecurrenceType.NONE, today);

        for (Task source : recurring) {
            Task cloned = new Task();
            cloned.setTitle(source.getTitle());
            cloned.setDescription(source.getDescription());
            cloned.setProject(source.getProject());
            cloned.setStatus(source.getStatus());
            cloned.setPriority(source.getPriority());
            cloned.setApprovalStatus(source.getApprovalStatus());
            cloned.setEstimateMinutes(source.getEstimateMinutes());
            cloned.setRecurrenceType(source.getRecurrenceType());
            cloned.setRecurrenceInterval(source.getRecurrenceInterval());
            cloned.setCreatedBy(source.getCreatedBy());
            cloned.setCompleted(false);

            if (source.getRecurrenceType() == RecurrenceType.DAILY) {
                source.setNextRecurrenceDate(
                        today.plusDays(source.getRecurrenceInterval() == null ? 1 : source.getRecurrenceInterval()));
            } else if (source.getRecurrenceType() == RecurrenceType.WEEKLY) {
                source.setNextRecurrenceDate(
                        today.plusWeeks(source.getRecurrenceInterval() == null ? 1 : source.getRecurrenceInterval()));
            } else if (source.getRecurrenceType() == RecurrenceType.MONTHLY) {
                source.setNextRecurrenceDate(
                        today.plusMonths(source.getRecurrenceInterval() == null ? 1 : source.getRecurrenceInterval()));
            }

            taskRepository.save(cloned);
            taskRepository.save(source);
        }

        if (!recurring.isEmpty()) {
            log.info("Generated {} recurring tasks", recurring.size());
        }
    }
}
