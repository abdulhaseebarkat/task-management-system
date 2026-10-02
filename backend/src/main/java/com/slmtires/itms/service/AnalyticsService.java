package com.slmtires.itms.service;

import com.slmtires.itms.dto.AnalyticsQuery;
import com.slmtires.itms.dto.DashboardAnalyticsResponse;
import com.slmtires.itms.dto.DashboardAnalyticsResponse.CategoryCount;
import com.slmtires.itms.dto.DashboardAnalyticsResponse.EmployeeCount;
import com.slmtires.itms.dto.DashboardAnalyticsResponse.PriorityCount;
import com.slmtires.itms.dto.DashboardAnalyticsResponse.StatusSlice;
import com.slmtires.itms.dto.DashboardAnalyticsResponse.Summary;
import com.slmtires.itms.dto.DashboardAnalyticsResponse.WeekCount;
import com.slmtires.itms.dto.EmployeeReportResponse;
import com.slmtires.itms.dto.EmployeeReportResponse.ActivityItem;
import com.slmtires.itms.dto.EmployeeReportResponse.MonthCount;
import com.slmtires.itms.dto.EmployeeReportResponse.ReassignedItem;
import com.slmtires.itms.dto.EmployeeReportResponse.WorkloadItem;
import com.slmtires.itms.dto.DashboardAnalyticsResponse.AtRiskTaskItem;
import com.slmtires.itms.dto.DashboardAnalyticsResponse.MonthlyRate;
import com.slmtires.itms.dto.DashboardAnalyticsResponse.PointEventItem;
import com.slmtires.itms.dto.DashboardAnalyticsResponse.PointsLeaderboardEntry;
import com.slmtires.itms.dto.DashboardAnalyticsResponse.StrikeDistributionSlice;
import com.slmtires.itms.dto.TaskPointsResponse;
import com.slmtires.itms.entity.AssignmentStatus;
import com.slmtires.itms.entity.PointsEventType;
import com.slmtires.itms.entity.Role;
import com.slmtires.itms.entity.Task;
import com.slmtires.itms.entity.TaskAssignment;
import com.slmtires.itms.entity.TaskDueDateHistory;
import com.slmtires.itms.entity.TaskPointsEvent;
import com.slmtires.itms.entity.TaskPriority;
import com.slmtires.itms.entity.TaskReassignmentHistory;
import com.slmtires.itms.entity.TaskStatus;
import com.slmtires.itms.entity.TaskStatusHistory;
import com.slmtires.itms.entity.User;
import com.slmtires.itms.exception.ResourceNotFoundException;
import com.slmtires.itms.repository.TaskDueDateHistoryRepository;
import com.slmtires.itms.repository.TaskPointsEventRepository;
import com.slmtires.itms.repository.TaskReassignmentHistoryRepository;
import com.slmtires.itms.repository.TaskRepository;
import com.slmtires.itms.repository.TaskSpecifications;
import com.slmtires.itms.repository.TaskStatusHistoryRepository;
import com.slmtires.itms.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Backs the Admin dashboard (Phase 6). Deliberately in-memory: at this department's scale
 * (~6 people, low hundreds of tasks) fetching the filtered task list once and aggregating in
 * Java is simpler and less error-prone than eight separate SQL GROUP BY queries, and is fast
 * enough that it needs no caching.
 *
 * <p>Not every widget shares the same window - each follows the approved mockup's own subtitle:
 * <ul>
 *   <li>Total Tasks / Completed / Reassigned / Completion Rate - tasks CREATED within [from, to].</li>
 *   <li>Active / Overdue / Blocked / On Hold - a LIVE snapshot (not date-bound), so an old task
 *       that is still blocked today always shows up regardless of the date-range filter.</li>
 *   <li>Task Status Distribution - tasks created within [from, to], all statuses.</li>
 *   <li>Team Workload / Priority Distribution - live snapshot of currently-open tasks.</li>
 *   <li>Employee Completion - per employee, THEIR OWN assignment's completedAt falling within
 *       [from, to] - not the whole task's completedAt, so finishing your part of a still-open
 *       shared task still counts. (Task Completion Trend, below, is the task-level equivalent:
 *       organizational throughput rather than individual credit.)</li>
 *   <li>Task Completion Trend - completions per ISO week, fixed last-8-weeks window (independent
 *       of the date-range filter, same as the mockup's own fixed "last 8 weeks" subtitle).</li>
 *   <li>Overdue Trend - the CURRENT overdue backlog, bucketed by the week its due date fell in.
 *       This is a snapshot of today's backlog by origin week, not a reconstructed historical
 *       trend (the append-only history tables would allow that, but at real added complexity
 *       for a chart whose job is "where is the backlog piling up" - documented here rather than
 *       silently approximated).</li>
 * </ul>
 * Every widget still respects the explicit employee/status/priority/category filters, if set.
 */
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private static final Set<TaskStatus> OPEN_STATUSES = EnumSet.of(
        TaskStatus.ASSIGNED, TaskStatus.IN_PROGRESS, TaskStatus.REOPENED, TaskStatus.BLOCKED, TaskStatus.ON_HOLD, TaskStatus.FAILED);
    private static final Set<TaskStatus> ACTIVE_STATUSES = EnumSet.of(
        TaskStatus.ASSIGNED, TaskStatus.IN_PROGRESS, TaskStatus.REOPENED);
    private static final Set<AssignmentStatus> ASSIGNMENT_OPEN_STATUSES = EnumSet.of(
        AssignmentStatus.ASSIGNED, AssignmentStatus.IN_PROGRESS, AssignmentStatus.ON_HOLD, AssignmentStatus.BLOCKED);
    private static final int TREND_WEEKS = 8;
    private static final int TREND_MONTHS = 6;
    private static final int RECENT_ACTIVITY_LIMIT = 10;
    private static final int DEFAULT_RANGE_DAYS = 30;
    private static final DateTimeFormatter WEEK_LABEL = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH);
    private static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);

    private static final BigDecimal HALF = new BigDecimal("0.50");

    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final TaskReassignmentHistoryRepository reassignmentHistoryRepository;
    private final TaskStatusHistoryRepository taskStatusHistoryRepository;
    private final TaskPointsEventRepository pointsEventRepository;
    private final TaskDueDateHistoryRepository dueDateHistoryRepository;

    @Transactional(readOnly = true)
    public DashboardAnalyticsResponse getDashboard(AnalyticsQuery query) {
        LocalDate to = query.to() != null ? query.to() : LocalDate.now();
        LocalDate from = query.from() != null ? query.from() : to.minusDays(DEFAULT_RANGE_DAYS - 1);

        List<Task> createdInRange = taskRepository.findAll(TaskSpecifications.forAnalytics(
            query.status(), query.priority(), query.categoryId(), query.employeeId(), startOfDay(from), startOfDay(to.plusDays(1))));
        List<Task> allMatchingFilters = taskRepository.findAll(TaskSpecifications.forAnalytics(
            query.status(), query.priority(), query.categoryId(), query.employeeId(), null, null));
        List<Task> liveOpen = allMatchingFilters.stream().filter(t -> OPEN_STATUSES.contains(t.getStatus())).toList();

        LocalDate trendStart = LocalDate.now().minusWeeks(TREND_WEEKS - 1L).with(DayOfWeek.MONDAY);
        List<Task> completedLast8Weeks = taskRepository.findAll(TaskSpecifications.forAnalyticsByCompleted(
            query.status(), query.priority(), query.categoryId(), query.employeeId(), startOfDay(trendStart), null));

        Summary summary = buildSummary(createdInRange, liveOpen, allMatchingFilters, from, to);
        List<StatusSlice> statusDistribution = buildStatusDistribution(createdInRange);
        List<CategoryCount> categoryDistribution = buildCategoryDistribution(createdInRange);
        List<EmployeeCount> teamWorkload = buildEmployeeCounts(liveOpen, this::isCurrentOpenAssignment);
        List<PriorityCount> priorityDistribution = buildPriorityDistribution(liveOpen);
        List<EmployeeCount> employeeCompletion = buildEmployeeCompletion(allMatchingFilters, startOfDay(from), startOfDay(to.plusDays(1)));
        List<EmployeeCount> employeeAssigned = buildEmployeeAssignedCounts(createdInRange, employees());
        List<WeekCount> completionTrend = bucketByWeek(completedLast8Weeks, task -> task.getCompletedAt().atZone(ZoneOffset.UTC).toLocalDate(), trendStart);
        List<Task> currentlyOverdue = allMatchingFilters.stream()
            .filter(t -> OPEN_STATUSES.contains(t.getStatus()) && t.getDueDate() != null && t.getDueDate().isBefore(LocalDate.now()))
            .toList();
        List<WeekCount> overdueTrend = bucketByWeek(currentlyOverdue, Task::getDueDate, trendStart);

        // Phase 9: fetched once, all-time, then filtered/aggregated in Java for each widget below -
        // the leaderboard and strike distribution are scoped to [from, to], while the efficiency
        // trend and at-risk list are deliberately NOT date-bound (a live snapshot / rolling trend).
        List<TaskPointsEvent> allPointsEvents = pointsEventRepository.findByOccurredAtBetweenOrderByOccurredAtDesc(Instant.EPOCH, Instant.now());
        List<PointsLeaderboardEntry> pointsLeaderboard = buildPointsLeaderboard(allPointsEvents, startOfDay(from), startOfDay(to.plusDays(1)));
        List<MonthlyRate> teamEfficiencyTrend = buildMonthlyRate(allPointsEvents);
        List<StrikeDistributionSlice> strikeDistribution = buildStrikeDistribution(allPointsEvents, startOfDay(from), startOfDay(to.plusDays(1)));
        List<AtRiskTaskItem> atRiskTasks = buildAtRiskTasks(liveOpen, allPointsEvents);
        List<EmployeeCount> employeeDueDateExtensions = buildEmployeeDueDateExtensionCounts(createdInRange, employees());
        List<EmployeeCount> employeeReassignedAway = buildEmployeeReassignedAwayCounts(employees(), startOfDay(from), startOfDay(to.plusDays(1)));

        return new DashboardAnalyticsResponse(from, to, summary, statusDistribution, teamWorkload,
            priorityDistribution, completionTrend, overdueTrend, employeeCompletion, employeeAssigned, categoryDistribution,
            pointsLeaderboard, teamEfficiencyTrend, strikeDistribution, atRiskTasks, employeeDueDateExtensions, employeeReassignedAway);
    }

    /**
     * Phase 8 individual report (spec sections 21 / 38) - one person's own objective metrics, no
     * synthesized score. Every task-level query below is org-wide (no employeeId in the
     * specification) and then filtered to this person in Java, for the same reason as
     * {@link #getDashboard}'s employeeCompletion: a completed-then-unassigned assignment stays
     * COMPLETED but is no longer "current", so an EXISTS-current-assignee filter would silently
     * drop it. The two exceptions are currentWorkload and the active/overdue/blocked/onHold
     * counts, which are explicitly about CURRENT assignments and use the person's OWN assignment
     * status (not the task's derived overall status) - correct for a shared task where a
     * co-assignee's state differs from theirs.
     *
     * <p>Two independent "completed" numbers exist on purpose: {@code summary.completed} is
     * createdAt-bound (paired with {@code assigned} for a coherent completion rate - "of what
     * was assigned in this window, how much did they finish"), while {@code avgCompletionDays}
     * is completedAt-bound (unbounded by when the task was created - "how long have their
     * recent completions taken"), matching the same createdAt-vs-completedAt split documented
     * on {@link #getDashboard}.
     */
    @Transactional(readOnly = true)
    public EmployeeReportResponse getEmployeeReport(Long employeeId, AnalyticsQuery query) {
        User employee = userRepository.findById(employeeId)
            .filter(u -> u.getRole() == Role.TEAM_MEMBER)
            .orElseThrow(() -> new ResourceNotFoundException("Employee not found."));

        LocalDate to = query.to() != null ? query.to() : LocalDate.now();
        LocalDate from = query.from() != null ? query.from() : to.minusDays(DEFAULT_RANGE_DAYS - 1);
        Instant fromInstant = startOfDay(from);
        Instant toInstant = startOfDay(to.plusDays(1));

        List<Task> orgCreatedInRange = taskRepository.findAll(TaskSpecifications.forAnalytics(
            query.status(), query.priority(), query.categoryId(), null, fromInstant, toInstant));
        List<Task> orgAllMatching = taskRepository.findAll(TaskSpecifications.forAnalytics(
            query.status(), query.priority(), query.categoryId(), null, null, null));

        List<Task> employeeCreatedInRange = orgCreatedInRange.stream()
            .filter(t -> t.getAssignments().stream().anyMatch(a -> a.getUser().getId().equals(employeeId)))
            .toList();

        record OpenItem(Task task, TaskAssignment assignment) {}
        List<OpenItem> employeeOpen = orgAllMatching.stream()
            .flatMap(t -> t.getAssignments().stream()
                .filter(a -> a.isCurrent() && a.getUser().getId().equals(employeeId) && ASSIGNMENT_OPEN_STATUSES.contains(a.getStatus()))
                .map(a -> new OpenItem(t, a)))
            .toList();

        List<TaskAssignment> completedInRange = orgAllMatching.stream()
            .flatMap(t -> t.getAssignments().stream())
            .filter(a -> a.getUser().getId().equals(employeeId) && a.getStatus() == AssignmentStatus.COMPLETED
                && a.getCompletedAt() != null && !a.getCompletedAt().isBefore(fromInstant) && a.getCompletedAt().isBefore(toInstant))
            .toList();

        EmployeeReportResponse.Summary summary = buildEmployeeSummary(employeeId, employeeCreatedInRange, employeeOpen.stream().map(OpenItem::assignment).toList(),
            completedInRange, fromInstant, toInstant);
        List<StatusSlice> statusDistribution = buildStatusDistribution(employeeCreatedInRange);
        List<PriorityCount> priorityDistribution = buildPriorityDistribution(employeeCreatedInRange);
        List<CategoryCount> categoryDistribution = buildCategoryDistribution(employeeCreatedInRange);

        LocalDate trendStart = LocalDate.now().minusMonths(TREND_MONTHS - 1L).withDayOfMonth(1);
        List<TaskAssignment> completionsForTrend = orgAllMatching.stream()
            .flatMap(t -> t.getAssignments().stream())
            .filter(a -> a.getUser().getId().equals(employeeId) && a.getStatus() == AssignmentStatus.COMPLETED
                && a.getCompletedAt() != null && !a.getCompletedAt().isBefore(startOfDay(trendStart)))
            .toList();
        List<MonthCount> completionTrend = bucketByMonth(completionsForTrend, a -> a.getCompletedAt().atZone(ZoneOffset.UTC).toLocalDate(), trendStart);

        List<WorkloadItem> currentWorkload = employeeOpen.stream()
            .sorted(Comparator.comparing(item -> item.task().getDueDate(), Comparator.nullsLast(Comparator.naturalOrder())))
            .map(item -> new WorkloadItem(item.task().getId(), item.task().getTaskNumber(), item.task().getTitle(), item.assignment().getId(),
                item.task().getPriority().name(), item.assignment().getStatus().name(), item.assignment().getProgress(), item.task().getDueDate()))
            .toList();

        List<ReassignedItem> reassignedTasks = buildReassignedTasks(employeeId, fromInstant, toInstant);
        List<ActivityItem> recentActivity = buildRecentActivity(employeeId);

        // Phase 9: all-time fetch for this employee, then filtered/aggregated per widget - the
        // summary/breakdown are scoped to [from, to], while the trend and events list are not
        // (a rolling trend and a "most recent activity" list respectively).
        List<TaskPointsEvent> employeeAllEvents = pointsEventRepository.findByUserIdAndOccurredAtBetweenOrderByOccurredAtDesc(employeeId, Instant.EPOCH, Instant.now());
        EmployeeReportResponse.PointsSummary pointsSummary = buildPointsSummary(employeeAllEvents, fromInstant, toInstant);
        List<EmployeeReportResponse.PointsBreakdownRow> pointsBreakdown = buildPointsBreakdown(employeeAllEvents, fromInstant, toInstant);
        List<MonthlyRate> monthlyPointsTrend = buildMonthlyRate(employeeAllEvents);
        List<PointEventItem> pointEvents = buildPointEventItems(employeeAllEvents);
        List<EmployeeReportResponse.TaskDetailRow> taskDetails = buildTaskDetails(employeeId, employeeCreatedInRange, employeeAllEvents);

        return new EmployeeReportResponse(employeeId, employee.getName(), from, to, summary, statusDistribution,
            priorityDistribution, categoryDistribution, completionTrend, currentWorkload, reassignedTasks, recentActivity,
            pointsSummary, pointsBreakdown, monthlyPointsTrend, pointEvents, taskDetails);
    }

    private EmployeeReportResponse.Summary buildEmployeeSummary(
        Long employeeId, List<Task> employeeCreatedInRange, List<TaskAssignment> employeeOpenAssignments,
        List<TaskAssignment> completedInRange, Instant fromInstant, Instant toInstant
    ) {
        long assigned = employeeCreatedInRange.size();
        long completed = employeeCreatedInRange.stream()
            .filter(t -> t.getAssignments().stream().anyMatch(a -> a.getUser().getId().equals(employeeId) && a.getStatus() == AssignmentStatus.COMPLETED))
            .count();
        long cancelled = employeeCreatedInRange.stream().filter(t -> t.getStatus() == TaskStatus.CANCELLED).count();
        int completionRate = assigned == 0 ? 0 : Math.round(completed * 100f / assigned);

        long active = employeeOpenAssignments.stream().filter(a -> a.getStatus() == AssignmentStatus.ASSIGNED || a.getStatus() == AssignmentStatus.IN_PROGRESS).count();
        long blocked = employeeOpenAssignments.stream().filter(a -> a.getStatus() == AssignmentStatus.BLOCKED).count();
        long onHold = employeeOpenAssignments.stream().filter(a -> a.getStatus() == AssignmentStatus.ON_HOLD).count();
        long overdue = employeeOpenAssignments.stream()
            .filter(a -> a.getTask().getDueDate() != null && a.getTask().getDueDate().isBefore(LocalDate.now()))
            .count();

        long reassignedAway = reassignmentHistoryRepository.countByFromUserIdAndCreatedAtBetween(employeeId, fromInstant, toInstant);

        Double avgCompletionDays = completedInRange.isEmpty() ? null : Math.round(completedInRange.stream()
            .mapToDouble(a -> Duration.between(a.getAssignedAt(), a.getCompletedAt()).toMinutes() / 1440.0)
            .average().orElse(0.0) * 10) / 10.0;

        return new EmployeeReportResponse.Summary(assigned, completed, active, overdue, blocked, onHold, cancelled, reassignedAway, completionRate, avgCompletionDays);
    }

    private List<ReassignedItem> buildReassignedTasks(Long employeeId, Instant from, Instant to) {
        List<TaskReassignmentHistory> reassignments = reassignmentHistoryRepository.findByFromUserIdAndCreatedAtBetweenOrderByCreatedAtDesc(employeeId, from, to);
        if (reassignments.isEmpty()) return List.of();

        Map<Long, Task> tasksById = taskRepository.findAllById(reassignments.stream().map(TaskReassignmentHistory::getTaskId).distinct().toList())
            .stream().collect(Collectors.toMap(Task::getId, t -> t));
        Map<Long, User> usersById = userRepository.findAllById(reassignments.stream().map(TaskReassignmentHistory::getToUserId).distinct().toList())
            .stream().collect(Collectors.toMap(User::getId, u -> u));

        return reassignments.stream().map(r -> {
            Task task = tasksById.get(r.getTaskId());
            User toUser = usersById.get(r.getToUserId());
            return new ReassignedItem(r.getTaskId(), task == null ? null : task.getTaskNumber(), task == null ? "Unknown" : task.getTitle(),
                r.getFromAssignmentId(), r.getCreatedAt(), toUser == null ? "Unknown" : toUser.getName(), r.getReason());
        }).toList();
    }

    private List<ActivityItem> buildRecentActivity(Long employeeId) {
        List<TaskStatusHistory> recent = taskStatusHistoryRepository.findRecentForAssignee(employeeId, PageRequest.of(0, RECENT_ACTIVITY_LIMIT));
        if (recent.isEmpty()) return List.of();

        Map<Long, Task> tasksById = taskRepository.findAllById(recent.stream().map(TaskStatusHistory::getTaskId).distinct().toList())
            .stream().collect(Collectors.toMap(Task::getId, t -> t));

        return recent.stream().map(h -> {
            Task task = tasksById.get(h.getTaskId());
            return new ActivityItem(h.getCreatedAt(), task == null ? null : task.getTaskNumber(), task == null ? "Unknown" : task.getTitle(),
                h.getOldStatus(), h.getNewStatus(), h.getComment());
        }).toList();
    }

    private List<MonthCount> bucketByMonth(List<TaskAssignment> assignments, Function<TaskAssignment, LocalDate> dateOf, LocalDate trendStart) {
        List<LocalDate> monthStarts = new ArrayList<>();
        for (int i = 0; i < TREND_MONTHS; i++) {
            monthStarts.add(trendStart.plusMonths(i));
        }
        Map<LocalDate, Long> counts = new LinkedHashMap<>();
        for (LocalDate monthStart : monthStarts) {
            counts.put(monthStart, 0L);
        }
        for (TaskAssignment assignment : assignments) {
            LocalDate date = dateOf.apply(assignment);
            if (date == null) continue;
            LocalDate monthStart = date.withDayOfMonth(1);
            if (counts.containsKey(monthStart)) {
                counts.merge(monthStart, 1L, Long::sum);
            }
        }
        return monthStarts.stream()
            .map(monthStart -> new MonthCount(monthStart.format(MONTH_LABEL), monthStart, counts.get(monthStart)))
            .toList();
    }

    private Summary buildSummary(List<Task> createdInRange, List<Task> liveOpen, List<Task> allMatchingFilters, LocalDate from, LocalDate to) {
        long totalTasks = createdInRange.size();
        long completed = createdInRange.stream().filter(t -> t.getStatus() == TaskStatus.COMPLETED).count();
        int completionRate = totalTasks == 0 ? 0 : Math.round(completed * 100f / totalTasks);

        long active = liveOpen.stream().filter(t -> ACTIVE_STATUSES.contains(t.getStatus())).count();
        long overdue = liveOpen.stream().filter(t -> t.getDueDate() != null && t.getDueDate().isBefore(LocalDate.now())).count();
        long blocked = liveOpen.stream().filter(t -> t.getStatus() == TaskStatus.BLOCKED).count();
        long onHold = liveOpen.stream().filter(t -> t.getStatus() == TaskStatus.ON_HOLD).count();

        Set<Long> filteredTaskIds = allMatchingFilters.stream().map(Task::getId).collect(java.util.stream.Collectors.toSet());
        long reassigned = filteredTaskIds.isEmpty() ? 0 : reassignmentHistoryRepository.countByTaskIdInAndCreatedAtBetween(
            filteredTaskIds, startOfDay(from), startOfDay(to.plusDays(1)));

        return new Summary(totalTasks, active, completed, overdue, blocked, onHold, reassigned, completionRate);
    }

    private List<StatusSlice> buildStatusDistribution(List<Task> tasks) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String label : List.of("COMPLETED", "IN_PROGRESS", "ASSIGNED", "ON_HOLD", "BLOCKED", "FAILED", "CANCELLED")) {
            counts.put(label, 0L);
        }
        for (Task task : tasks) {
            String bucket = switch (task.getStatus()) {
                case COMPLETED -> "COMPLETED";
                case IN_PROGRESS, REOPENED -> "IN_PROGRESS";
                case ASSIGNED, DRAFT -> "ASSIGNED";
                case ON_HOLD -> "ON_HOLD";
                case BLOCKED -> "BLOCKED";
                case FAILED -> "FAILED";
                case CANCELLED -> "CANCELLED";
            };
            counts.merge(bucket, 1L, Long::sum);
        }
        long total = tasks.size();
        List<StatusSlice> result = new ArrayList<>();
        for (var entry : counts.entrySet()) {
            int percent = total == 0 ? 0 : Math.round(entry.getValue() * 100f / total);
            result.add(new StatusSlice(entry.getKey(), entry.getValue(), percent));
        }
        return result;
    }

    private List<CategoryCount> buildCategoryDistribution(List<Task> tasks) {
        Map<Long, String> names = new LinkedHashMap<>();
        Map<Long, Long> counts = new LinkedHashMap<>();
        for (Task task : tasks) {
            Long id = task.getCategory().getId();
            names.putIfAbsent(id, task.getCategory().getName());
            counts.merge(id, 1L, Long::sum);
        }
        return counts.entrySet().stream()
            .map(e -> new CategoryCount(e.getKey(), names.get(e.getKey()), e.getValue()))
            .sorted(Comparator.comparingLong(CategoryCount::count).reversed())
            .toList();
    }

    private List<PriorityCount> buildPriorityDistribution(List<Task> tasks) {
        Map<TaskPriority, Long> counts = new LinkedHashMap<>();
        for (TaskPriority priority : TaskPriority.values()) {
            counts.put(priority, 0L);
        }
        for (Task task : tasks) {
            counts.merge(task.getPriority(), 1L, Long::sum);
        }
        return counts.entrySet().stream().map(e -> new PriorityCount(e.getKey().name(), e.getValue())).toList();
    }

    private boolean isCurrentOpenAssignment(TaskAssignment assignment) {
        return assignment.isCurrent();
    }

    /** Every active team member is listed even at zero, so a quiet employee doesn't just vanish from the chart. */
    private List<EmployeeCount> buildEmployeeCounts(List<Task> tasks, java.util.function.Predicate<TaskAssignment> assignmentFilter) {
        return buildEmployeeCounts(tasks, assignmentFilter, employees());
    }

    private List<EmployeeCount> buildEmployeeCounts(List<Task> tasks, java.util.function.Predicate<TaskAssignment> assignmentFilter, List<User> employees) {
        Map<Long, Long> counts = new LinkedHashMap<>();
        for (User employee : employees) {
            counts.put(employee.getId(), 0L);
        }
        for (Task task : tasks) {
            for (TaskAssignment assignment : task.getAssignments()) {
                if (assignmentFilter.test(assignment) && counts.containsKey(assignment.getUser().getId())) {
                    counts.merge(assignment.getUser().getId(), 1L, Long::sum);
                }
            }
        }
        return employees.stream()
            .map(employee -> new EmployeeCount(employee.getId(), employee.getName(), counts.get(employee.getId())))
            .toList();
    }

    /**
     * Per employee, THEIR OWN assignment.completedAt within [from, to) - looks at every assignment
     * a task has ever had (not just current), since a completed assignment is never subsequently
     * reassigned (TaskReassignmentService forbids it) and stays COMPLETED even if later unassigned
     * via a plain edit, so this can't double- or under-count.
     */
    private List<EmployeeCount> buildEmployeeCompletion(List<Task> tasks, Instant from, Instant to) {
        return buildEmployeeCounts(tasks, assignment ->
            assignment.getStatus() == com.slmtires.itms.entity.AssignmentStatus.COMPLETED
                && assignment.getCompletedAt() != null
                && !assignment.getCompletedAt().isBefore(from)
                && assignment.getCompletedAt().isBefore(to),
            employees());
    }

    /**
     * Per employee, tasks in this list where they're (or ever were) an assignee - one count per
     * TASK, not per assignment row, so a reassign-then-reassign-back doesn't double-count them on
     * the same task. Mirrors getEmployeeReport's own summary.assigned definition exactly, just
     * computed for every employee across the whole department at once instead of one at a time.
     * Pairs with buildEmployeeCompletion for the "Tasks Assigned vs. Completed, by Person" chart.
     */
    private List<EmployeeCount> buildEmployeeAssignedCounts(List<Task> tasks, List<User> employees) {
        Map<Long, Long> counts = new LinkedHashMap<>();
        for (User employee : employees) {
            counts.put(employee.getId(), 0L);
        }
        for (Task task : tasks) {
            Set<Long> distinctAssigneeIds = task.getAssignments().stream().map(a -> a.getUser().getId()).collect(Collectors.toSet());
            for (Long id : distinctAssigneeIds) {
                if (counts.containsKey(id)) {
                    counts.merge(id, 1L, Long::sum);
                }
            }
        }
        return employees.stream()
            .map(employee -> new EmployeeCount(employee.getId(), employee.getName(), counts.get(employee.getId())))
            .toList();
    }

    /**
     * Per employee, distinct tasks (among those passed in) that have had their due date extended by
     * the Admin at least once - counted regardless of whether that particular extension had points
     * deducted or not ("be it with deduction or without deduction"). A task extended more than once
     * still only counts once. Attribution follows the same "ever an assignee" convention as
     * {@link #buildEmployeeAssignedCounts} - a reassign-then-extend doesn't drop the original assignee.
     */
    private List<EmployeeCount> buildEmployeeDueDateExtensionCounts(List<Task> tasks, List<User> employees) {
        Map<Long, Long> counts = new LinkedHashMap<>();
        for (User employee : employees) {
            counts.put(employee.getId(), 0L);
        }
        for (Task task : tasks) {
            boolean extended = dueDateHistoryRepository.findByTaskIdOrderByChangedAtAsc(task.getId()).stream()
                .anyMatch(h -> h.getPreviousDueDate() != null);
            if (!extended) continue;
            Set<Long> distinctAssigneeIds = task.getAssignments().stream().map(a -> a.getUser().getId()).collect(Collectors.toSet());
            for (Long id : distinctAssigneeIds) {
                if (counts.containsKey(id)) {
                    counts.merge(id, 1L, Long::sum);
                }
            }
        }
        return employees.stream()
            .map(employee -> new EmployeeCount(employee.getId(), employee.getName(), counts.get(employee.getId())))
            .toList();
    }

    /**
     * Per employee, how many times a task has been reassigned away from them (the "from" side) in
     * [from, to) - mirrors {@code EmployeeReportResponse.Summary#reassignedAway}'s own definition,
     * computed for every employee at once via the same repository count query.
     */
    private List<EmployeeCount> buildEmployeeReassignedAwayCounts(List<User> employees, Instant from, Instant to) {
        return employees.stream()
            .map(employee -> new EmployeeCount(employee.getId(), employee.getName(),
                reassignmentHistoryRepository.countByFromUserIdAndCreatedAtBetween(employee.getId(), from, to)))
            .toList();
    }

    private List<User> employees() {
        return userRepository.findAllByRoleAndActiveTrueOrderByNameAsc(Role.TEAM_MEMBER);
    }

    private List<WeekCount> bucketByWeek(List<Task> tasks, Function<Task, LocalDate> dateOf, LocalDate trendStart) {
        List<LocalDate> weekStarts = new ArrayList<>();
        for (int i = 0; i < TREND_WEEKS; i++) {
            weekStarts.add(trendStart.plusWeeks(i));
        }
        Map<LocalDate, Long> counts = new LinkedHashMap<>();
        for (LocalDate weekStart : weekStarts) {
            counts.put(weekStart, 0L);
        }
        for (Task task : tasks) {
            LocalDate date = dateOf.apply(task);
            if (date == null) continue;
            LocalDate weekStart = date.with(DayOfWeek.MONDAY);
            if (counts.containsKey(weekStart)) {
                counts.merge(weekStart, 1L, Long::sum);
            }
        }
        return weekStarts.stream()
            .map(weekStart -> new WeekCount(weekStart.format(WEEK_LABEL), weekStart, counts.get(weekStart)))
            .toList();
    }

    private Instant startOfDay(LocalDate date) {
        return date.atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    // ---- Phase 9: points/performance-scoring aggregation --------------------------------------

    /**
     * 0 = full points, 1/2 = after the 1st/2nd strike, 3 = FAILED. Works for a resolved
     * (COMPLETED/FAILED) event as well as a still-open one (ASSIGNED/STRIKE_1/STRIKE_2) - both kinds
     * carry a basePoints/resultingPoints pair, so the same ratio check classifies either.
     */
    private int strikeLevelOf(TaskPointsEvent event) {
        if (event.getEventType() == PointsEventType.FAILED) return 3;
        BigDecimal base = event.getBasePoints();
        BigDecimal resulting = event.getResultingPoints();
        if (resulting.compareTo(base) == 0) return 0;
        if (resulting.compareTo(base.multiply(HALF).setScale(2, RoundingMode.HALF_UP)) == 0) return 1;
        return 2;
    }

    /**
     * Every points event that counts toward earned/possible totals: a resolved cycle's terminal
     * outcome (COMPLETED/FAILED - same as always, and there can be more than one per assignment
     * over its lifetime if it's been reopened/reinstated), PLUS each still-open assignment's current
     * cycle, valued at whatever it's presently sitting at - full base points if untouched, or
     * reduced if it has already taken a strike. This is what makes points show up the moment a task
     * is assigned rather than only once it resolves, while staying perfectly continuous: an open
     * cycle's live value and its eventual COMPLETED/FAILED value are computed from the exact same
     * numbers, so resolving a task never produces a jump - only a genuine strike does. A cycle
     * closed out neutrally (CANCELLED, or REASSIGNED without the points-loss checkbox) contributes
     * nothing either way, exactly as before - "still open" specifically means its latest event is
     * ASSIGNED/STRIKE_1/STRIKE_2, never one of the terminal types.
     */
    private List<TaskPointsEvent> countableEvents(List<TaskPointsEvent> events) {
        List<TaskPointsEvent> result = new ArrayList<>();
        for (List<TaskPointsEvent> cycle : events.stream().collect(Collectors.groupingBy(TaskPointsEvent::getAssignmentId)).values()) {
            List<TaskPointsEvent> sorted = cycle.stream()
                .sorted(Comparator.comparing(TaskPointsEvent::getOccurredAt).thenComparing(TaskPointsEvent::getId))
                .toList();
            for (TaskPointsEvent event : sorted) {
                if (event.getEventType() == PointsEventType.COMPLETED || event.getEventType() == PointsEventType.FAILED) {
                    result.add(event);
                }
            }
            TaskPointsEvent latest = sorted.get(sorted.size() - 1);
            boolean stillOpen = latest.getEventType() == PointsEventType.ASSIGNED
                || latest.getEventType() == PointsEventType.STRIKE_1
                || latest.getEventType() == PointsEventType.STRIKE_2;
            if (stillOpen) {
                result.add(latest);
            }
        }
        return result;
    }

    private List<TaskPointsEvent> countableEventsInRange(List<TaskPointsEvent> events, Instant from, Instant to) {
        return countableEvents(events).stream()
            .filter(e -> !e.getOccurredAt().isBefore(from) && e.getOccurredAt().isBefore(to))
            .toList();
    }

    /**
     * earned ÷ possible - of every point ever at stake (open tasks included, at their live value),
     * what fraction has actually been banked so far. Per the Admin's explicit choice: a pile of
     * ordinary, still-open work DOES lower this until it's actually completed - unlike the
     * earned÷(earned+lost) alternative considered earlier, which this replaced.
     */
    private int efficiencyRate(BigDecimal earned, BigDecimal possible) {
        return possible.signum() == 0 ? 0 : Math.round(earned.divide(possible, 4, RoundingMode.HALF_UP).floatValue() * 100);
    }

    /** Every active team member is listed even at zero points, so a quiet employee doesn't vanish from the leaderboard. */
    private List<PointsLeaderboardEntry> buildPointsLeaderboard(List<TaskPointsEvent> allEvents, Instant from, Instant to) {
        List<TaskPointsEvent> countable = countableEventsInRange(allEvents, from, to);
        Map<Long, BigDecimal> earnedByUser = new LinkedHashMap<>();
        Map<Long, BigDecimal> possibleByUser = new LinkedHashMap<>();
        Map<Long, BigDecimal> lostByUser = new LinkedHashMap<>();
        for (User employee : employees()) {
            earnedByUser.put(employee.getId(), BigDecimal.ZERO);
            possibleByUser.put(employee.getId(), BigDecimal.ZERO);
            lostByUser.put(employee.getId(), BigDecimal.ZERO);
        }
        for (TaskPointsEvent event : countable) {
            if (!earnedByUser.containsKey(event.getUserId())) continue;
            if (event.getEventType() == PointsEventType.COMPLETED) {
                earnedByUser.merge(event.getUserId(), event.getResultingPoints(), BigDecimal::add);
            }
            possibleByUser.merge(event.getUserId(), event.getBasePoints(), BigDecimal::add);
            lostByUser.merge(event.getUserId(), event.getBasePoints().subtract(event.getResultingPoints()), BigDecimal::add);
        }
        return employees().stream()
            .map(employee -> {
                BigDecimal earned = earnedByUser.get(employee.getId());
                BigDecimal possible = possibleByUser.get(employee.getId());
                BigDecimal lost = lostByUser.get(employee.getId());
                return new PointsLeaderboardEntry(employee.getId(), employee.getName(), earned, possible, lost, efficiencyRate(earned, possible));
            })
            .sorted(Comparator.comparingInt(PointsLeaderboardEntry::efficiencyRate).reversed())
            .toList();
    }

    /** Shared by the department "Team Efficiency Trend" (all employees' events) and the individual "Monthly Points Trend" (one employee's events) - identical bucketing math either way. */
    private List<MonthlyRate> buildMonthlyRate(List<TaskPointsEvent> events) {
        LocalDate trendStart = LocalDate.now().minusMonths(TREND_MONTHS - 1L).withDayOfMonth(1);
        Instant trendStartInstant = startOfDay(trendStart);
        List<TaskPointsEvent> countable = countableEvents(events).stream()
            .filter(e -> !e.getOccurredAt().isBefore(trendStartInstant))
            .toList();

        List<LocalDate> monthStarts = new ArrayList<>();
        for (int i = 0; i < TREND_MONTHS; i++) monthStarts.add(trendStart.plusMonths(i));
        Map<LocalDate, BigDecimal> earned = new LinkedHashMap<>();
        Map<LocalDate, BigDecimal> possible = new LinkedHashMap<>();
        for (LocalDate month : monthStarts) {
            earned.put(month, BigDecimal.ZERO);
            possible.put(month, BigDecimal.ZERO);
        }
        for (TaskPointsEvent event : countable) {
            LocalDate month = event.getOccurredAt().atZone(ZoneOffset.UTC).toLocalDate().withDayOfMonth(1);
            if (!earned.containsKey(month)) continue;
            if (event.getEventType() == PointsEventType.COMPLETED) {
                earned.merge(month, event.getResultingPoints(), BigDecimal::add);
            }
            possible.merge(month, event.getBasePoints(), BigDecimal::add);
        }
        return monthStarts.stream()
            .map(month -> new MonthlyRate(month.format(MONTH_LABEL), month, efficiencyRate(earned.get(month), possible.get(month))))
            .toList();
    }

    private List<StrikeDistributionSlice> buildStrikeDistribution(List<TaskPointsEvent> allEvents, Instant from, Instant to) {
        List<TaskPointsEvent> countable = countableEventsInRange(allEvents, from, to);
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String level : List.of("0", "1", "2", "FAILED")) counts.put(level, 0L);
        for (TaskPointsEvent event : countable) {
            int level = strikeLevelOf(event);
            counts.merge(level == 3 ? "FAILED" : String.valueOf(level), 1L, Long::sum);
        }
        long total = countable.size();
        List<StrikeDistributionSlice> result = new ArrayList<>();
        for (var entry : counts.entrySet()) {
            int percent = total == 0 ? 0 : Math.round(entry.getValue() * 100f / total);
            result.add(new StrikeDistributionSlice(entry.getKey(), entry.getValue(), percent));
        }
        return result;
    }

    /** Currently-open assignments sitting at strike 1 or 2 right now - a live snapshot, not date-bound. */
    private List<AtRiskTaskItem> buildAtRiskTasks(List<Task> liveOpen, List<TaskPointsEvent> allEvents) {
        Map<Long, TaskPointsEvent> latestByAssignment = new LinkedHashMap<>();
        for (TaskPointsEvent event : allEvents) {
            latestByAssignment.putIfAbsent(event.getAssignmentId(), event);
        }
        List<AtRiskTaskItem> result = new ArrayList<>();
        for (Task task : liveOpen) {
            for (TaskAssignment assignment : task.getAssignments()) {
                if (!assignment.isCurrent()) continue;
                TaskPointsEvent latest = latestByAssignment.get(assignment.getId());
                if (latest == null) continue;
                int level = switch (latest.getEventType()) {
                    case STRIKE_1 -> 1;
                    case STRIKE_2 -> 2;
                    default -> 0;
                };
                if (level == 0) continue;
                result.add(new AtRiskTaskItem(task.getId(), task.getTaskNumber(), task.getTitle(),
                    assignment.getId(), assignment.getUser().getName(), level, task.getDueDate()));
            }
        }
        return result.stream()
            .sorted(Comparator.comparing(AtRiskTaskItem::dueDate, Comparator.nullsLast(Comparator.naturalOrder())))
            .toList();
    }

    private EmployeeReportResponse.PointsSummary buildPointsSummary(List<TaskPointsEvent> events, Instant from, Instant to) {
        List<TaskPointsEvent> countable = countableEventsInRange(events, from, to);
        BigDecimal earned = countable.stream()
            .filter(e -> e.getEventType() == PointsEventType.COMPLETED)
            .map(TaskPointsEvent::getResultingPoints).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal possible = countable.stream().map(TaskPointsEvent::getBasePoints).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal lost = countable.stream().map(e -> e.getBasePoints().subtract(e.getResultingPoints())).reduce(BigDecimal.ZERO, BigDecimal::add);
        long failed = countable.stream().filter(e -> e.getEventType() == PointsEventType.FAILED).count();
        return new EmployeeReportResponse.PointsSummary(earned, possible, lost, efficiencyRate(earned, possible), failed);
    }

    private List<EmployeeReportResponse.PointsBreakdownRow> buildPointsBreakdown(List<TaskPointsEvent> events, Instant from, Instant to) {
        List<TaskPointsEvent> countable = countableEventsInRange(events, from, to);
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String level : List.of("FULL", "STRIKE_1", "STRIKE_2", "FAILED")) counts.put(level, 0L);
        for (TaskPointsEvent event : countable) {
            String label = switch (strikeLevelOf(event)) {
                case 0 -> "FULL";
                case 1 -> "STRIKE_1";
                case 2 -> "STRIKE_2";
                default -> "FAILED";
            };
            counts.merge(label, 1L, Long::sum);
        }
        long total = countable.size();
        List<EmployeeReportResponse.PointsBreakdownRow> result = new ArrayList<>();
        for (var entry : counts.entrySet()) {
            int percent = total == 0 ? 0 : Math.round(entry.getValue() * 100f / total);
            result.add(new EmployeeReportResponse.PointsBreakdownRow(entry.getKey(), entry.getValue(), percent));
        }
        return result;
    }

    /** Most recent events overall (not date-range-bound), matching {@link #buildRecentActivity}'s "recent activity" framing. ASSIGNED events are start-of-cycle noise, not a scoring outcome, so they're excluded. */
    private List<PointEventItem> buildPointEventItems(List<TaskPointsEvent> events) {
        List<TaskPointsEvent> relevant = events.stream()
            .filter(e -> e.getEventType() != PointsEventType.ASSIGNED)
            .limit(RECENT_ACTIVITY_LIMIT)
            .toList();
        if (relevant.isEmpty()) return List.of();

        Map<Long, Task> tasksById = taskRepository.findAllById(relevant.stream().map(TaskPointsEvent::getTaskId).distinct().toList())
            .stream().collect(Collectors.toMap(Task::getId, t -> t));

        return relevant.stream().map(event -> {
            Task task = tasksById.get(event.getTaskId());
            return new PointEventItem(event.getOccurredAt(), event.getTaskId(), task == null ? null : task.getTaskNumber(),
                task == null ? "Unknown" : task.getTitle(), event.getAssignmentId(), event.getEventType().name(),
                event.getPointsDelta(), event.getResultingPoints(), event.getReason());
        }).toList();
    }

    /**
     * The full points-relevant ledger, one row per (task, this employee's assignment) - backs the
     * "Individual Tasks" table in the exported reports. Not date-range-filtered by points activity -
     * every task this employee was ever put on within the report's [from, to] window gets a row,
     * whatever its current state, so a still-open task shows up with its live "possible" value and
     * zero deducted, exactly like every other live-points surface in this report.
     */
    private List<EmployeeReportResponse.TaskDetailRow> buildTaskDetails(Long employeeId, List<Task> tasksCreatedInRange, List<TaskPointsEvent> employeeAllEvents) {
        List<EmployeeReportResponse.TaskDetailRow> rows = new ArrayList<>();
        for (Task task : tasksCreatedInRange) {
            List<TaskAssignment> mine = task.getAssignments().stream()
                .filter(a -> a.getUser().getId().equals(employeeId))
                .sorted(Comparator.comparing(TaskAssignment::getAssignedAt))
                .toList();
            if (mine.isEmpty()) continue;

            List<EmployeeReportResponse.DueDateEntry> dueDates = buildDueDateEntries(task,
                dueDateHistoryRepository.findByTaskIdOrderByChangedAtAsc(task.getId()));
            BigDecimal possiblePoints = PointsService.basePointsFor(task.getPriority());

            for (TaskAssignment assignment : mine) {
                List<TaskPointsEvent> assignmentEvents = employeeAllEvents.stream()
                    .filter(e -> e.getAssignmentId().equals(assignment.getId()))
                    .toList();
                BigDecimal deducted = countableEvents(assignmentEvents).stream()
                    .map(e -> e.getBasePoints().subtract(e.getResultingPoints()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                rows.add(new EmployeeReportResponse.TaskDetailRow(task.getId(), task.getTaskNumber(), task.getTitle(),
                    task.getPriority().name(), assignment.getAssignedAt(), task.getStatus().name(), dueDates, possiblePoints, deducted));
            }
        }
        return rows.stream()
            .sorted(Comparator.comparing(EmployeeReportResponse.TaskDetailRow::assignedAt, Comparator.nullsLast(Comparator.reverseOrder())))
            .toList();
    }

    /**
     * The task's original due date first (its very first due date ever, whether set at creation or
     * filled in later after starting in DRAFT), then every later change in order. "first" marks that
     * one entry so callers can label it distinctly from the extensions that follow.
     */
    private List<EmployeeReportResponse.DueDateEntry> buildDueDateEntries(Task task, List<TaskDueDateHistory> historyRows) {
        List<EmployeeReportResponse.DueDateEntry> entries = new ArrayList<>();
        if (historyRows.isEmpty()) {
            if (task.getDueDate() != null) {
                entries.add(new EmployeeReportResponse.DueDateEntry(task.getDueDate(), true, true));
            }
            return entries;
        }
        TaskDueDateHistory firstChange = historyRows.get(0);
        if (firstChange.getPreviousDueDate() != null) {
            entries.add(new EmployeeReportResponse.DueDateEntry(firstChange.getPreviousDueDate(), true, true));
            entries.add(new EmployeeReportResponse.DueDateEntry(firstChange.getNewDueDate(), false, firstChange.isCountsTowardStrikes()));
        } else {
            // Task started in DRAFT with no due date - this first history row IS the original due date, not an extension.
            entries.add(new EmployeeReportResponse.DueDateEntry(firstChange.getNewDueDate(), true, true));
        }
        for (int i = 1; i < historyRows.size(); i++) {
            TaskDueDateHistory row = historyRows.get(i);
            entries.add(new EmployeeReportResponse.DueDateEntry(row.getNewDueDate(), false, row.isCountsTowardStrikes()));
        }
        return entries;
    }

    /**
     * Backs the per-task "Points — This Task" panel. Admin: any assignment. Team member: only an
     * assignment that's theirs - loaded first so we know who owns it before deciding visibility;
     * anyone else's assignment looks exactly like it doesn't exist (404, never 403).
     */
    @Transactional(readOnly = true)
    public TaskPointsResponse getTaskPoints(Long taskId, Long assignmentId, com.slmtires.itms.security.AppUserPrincipal principal) {
        Task task = taskRepository.findById(taskId).orElseThrow(() -> new ResourceNotFoundException("Task not found."));
        List<TaskPointsEvent> history = pointsEventRepository.findByAssignmentIdOrderByOccurredAtAsc(assignmentId);
        if (history.isEmpty() || !history.get(0).getTaskId().equals(taskId)) {
            throw new ResourceNotFoundException("No points history for this assignment.");
        }
        TaskAssignment assignment = task.getAssignments().stream()
            .filter(a -> a.getId().equals(assignmentId))
            .findFirst()
            .orElseThrow(() -> new ResourceNotFoundException("Assignment not found."));

        boolean admin = principal.getUser().getRole() == Role.ADMIN;
        if (!admin && !assignment.getUser().getId().equals(principal.getId())) {
            throw new ResourceNotFoundException("Assignment not found.");
        }

        TaskPointsEvent latest = history.get(history.size() - 1);
        String outcome = switch (latest.getEventType()) {
            case COMPLETED -> "COMPLETED";
            case FAILED -> "FAILED";
            case CANCELLED -> "CANCELLED";
            case REASSIGNED -> "REASSIGNED";
            case ASSIGNED, STRIKE_1, STRIKE_2 -> "OPEN";
        };

        List<TaskPointsResponse.Event> events = history.stream()
            .map(e -> new TaskPointsResponse.Event(e.getOccurredAt(), e.getEventType().name(), e.getPointsDelta(), e.getResultingPoints(), e.getReason()))
            .toList();

        return new TaskPointsResponse(task.getId(), task.getTaskNumber(), task.getTitle(), task.getPriority().name(),
            assignmentId, assignment.getUser().getId(), assignment.getUser().getName(),
            history.get(0).getBasePoints(), latest.getResultingPoints(), outcome, events);
    }
}
