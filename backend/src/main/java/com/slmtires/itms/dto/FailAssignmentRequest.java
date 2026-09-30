package com.slmtires.itms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FailAssignmentRequest(
    @NotBlank @Size(max = 255) String reason
) {}
