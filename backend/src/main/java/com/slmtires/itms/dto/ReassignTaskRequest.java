package com.slmtires.itms.dto;

import com.slmtires.itms.entity.ReassignmentClassification;
import jakarta.validation.constraints.NotNull;

public record ReassignTaskRequest(
    @NotNull Long fromAssignmentId,
    @NotNull Long toUserId,
    String reason,
    ReassignmentClassification classification,
    Boolean keepProgress,
    /** Admin's choice: should the ORIGINAL assignee lose points for this task? Null/omitted
     * defaults to false - reassignment is neutral (excluded from scoring) unless the Admin
     * deliberately opts in, unlike the due-date-extension checkbox which defaults to true. */
    Boolean deductPoints
) {
    public boolean shouldDeductPoints() {
        return Boolean.TRUE.equals(deductPoints);
    }
}
