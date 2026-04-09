package com.example.demo.repository;

import com.example.demo.entity.Task;
import com.example.demo.entity.Project;
import com.example.demo.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface TaskRepository extends JpaRepository<Task, Long> {

    List<Task> findByProject(Project project);

    @Query("""
            SELECT DISTINCT t FROM Task t
            LEFT JOIN t.members m
            WHERE t.project.workspace.createdBy = :user
              AND (:keyword IS NULL
                OR LOWER(t.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(COALESCE(t.description, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(COALESCE(t.notes, '')) LIKE LOWER(CONCAT('%', :keyword, '%')))
              AND (:statusName IS NULL OR LOWER(t.status.name) = LOWER(:statusName))
              AND (:assigneeEmail IS NULL OR LOWER(m.email) = LOWER(:assigneeEmail))
              AND (:dueBefore IS NULL OR t.dueDate <= :dueBefore)
              AND (:dueAfter IS NULL OR t.dueDate >= :dueAfter)
            """)
    List<Task> searchByKeywordAndOwner(
            @Param("user") User user,
            @Param("keyword") String keyword,
            @Param("statusName") String statusName,
            @Param("assigneeEmail") String assigneeEmail,
            @Param("dueBefore") LocalDate dueBefore,
            @Param("dueAfter") LocalDate dueAfter);

    long countByProjectWorkspaceCreatedBy(User user);

    long countByProjectWorkspaceCreatedByAndCompletedTrue(User user);

    long countByProjectWorkspaceCreatedByAndDueDateBeforeAndCompletedFalse(User user, LocalDate date);

    long countByProjectWorkspaceCreatedByAndCreatedAtAfter(User user, LocalDateTime createdAtAfter);

    List<Task> findByProjectWorkspaceCreatedByAndDueDateBeforeAndCompletedFalse(User user, LocalDate date);

}
