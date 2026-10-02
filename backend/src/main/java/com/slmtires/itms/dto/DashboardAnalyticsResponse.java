package com.slmtires.itms.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Backs the Admin dashboard (Phase 6). Each field's window is documented on {@link com.slmtires.itms.service.AnalyticsService}
 * - not every widget uses the same one, matching the approved mockup's per-card subtitles.
 */
public record DashboardAnalyticsResponse(
    LocalDate from,
    LocalDate to,
    Summary summary,
    List<StatusSlice> statusDistribution,
    List<EmployeeCount> teamWorkload,
    List<PriorityCount> priorityDistribution,
    List<WeekCount> completionTrend,
    List<WeekCount> overdueTrend,
    List<EmployeeCount> employeeCompletion,
    /** Tasks created in [from, to] where this employee is (or ever was) an assignee - paired with
     * employeeCompletion for the "Tasks Assigned vs. Completed, by Person" chart. Admin (Farrukh) is
     * never in this list - only active team members, same as every other per-employee breakdown. */
    List<EmployeeCount> employeeAssigned,
    List<CategoryCount> categoryDistribution,
    List<PointsLeaderboardEntry> pointsLeaderboard,
    List<MonthlyRate> teamEfficiencyTrend,
    List<StrikeDistributionSlice> strikeDistribution,
    List<AtRiskTaskItem> atRiskTasks,
    /** Per employee, distinct tasks created in [from, to] that have had their due date extended by
     * the Admin at least once - counted whether or not that extension deducted points. A task
     * extended more than once still only counts once. */
    List<EmployeeCount> employeeDueDateExtensions,
    /** Per employee, how many times a task has been reassigned away from them (the "from" side) in
     * [from, to] - mirrors EmployeeReportResponse.Summary#reassignedAway, computed for everyone at once. */
    List<EmployeeCount> employeeReassignedAway
) {
    public record Summary(
        long totalTasks,
        long active,
        long completed,
        long overdue,
        long blocked,
        long onHold,
        long reassigned,
        int completionRate
    ) {}

    public record StatusSlice(String status, long count, int percent) {}

    public record EmployeeCount(Long employeeId, String employeeName, long count) {}

    public record PriorityCount(String priority, long count) {}

    public record CategoryCount(Long categoryId, String categoryName, long count) {}

    public record WeekCount(String weekLabel, LocalDate weekStart, long count) {}

    /**
     * Sorted by efficiencyRate descending - the fair, comparable ranking (raw points are shown
     * alongside, never used to rank). pointsEarned is completed work only; pointsPossible is live
     * (open tasks included at full base value); pointsLost is what's already been genuinely
     * forfeited (open or resolved) - see EmployeeReportResponse.PointsSummary for the full rationale.
     */
    public record PointsLeaderboardEntry(Long employeeId, String employeeName, BigDecimal pointsEarned, BigDecimal pointsPossible, BigDecimal pointsLost, int efficiencyRate) {}

    public record MonthlyRate(String monthLabel, LocalDate monthStart, int rate) {}

    /** level is "0", "1", "2" or "FAILED". */
    public record StrikeDistributionSlice(String level, long count, int percent) {}

    public record AtRiskTaskItem(Long taskId, String taskNumber, String title, Long assignmentId, String employeeName, int strikeLevel, LocalDate dueDate) {}

    public record PointEventItem(Instant occurredAt, Long taskId, String taskNumber, String title, Long assignmentId, String eventType, BigDecimal pointsDelta, BigDecimal resultingPoints, String reason) {}
}
