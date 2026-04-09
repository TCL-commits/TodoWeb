package com.example.demo.service;

import com.example.demo.entity.Project;
import com.example.demo.entity.RecurrenceType;
import com.example.demo.entity.Task;
import com.example.demo.entity.TaskStatus;
import com.example.demo.repository.TaskRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecurringTaskServiceTest {

    @Mock
    private TaskRepository taskRepository;

    @InjectMocks
    private RecurringTaskService recurringTaskService;

    @Test
    void generateRecurringTasksShouldCloneAndAdvanceSchedule() {
        Task source = new Task();
        source.setId(10L);
        source.setTitle("Weekly report");
        source.setProject(new Project());
        source.setStatus(new TaskStatus());
        source.setRecurrenceType(RecurrenceType.WEEKLY);
        source.setRecurrenceInterval(1);
        source.setNextRecurrenceDate(LocalDate.now());

        when(taskRepository.findByRecurrenceTypeNotAndNextRecurrenceDateLessThanEqual(
                RecurrenceType.NONE,
                LocalDate.now())).thenReturn(List.of(source));
        when(taskRepository.save(any(Task.class))).thenAnswer(i -> i.getArgument(0));

        recurringTaskService.generateRecurringTasks();

        verify(taskRepository, atLeast(2)).save(any(Task.class));

        ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
        verify(taskRepository, atLeast(2)).save(captor.capture());

        List<Task> saved = captor.getAllValues();
        Task cloned = saved.get(0);
        Task updatedSource = saved.get(1);

        assertEquals("Weekly report", cloned.getTitle());
        assertEquals(false, cloned.getCompleted());
        assertEquals(LocalDate.now().plusWeeks(1), updatedSource.getNextRecurrenceDate());
    }
}
