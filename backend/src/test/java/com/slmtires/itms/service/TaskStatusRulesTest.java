package com.slmtires.itms.service;

import com.slmtires.itms.entity.AssignmentStatus;
import com.slmtires.itms.entity.TaskStatus;
import com.slmtires.itms.exception.BadRequestException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static com.slmtires.itms.entity.AssignmentStatus.*;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TaskStatusRulesTest {

    /** Far enough out that no test here is about the due date itself - it exists so these cases
     * exercise only the assignment-status-driven rules, same as before the due date was added. */
    private static final LocalDate SOME_DUE_DATE = LocalDate.of(2099, 1, 1);

    private static TaskStatus derive(TaskStatus previous, AssignmentStatus... statuses) {
        return TaskStatusRules.derive(List.of(statuses), previous, SOME_DUE_DATE);
    }

    @Test
    void noAssigneesIsDraft() {
        assertEquals(TaskStatus.DRAFT, TaskStatusRules.derive(List.of(), TaskStatus.IN_PROGRESS, SOME_DUE_DATE));
    }

    @Test
    void missingDueDateIsDraftEvenWithAssignees() {
        assertEquals(TaskStatus.DRAFT, TaskStatusRules.derive(List.of(ASSIGNED), TaskStatus.ASSIGNED, null));
        assertEquals(TaskStatus.DRAFT, TaskStatusRules.derive(List.of(IN_PROGRESS), TaskStatus.IN_PROGRESS, null));
    }

    @Test
    void assigneesAndDueDateBothPresentEscapesDraft() {
        assertEquals(TaskStatus.ASSIGNED, TaskStatusRules.derive(List.of(ASSIGNED), TaskStatus.DRAFT, SOME_DUE_DATE));
    }

    @Test
    void everyoneCompletedIsCompleted() {
        assertEquals(TaskStatus.COMPLETED, derive(TaskStatus.IN_PROGRESS, COMPLETED, COMPLETED));
    }

    @Test
    void addingAnUnstartedAssigneeToACompletedTaskReopensProgress() {
        assertEquals(TaskStatus.IN_PROGRESS, derive(TaskStatus.COMPLETED, COMPLETED, ASSIGNED));
    }

    @Test
    void blockedBeatsInProgress() {
        assertEquals(TaskStatus.BLOCKED, derive(TaskStatus.ASSIGNED, BLOCKED, IN_PROGRESS, ASSIGNED));
    }

    @Test
    void anyoneWorkingMakesTheTaskInProgress() {
        assertEquals(TaskStatus.IN_PROGRESS, derive(TaskStatus.ASSIGNED, ASSIGNED, IN_PROGRESS));
        assertEquals(TaskStatus.IN_PROGRESS, derive(TaskStatus.ASSIGNED, ON_HOLD, IN_PROGRESS));
    }

    @Test
    void everyoneOnHoldIsOnHold() {
        assertEquals(TaskStatus.ON_HOLD, derive(TaskStatus.ASSIGNED, ON_HOLD, ON_HOLD));
    }

    @Test
    void finishedPlusOnHoldIsOnHold() {
        assertEquals(TaskStatus.ON_HOLD, derive(TaskStatus.IN_PROGRESS, COMPLETED, ON_HOLD));
    }

    @Test
    void onHoldMixedWithUnstartedIsStillAssigned() {
        assertEquals(TaskStatus.ASSIGNED, derive(TaskStatus.ASSIGNED, ON_HOLD, ASSIGNED));
    }

    @Test
    void nobodyStartedIsAssigned() {
        assertEquals(TaskStatus.ASSIGNED, derive(TaskStatus.DRAFT, ASSIGNED, ASSIGNED));
    }

    @Test
    void reopenedStaysReopenedUntilWorkResumes() {
        assertEquals(TaskStatus.REOPENED, derive(TaskStatus.REOPENED, ASSIGNED, ASSIGNED));
        assertEquals(TaskStatus.IN_PROGRESS, derive(TaskStatus.REOPENED, IN_PROGRESS, ASSIGNED));
    }

    @Test
    void employeeCannotSetSystemStatuses() {
        for (AssignmentStatus status : List.of(ASSIGNED, REASSIGNED, CANCELLED, REMOVED)) {
            assertThrows(BadRequestException.class, () -> TaskStatusRules.validateAssignmentUpdate(status, 0, null));
        }
    }

    @Test
    void completedRequiresFullProgress() {
        assertThrows(BadRequestException.class, () -> TaskStatusRules.validateAssignmentUpdate(COMPLETED, 99, null));
        assertDoesNotThrow(() -> TaskStatusRules.validateAssignmentUpdate(COMPLETED, 100, null));
    }

    @Test
    void fullProgressRequiresCompleted() {
        assertThrows(BadRequestException.class, () -> TaskStatusRules.validateAssignmentUpdate(IN_PROGRESS, 100, null));
        assertThrows(BadRequestException.class, () -> TaskStatusRules.validateAssignmentUpdate(BLOCKED, 100, "x"));
    }

    @Test
    void blockedAndOnHoldNeedAReason() {
        assertThrows(BadRequestException.class, () -> TaskStatusRules.validateAssignmentUpdate(BLOCKED, 30, null));
        assertThrows(BadRequestException.class, () -> TaskStatusRules.validateAssignmentUpdate(ON_HOLD, 30, "   "));
        assertDoesNotThrow(() -> TaskStatusRules.validateAssignmentUpdate(BLOCKED, 30, "Waiting for hardware"));
        assertDoesNotThrow(() -> TaskStatusRules.validateAssignmentUpdate(ON_HOLD, 0, "Vendor dependency"));
    }

    @Test
    void inProgressWithPartialProgressIsFine() {
        assertDoesNotThrow(() -> TaskStatusRules.validateAssignmentUpdate(IN_PROGRESS, 0, null));
        assertDoesNotThrow(() -> TaskStatusRules.validateAssignmentUpdate(IN_PROGRESS, 99, null));
    }

    @Test
    void reinstatedWorkResumesWhereItStopped() {
        assertEquals(ASSIGNED, TaskStatusRules.statusAfterReinstate(0));
        assertEquals(IN_PROGRESS, TaskStatusRules.statusAfterReinstate(29));
        assertEquals(IN_PROGRESS, TaskStatusRules.statusAfterReinstate(99));
    }

    @Test
    void reopenWithKeepProgressRestoresPreCompletionProgress() {
        assertEquals(60, TaskStatusRules.restoredProgress((short) 60));
        assertEquals(0, TaskStatusRules.restoredProgress((short) 0));
    }

    @Test
    void restoredProgressIsNeverFullAndFallsBackWhenUnknown() {
        assertEquals(99, TaskStatusRules.restoredProgress(null));
        assertEquals(99, TaskStatusRules.restoredProgress((short) 100));
    }
}
