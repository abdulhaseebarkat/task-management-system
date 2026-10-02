package com.slmtires.itms.dto;

import com.slmtires.itms.dto.DashboardAnalyticsResponse.CategoryCount;
import com.slmtires.itms.dto.DashboardAnalyticsResponse.MonthlyRate;
import com.slmtires.itms.dto.DashboardAnalyticsResponse.PointEventItem;
import com.slmtires.itms.dto.DashboardAnalyticsResponse.PriorityCount;
import com.slmtires.itms.dto.DashboardAnalyticsResponse.StatusSlice;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Phase 8 individual report (spec section 21 / 38), extended in Phase 9 with the points/
 * performance-scoring methodology the original spec left as an option ("no arbitrary score...
 * unless a formal scoring methodology is defined") - Admin-only, never shown to the employee
 * themself. See AnalyticsService#getEmployeeReport for what window each field uses.
 */
public record EmployeeReportResponse(
    Long employeeId,
    String employeeName,
    LocalDate from,
    LocalDate to,
    Summary summary,
    List<StatusSlice> statusDistribution,
    List<PriorityCount> priorityDistribution,
    List<CategoryCount> categoryDistribution,
    List<MonthCount> completionTrend,
    List<WorkloadItem> currentWorkload,
    List<ReassignedItem> reassignedTasks,
    List<ActivityItem> recentActivity,
    PointsSummary pointsSummary,
    List<PointsBreakdownRow> pointsBreakdown,
    List<MonthlyRate> monthlyPointsTrend,
    List<PointEventItem> pointEvents,
    List<TaskDetailRow> taskDetails
) {
    public record Summary(
        long assigned,
        long completed,
        long active,
        long overdue,
        long blocked,
        long onHold,
        long cancelled,
        long reassignedAway,
        int completionRate,
        Double avgCompletionDays
    ) {}

    public record MonthCount(String monthLabel, LocalDate monthStart, long count) {}

    public record WorkloadItem(Long taskId, String taskNumber, String title, Long assignmentId, String priority, String status, int progress, LocalDate dueDate) {}

    public record ReassignedItem(Long taskId, String taskNumber, String title, Long assignmentId, Instant reassignedAt, String toUserName, String reason) {}

    public record ActivityItem(Instant occurredAt, String taskNumber, String title, String oldStatus, String newStatus, String comment) {}

    /**
     * pointsEarned counts ONLY actually-completed work (the resultingPoints of COMPLETED events) -
     * an open, not-yet-finished task never contributes here, however long it's been sitting at full
     * points. pointsPossible is the live total (open tasks included at their full base value, same
     * as always) - the ceiling everything is measured against. pointsLost is what has already been
     * genuinely forfeited (basePoints minus resultingPoints) across every task, open or resolved -
     * a strike already landed on an open task counts here immediately, but an untouched open task
     * contributes nothing to either earned or lost, only to possible, until something actually
     * happens to it. efficiencyRate is earned ÷ possible - of every point ever at stake (including
     * still-open work, at its live value), what fraction has actually been banked so far. By design,
     * a pile of normal, still-open work DOES lower this until it's actually completed.
     */
    public record PointsSummary(BigDecimal pointsEarned, BigDecimal pointsPossible, BigDecimal pointsLost, int efficiencyRate, long tasksFailed) {}

    /** level is "FULL", "STRIKE_1", "STRIKE_2" or "FAILED". */
    public record PointsBreakdownRow(String level, long count, int percent) {}

    /** One row per (task, this employee's assignment) - the full points-relevant ledger for a single task. */
    public record TaskDetailRow(
        Long taskId,
        String taskNumber,
        String title,
        String priority,
        Instant assignedAt,
        String status,
        List<DueDateEntry> dueDates,
        BigDecimal possiblePoints,
        BigDecimal deductedPoints
    ) {}

    /** first=true is the task's original due date (or its first-ever due date, if it started in DRAFT); every other entry is a later change, in order. */
    public record DueDateEntry(LocalDate dueDate, boolean first, boolean countedTowardStrikes) {}
}
