package com.slmtires.itms.dto;

import java.time.LocalDate;
import java.util.List;

public record DueDateHistoryView(
    LocalDate originalDueDate,
    LocalDate currentDueDate,
    int extensionCount,
    List<TaskDueDateHistoryResponse> changes
) {}
