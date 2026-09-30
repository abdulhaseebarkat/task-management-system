package com.slmtires.itms.entity;

public enum TaskStatus {
    DRAFT,
    ASSIGNED,
    IN_PROGRESS,
    ON_HOLD,
    BLOCKED,
    COMPLETED,
    CANCELLED,
    REOPENED,
    /** A current assignment on this task hit its 3rd missed deadline (Phase 9 scoring). Advisory,
     * not a hard stop - the Admin can still reassign or extend again. Set only while the task
     * isn't already COMPLETED; if the work is finished later anyway, status moves on normally
     * (this flag is about deadline discipline, not whether the work ever gets done), while the
     * points ledger permanently keeps the 0-point outcome for whoever missed the 3rd deadline. */
    FAILED
}
