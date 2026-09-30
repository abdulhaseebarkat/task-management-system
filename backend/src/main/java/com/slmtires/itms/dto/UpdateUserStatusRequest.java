package com.slmtires.itms.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateUserStatusRequest(
    @NotNull Boolean active
) {
}
