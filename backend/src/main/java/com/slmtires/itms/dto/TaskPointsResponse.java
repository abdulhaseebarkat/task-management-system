package com.slmtires.itms.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Backs the per-task "Points — This Task" panel (Phase 9), scoped to one assignment. Admin only. */
public record TaskPointsResponse(
    Long taskId,
    String taskNumber,
    String title,
    String priority,
    Long assignmentId,
    Long userId,
    String userName,
    BigDecimal basePoints,
    BigDecimal resultingPoints,
    String outcome,
    List<Event> events
) {
    public record Event(Instant occurredAt, String eventType, BigDecimal pointsDelta, BigDecimal resultingPoints, String reason) {}
}
