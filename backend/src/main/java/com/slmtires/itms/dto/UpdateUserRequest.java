package com.slmtires.itms.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateUserRequest(
    @NotBlank String name,
    String department
) {
}
