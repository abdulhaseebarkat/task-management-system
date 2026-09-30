package com.slmtires.itms.dto;

import com.slmtires.itms.entity.TaskPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/** Task status is deliberately absent: it is derived from assignments, or changed via cancel/reopen. */
public record TaskRequest(
    @NotBlank @Size(max = 200) String title,
    @Size(max = 10000) String description,
    @NotNull TaskPriority priority,
    @NotNull Long categoryId,
    @Size(max = 100) List<@NotNull Long> assigneeIds,
    LocalDate dueDate
) {
    public List<Long> safeAssigneeIds() {
        return assigneeIds == null ? List.of() : assigneeIds.stream().distinct().toList();
    }
}
