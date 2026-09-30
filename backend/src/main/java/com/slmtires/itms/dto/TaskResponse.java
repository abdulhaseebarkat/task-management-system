package com.slmtires.itms.dto;

import com.slmtires.itms.entity.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Two views of a task. Admins get the full picture. Employees get only what they need:
 * the task itself plus the NAME, status and progress of the people sharing it - no
 * emails, codes, roles, creator, or anyone else's blocked/on-hold reason.
 */
public record TaskResponse(
    Long id,
    String taskNumber,
    String title,
    String description,
    TaskPriority priority,
    CategoryResponse category,
    TaskStatus status,
    short overallProgress,
    LocalDate dueDate,
    LocalDate originalDueDate,
    int dueDateExtensions,
    Integer reassignmentCount,
    UserResponse createdBy,
    List<AssignmentResponse> assignments
) {
    public static TaskResponse forAdmin(Task task) {
        return build(task, UserResponse.from(task.getCreatedBy()), null, task.getDueDate(), 0, null);
    }

    public static TaskResponse forEmployee(Task task, Long viewerId) {
        return build(task, null, viewerId, task.getDueDate(), 0, null);
    }

    public static TaskResponse forAdmin(Task task, LocalDate originalDueDate, int dueDateExtensions, Integer reassignmentCount) {
        return build(task, UserResponse.from(task.getCreatedBy()), null, originalDueDate, dueDateExtensions, reassignmentCount);
    }

    private static TaskResponse build(Task task, UserResponse createdBy, Long employeeViewerId, LocalDate originalDueDate, int dueDateExtensions, Integer reassignmentCount) {
        boolean admin = employeeViewerId == null;
        List<AssignmentResponse> assignments = task.getAssignments().stream()
            .filter(TaskAssignment::isCurrent)
            .map(assignment -> AssignmentResponse.from(assignment, admin || Objects.equals(assignment.getUser().getId(), employeeViewerId)))
            .toList();
        return new TaskResponse(
            task.getId(), task.getTaskNumber(), task.getTitle(), task.getDescription(), task.getPriority(),
            new CategoryResponse(task.getCategory().getId(), task.getCategory().getName()), task.getStatus(),
            task.getOverallProgress(), task.getDueDate(), originalDueDate == null ? task.getDueDate() : originalDueDate,
            dueDateExtensions, reassignmentCount, createdBy, assignments
        );
    }

    public record CategoryResponse(Long id, String name) {}

    public record AssigneeResponse(Long id, String name) {}

    public record AssignmentResponse(Long id, AssigneeResponse user, AssignmentStatus status, short progress, String reason) {
        static AssignmentResponse from(TaskAssignment assignment, boolean includeReason) {
            String reason = includeReason
                ? switch (assignment.getStatus()) {
                    case BLOCKED -> assignment.getBlockedReason();
                    case ON_HOLD -> assignment.getOnHoldReason();
                    default -> null;
                }
                : null;
            User user = assignment.getUser();
            return new AssignmentResponse(assignment.getId(), new AssigneeResponse(user.getId(), user.getName()), assignment.getStatus(), assignment.getProgress(), reason);
        }
    }
}
