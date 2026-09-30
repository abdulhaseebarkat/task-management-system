package com.slmtires.itms.service;

import com.slmtires.itms.entity.*;
import com.slmtires.itms.exception.ResourceNotFoundException;
import com.slmtires.itms.repository.TaskRepository;
import com.slmtires.itms.security.AppUserPrincipal;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Shared by every task-mutating service (TaskService, TaskDueDateService,
 * TaskReassignmentService) so status derivation, visibility and locking
 * behave identically everywhere - see TaskService's private recalculate()
 * for the history of this being copy-pasted and drifting out of sync.
 */
@Component
@RequiredArgsConstructor
public class TaskLifecycleSupport {
    private final TaskRepository taskRepository;
    private final EntityManager entityManager;
    private final PointsService pointsService;

    public Task findTask(Long id) {
        return taskRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Task not found."));
    }

    /**
     * Loads the task and schedules a forced version increment on it for this
     * transaction, so two concurrent mutations of the same task - even ones
     * that don't happen to change the same column - can never both succeed;
     * the second to commit gets ObjectOptimisticLockingFailureException (409).
     *
     * Note: Spring Data's @Lock annotation only has an effect on repository
     * query methods - it is silently ignored on a plain @Component method,
     * so the lock has to be requested explicitly via the EntityManager.
     * This must run inside the caller's write transaction (propagation
     * REQUIRED, the default) so it shares the same persistence context.
     */
    @Transactional
    public Task findTaskForWrite(Long id) {
        Task task = findTask(id);
        entityManager.lock(task, LockModeType.OPTIMISTIC_FORCE_INCREMENT);
        return task;
    }

    public List<TaskAssignment> currentAssignments(Task task) {
        return task.getAssignments().stream().filter(TaskAssignment::isCurrent).toList();
    }

    public void requireVisible(Task task, AppUserPrincipal principal) {
        if (isAdmin(principal)) return;
        // A DRAFT task (missing a due date and/or assignees) is Admin-only, even if this person
        // happens to be a current assignee - it isn't a real piece of work yet.
        if (task.getStatus() == TaskStatus.DRAFT) throw new ResourceNotFoundException("Task not found.");
        boolean assigned = currentAssignments(task).stream()
            .anyMatch(item -> Objects.equals(item.getUser().getId(), principal.getId()));
        if (!assigned) throw new ResourceNotFoundException("Task not found.");
    }

    public boolean isAdmin(AppUserPrincipal principal) {
        return principal.getUser().getRole() == Role.ADMIN;
    }

    public void recalculate(Task task) {
        if (task.getStatus() == TaskStatus.CANCELLED) {
            return;
        }
        List<TaskAssignment> current = currentAssignments(task);
        task.setStatus(TaskStatusRules.derive(current.stream().map(TaskAssignment::getStatus).toList(), task.getStatus(), task.getDueDate()));

        if (task.getStatus() == TaskStatus.COMPLETED) {
            task.setOverallProgress((short) 100);
            if (task.getCompletedAt() == null) {
                task.setCompletedAt(Instant.now());
            }
        } else {
            task.setOverallProgress((short) Math.round(current.stream().mapToInt(TaskAssignment::getProgress).average().orElse(0)));
            task.setCompletedAt(null);
        }

        // Phase 9: may seal STRIKE_1/STRIKE_2/FAILED/COMPLETED points events and, on a fresh 3rd
        // strike, override status to FAILED (never overriding a genuine COMPLETED this same pass).
        pointsService.evaluateAndSeal(task);
    }

    public TaskAssignment currentAssignment(Task task, Long assignmentId) {
        return currentAssignments(task).stream()
            .filter(item -> Objects.equals(item.getId(), assignmentId))
            .findFirst()
            .orElseThrow(() -> new ResourceNotFoundException("Assignment not found."));
    }
}
