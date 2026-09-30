package com.slmtires.itms.dto;

import com.slmtires.itms.entity.ReassignmentClassification;

import java.time.Instant;

public record ReassignmentHistoryResponse(
    Long id,
    Instant createdAt,
    AssigneeSummary fromUser,
    AssigneeSummary toUser,
    AssigneeSummary reassignedBy,
    String reason,
    ReassignmentClassification classification,
    String fromStatus,
    int fromProgress,
    String toStatus,
    int toProgress
) {
    public record AssigneeSummary(Long id, String name, boolean active) {}
}
