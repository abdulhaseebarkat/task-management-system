package com.slmtires.itms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record DueDateChangeRequest(
    @NotNull(message = "Due date is required.") LocalDate dueDate,
    @NotBlank(message = "A reason is required.") @Size(max = 255) String reason,
    /** Admin's choice for this specific extension: null/omitted defaults to true (the historical,
     * always-penalize behavior), matching the checkbox's default-checked state in the UI. */
    Boolean deductPoints
) {
    public boolean shouldDeductPoints() {
        return deductPoints == null || deductPoints;
    }
}
