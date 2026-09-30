package com.slmtires.itms.dto;

import com.slmtires.itms.entity.TaskPriority;
import com.slmtires.itms.entity.TaskStatus;

import java.time.LocalDate;

/** Every field optional; {@link com.slmtires.itms.service.AnalyticsService} fills in the date-range default. */
public record AnalyticsQuery(
    LocalDate from,
    LocalDate to,
    Long employeeId,
    TaskStatus status,
    TaskPriority priority,
    Long categoryId
) {}
