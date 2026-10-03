package com.slmtires.itms.entity;

public enum PointsEventType {
    /** Starts a scoring cycle for an assignment - base value set. Written on initial assignment,
     * on becoming the new assignee of a reassignment, and on reopen/reinstate (a fresh cycle). */
    ASSIGNED,
    /** 1st deadline missed within this cycle - resulting points drop to 50% of base. */
    STRIKE_1,
    /** 2nd deadline missed within this cycle - resulting points drop to 25% of base. */
    STRIKE_2,
    /** 3rd deadline missed - terminal for scoring, 0 points, permanent regardless of what happens next. */
    FAILED,
    /** Assignment completed before a 3rd strike - locks in whatever the strike level left them. */
    COMPLETED,
    /** Task cancelled while this assignment was open - neutral, excluded from scoring entirely. */
    CANCELLED,
    /** Reassigned away before a 3rd strike - neutral, excluded (a 3rd-strike reassignment needs no
     * separate event; FAILED already closed the cycle). */
    REASSIGNED,
    /** Admin edited priority on a still-open cycle - base points re-pegged to the new priority,
     * preserving whatever strike level the cycle was already sitting at (e.g. still 50% of base,
     * just 50% of the new base). Written instead of mutating the original ASSIGNED/STRIKE_1/STRIKE_2
     * row, since history is append-only; this becomes the cycle's new "latest" event. */
    REBASED
}
