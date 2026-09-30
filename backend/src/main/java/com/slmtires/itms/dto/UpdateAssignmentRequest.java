package com.slmtires.itms.dto;

import com.slmtires.itms.entity.AssignmentStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** progress is an Integer (not short) so an out-of-range value is a clean validation error, not a JSON parse failure. */
public record UpdateAssignmentRequest(
    @NotNull AssignmentStatus status,
    @NotNull @Min(0) @Max(100) Integer progress,
    @Size(max = 255) String reason
) {}
