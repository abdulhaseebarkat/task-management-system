package com.slmtires.itms.dto;

import java.time.Instant;
import java.time.LocalDate;

/** changedBy is populated only for an Admin viewer - never sent to an employee (spec: they see the history, not who changed it). */
public record TaskDueDateHistoryResponse(
    Long id,
    LocalDate previousDueDate,
    LocalDate newDueDate,
    String kind,
    String reason,
    ChangedBy changedBy,
    Instant changedAt,
    boolean countsTowardStrikes
) {
    public record ChangedBy(Long id, String name) {}
}
