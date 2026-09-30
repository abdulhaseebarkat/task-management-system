package com.slmtires.itms.service;

import com.slmtires.itms.entity.AssignmentStatus;
import com.slmtires.itms.entity.TaskStatus;
import com.slmtires.itms.exception.BadRequestException;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * The task lifecycle rules in one pure, unit-testable place.
 *
 * Task status is never set directly. It is derived from the statuses of the
 * task's current assignments (first matching rule wins):
 *   1. every assignment COMPLETED            -> COMPLETED
 *   2. any assignment BLOCKED                -> BLOCKED
 *   3. any assignment IN_PROGRESS            -> IN_PROGRESS
 *   4. every unfinished assignment ON_HOLD   -> ON_HOLD
 *   5. some assignment COMPLETED             -> IN_PROGRESS (partly done, rest waiting)
 *   6. otherwise (nobody has started)        -> REOPENED if it was reopened, else ASSIGNED
 * No current assignments at all, OR no due date yet -> DRAFT: the task is incomplete, not yet
 * a real piece of work. A DRAFT task is invisible to team members regardless of which of the two
 * is missing (see TaskLifecycleSupport#requireVisible / TaskService#listTasks) - only the Admin
 * sees it until both are filled in.
 * CANCELLED is terminal and set only by the explicit cancel action.
 */
final class TaskStatusRules {

    static final Set<AssignmentStatus> EMPLOYEE_SETTABLE =
        EnumSet.of(AssignmentStatus.IN_PROGRESS, AssignmentStatus.ON_HOLD, AssignmentStatus.BLOCKED, AssignmentStatus.COMPLETED);

    private TaskStatusRules() {
    }

    static TaskStatus derive(List<AssignmentStatus> current, TaskStatus previous, LocalDate dueDate) {
        if (current.isEmpty() || dueDate == null) {
            return TaskStatus.DRAFT;
        }
        if (current.stream().allMatch(s -> s == AssignmentStatus.COMPLETED)) {
            return TaskStatus.COMPLETED;
        }
        if (current.contains(AssignmentStatus.BLOCKED)) {
            return TaskStatus.BLOCKED;
        }
        if (current.contains(AssignmentStatus.IN_PROGRESS)) {
            return TaskStatus.IN_PROGRESS;
        }
        boolean unfinishedAllOnHold = current.stream()
            .filter(s -> s != AssignmentStatus.COMPLETED)
            .allMatch(s -> s == AssignmentStatus.ON_HOLD);
        if (unfinishedAllOnHold) {
            return TaskStatus.ON_HOLD;
        }
        if (current.contains(AssignmentStatus.COMPLETED)) {
            return TaskStatus.IN_PROGRESS;
        }
        return previous == TaskStatus.REOPENED ? TaskStatus.REOPENED : TaskStatus.ASSIGNED;
    }

    /** Where an assignment resumes when a cancelled task is reinstated (its progress is kept). */
    static AssignmentStatus statusAfterReinstate(int progress) {
        return progress > 0 ? AssignmentStatus.IN_PROGRESS : AssignmentStatus.ASSIGNED;
    }

    /**
     * Progress restored for someone when a task is reopened with "keep progress". Falls back to 99
     * when unknown (work completed before this was recorded) and never exceeds 99, since 100%
     * is only valid for COMPLETED work.
     */
    static int restoredProgress(Short progressBeforeCompletion) {
        return progressBeforeCompletion == null ? 99 : Math.min(progressBeforeCompletion, 99);
    }

    /** What a status/progress/reason update may look like - the same rules for an employee and for an Admin adjusting their work. */
    static void validateAssignmentUpdate(AssignmentStatus status, int progress, String reason) {
        if (!EMPLOYEE_SETTABLE.contains(status)) {
            throw new BadRequestException("Work can be set to In Progress, On Hold, Blocked or Completed.");
        }
        if (status == AssignmentStatus.COMPLETED && progress != 100) {
            throw new BadRequestException("Completed work must be at 100% progress.");
        }
        if (status != AssignmentStatus.COMPLETED && progress >= 100) {
            throw new BadRequestException("Progress can only reach 100% when the work is marked Completed.");
        }
        boolean reasonRequired = status == AssignmentStatus.BLOCKED || status == AssignmentStatus.ON_HOLD;
        if (reasonRequired && (reason == null || reason.isBlank())) {
            throw new BadRequestException("Please give a reason when marking work Blocked or On Hold.");
        }
    }
}
