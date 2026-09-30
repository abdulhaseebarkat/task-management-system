package com.slmtires.itms.dto;

import java.time.Instant;
import java.util.Map;

public record AuditLogRowResponse(
    Long id,
    Instant at,
    UserResponse actor,
    String entityType,
    Long entityId,
    String taskNumber,
    String action,
    String summary,
    Map<String, Object> oldValue,
    Map<String, Object> newValue
) {}
