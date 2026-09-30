package com.slmtires.itms.service;

import com.slmtires.itms.entity.AssignmentStatus;
import com.slmtires.itms.entity.PointsEventType;
import com.slmtires.itms.entity.Task;
import com.slmtires.itms.entity.TaskAssignment;
import com.slmtires.itms.entity.TaskDueDateHistory;
import com.slmtires.itms.entity.TaskPointsEvent;
import com.slmtires.itms.entity.TaskPriority;
import com.slmtires.itms.entity.TaskStatus;
import com.slmtires.itms.repository.TaskDueDateHistoryRepository;
import com.slmtires.itms.repository.TaskPointsEventRepository;
import com.slmtires.itms.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Phase 9 scoring. Every task carries a base point value from its priority
 * (LOW=2, MEDIUM=3, HIGH=5, CRITICAL=8). Per assignee, per "cycle" (started
 * by an ASSIGNED event - initial assignment, becoming a reassignment's new
 * assignee, or a fresh start on reopen/reinstate):
 *
 * <p><b>Points are never deducted automatically just because a task sits overdue.</b> The only
 * thing that ever costs points is a due-date extension the Admin explicitly grants with "deduct
 * points" left checked ({@code TaskDueDateHistory.countsTowardStrikes}, defaulted true - see
 * {@code TaskDueDateService}). A task can be arbitrarily overdue, untouched, forever, and still
 * sit at full points - nothing strikes it until an Admin acts. An extension granted with the
 * checkbox unchecked still moves the due date and still counts toward the visible extension count
 * / advisory cap, it just never enters the strike math for any assignee.
 *
 * <ul>
 *   <li>1st point-eligible extension - STRIKE_1, resulting points drop to 50% of base.</li>
 *   <li>2nd point-eligible extension - STRIKE_2, drop to 25% of base.</li>
 *   <li>3rd point-eligible extension - FAILED, 0 points. Terminal for scoring, permanently,
 *       regardless of what happens to the task afterward (kept even if it's later completed
 *       anyway - status and scoring are tracked separately by design). The cap on extensions is
 *       advisory, not enforced - the Admin can still extend again or reassign.</li>
 *   <li>Completed before a 3rd strike - COMPLETED, locks in whatever the strike level left them.</li>
 *   <li>Cancelled or reassigned away before a 3rd strike - neutral, excluded from scoring
 *       entirely (never counted as "possible" or "earned").</li>
 * </ul>
 *
 * No scheduler: strikes are evaluated at natural touch-points (a due-date change, any assignment
 * update, reassignment, reopen/reinstate) via {@link #evaluateAndSeal}, which is idempotent - safe
 * to call as often as needed.
 */
@Service
@RequiredArgsConstructor
public class PointsService {
    private static final BigDecimal HALF = new BigDecimal("0.50");
    private static final BigDecimal QUARTER = new BigDecimal("0.25");
    private static final Set<PointsEventType> TERMINAL = EnumSet.of(
        PointsEventType.COMPLETED, PointsEventType.FAILED, PointsEventType.CANCELLED, PointsEventType.REASSIGNED);

    private final TaskPointsEventRepository pointsEventRepository;
    private final TaskDueDateHistoryRepository dueDateHistoryRepository;
    private final TaskRepository taskRepository;

    public static BigDecimal basePointsFor(TaskPriority priority) {
        return switch (priority) {
            case LOW -> BigDecimal.valueOf(2);
            case MEDIUM -> BigDecimal.valueOf(3);
            case HIGH -> BigDecimal.valueOf(5);
            case CRITICAL -> BigDecimal.valueOf(8);
        };
    }

    /** Starts a fresh scoring cycle for this assignment - initial assignment, a reassignment's new
     * assignee, or a reopen/reinstate giving someone another shot. */
    @Transactional
    public void sealAssigned(Task task, TaskAssignment assignment) {
        BigDecimal basePoints = basePointsFor(task.getPriority());
        seal(task, assignment, PointsEventType.ASSIGNED, basePoints, BigDecimal.ZERO, basePoints, Instant.now(), null);
    }

    /**
     * Closes this assignment's current cycle as neutral (cancelled or reassigned away) - excluded
     * from scoring entirely (never counted as "possible" or "earned", regardless of what
     * resultingPoints ends up holding). A no-op if the cycle is already closed (e.g. already
     * FAILED). resultingPoints carries forward whatever they were actually sitting at (full base
     * points, or less if a strike had already landed) - never reset to 0, which would incorrectly
     * read as "lost everything" on the per-task points panel for an outcome that's supposed to mean
     * "doesn't count at all".
     */
    @Transactional
    public void sealNeutralClose(Task task, TaskAssignment assignment, PointsEventType type, String reason) {
        var latest = pointsEventRepository.findFirstByAssignmentIdOrderByOccurredAtDescIdDesc(assignment.getId());
        if (latest.isPresent() && TERMINAL.contains(latest.get().getEventType())) {
            return;
        }
        BigDecimal basePoints = latest.map(TaskPointsEvent::getBasePoints).orElseGet(() -> basePointsFor(task.getPriority()));
        BigDecimal currentResulting = latest.map(TaskPointsEvent::getResultingPoints).orElse(basePoints);
        seal(task, assignment, type, basePoints, BigDecimal.ZERO, currentResulting, Instant.now(), reason);
    }

    /**
     * Closes this assignment's current cycle as a FAILED outcome by explicit Admin choice - either
     * at reassignment time (points-loss checkbox checked) or directly via the Fail action on an
     * assignment that's still current. Unlike {@link #sealNeutralClose}, this COUNTS in the
     * assignee's scoring totals (0 of however many points were possible), same as a 3rd-strike
     * failure. Permanent, same as any other FAILED seal - kept regardless of what happens to the
     * task afterward. A no-op if the cycle is already closed (e.g. already FAILED from strikes, so
     * there's nothing to change). Does not itself touch the task's live status - the caller must
     * still call {@link TaskLifecycleSupport#recalculate} (or {@link #evaluateAndSeal}), which sets
     * task.status to FAILED if this assignment is still current, or leaves it alone if the caller
     * has already made it non-current (e.g. mid-reassignment).
     */
    @Transactional
    public void sealFailedByAdmin(Task task, TaskAssignment assignment, String reason) {
        var latest = pointsEventRepository.findFirstByAssignmentIdOrderByOccurredAtDescIdDesc(assignment.getId());
        if (latest.isPresent() && TERMINAL.contains(latest.get().getEventType())) {
            return;
        }
        BigDecimal basePoints = latest.map(TaskPointsEvent::getBasePoints).orElseGet(() -> basePointsFor(task.getPriority()));
        BigDecimal currentResulting = latest.map(TaskPointsEvent::getResultingPoints).orElse(basePoints);
        seal(task, assignment, PointsEventType.FAILED, basePoints, currentResulting.negate(), BigDecimal.ZERO, Instant.now(), reason);
    }

    /**
     * Evaluates every current assignment on this task against due-date reality and seals whatever
     * strike/failure/completion events are newly due. Called from every natural task-mutation
     * touch-point (see class Javadoc). Sets task.status to FAILED whenever any current assignment's
     * cycle is sitting at a sealed FAILED outcome (whether just sealed this call or from an earlier
     * one) - re-asserted every call so a later, unrelated recalculate() doesn't silently overwrite
     * it back to a normal derived status. Never overrides a genuine COMPLETED.
     */
    @Transactional
    public void evaluateAndSeal(Task task) {
        boolean anyUnresolvedFailure = false;
        for (TaskAssignment assignment : task.getAssignments()) {
            if (!assignment.isCurrent() || assignment.getId() == null) {
                continue;
            }
            if (evaluateAssignment(task, assignment)) {
                anyUnresolvedFailure = true;
            }
        }
        if (anyUnresolvedFailure && task.getStatus() != TaskStatus.COMPLETED) {
            task.setStatus(TaskStatus.FAILED);
        }
    }

    /** @return true if this assignment's cycle is (now, or already was) closed at FAILED. */
    private boolean evaluateAssignment(Task task, TaskAssignment assignment) {
        List<TaskPointsEvent> history = pointsEventRepository.findByAssignmentIdOrderByOccurredAtAsc(assignment.getId());
        if (history.isEmpty()) {
            return false; // no ASSIGNED sealed yet - defensive, shouldn't happen
        }
        int lastAssignedIndex = -1;
        for (int i = history.size() - 1; i >= 0; i--) {
            if (history.get(i).getEventType() == PointsEventType.ASSIGNED) {
                lastAssignedIndex = i;
                break;
            }
        }
        if (lastAssignedIndex == -1) {
            return false; // defensive
        }
        List<TaskPointsEvent> cycle = history.subList(lastAssignedIndex, history.size());
        PointsEventType lastType = cycle.get(cycle.size() - 1).getEventType();
        if (lastType != PointsEventType.ASSIGNED && TERMINAL.contains(lastType)) {
            return lastType == PointsEventType.FAILED; // already closed - report whether it's a failure
        }

        int strikesSoFar = (int) cycle.stream()
            .filter(e -> e.getEventType() == PointsEventType.STRIKE_1 || e.getEventType() == PointsEventType.STRIKE_2)
            .count();
        Instant cycleStart = cycle.get(0).getOccurredAt();
        BigDecimal basePoints = cycle.get(0).getBasePoints();

        List<TaskDueDateHistory> extensionsThisCycle = dueDateHistoryRepository.findByTaskIdOrderByChangedAtAsc(task.getId()).stream()
            .filter(h -> h.getChangedAt().isAfter(cycleStart))
            .filter(h -> h.getPreviousDueDate() != null && h.getNewDueDate().isAfter(h.getPreviousDueDate()))
            .filter(TaskDueDateHistory::isCountsTowardStrikes)
            .toList();
        // Merely sitting overdue never costs points on its own - only a deliberate, admin-approved
        // due-date extension does (see class Javadoc). A task can be arbitrarily overdue and still
        // sit at full points forever if nobody ever extends it with "deduct points" checked.
        int strikesNow = extensionsThisCycle.size();

        boolean cycleClosed = false;
        for (int level = strikesSoFar + 1; level <= Math.min(strikesNow, 3) && !cycleClosed; level++) {
            Instant occurredAt = level <= extensionsThisCycle.size() ? extensionsThisCycle.get(level - 1).getChangedAt() : Instant.now();
            if (level == 1) {
                BigDecimal resulting = scale(basePoints.multiply(HALF));
                seal(task, assignment, PointsEventType.STRIKE_1, basePoints, resulting.subtract(basePoints), resulting, occurredAt, "1st deadline missed");
            } else if (level == 2) {
                BigDecimal previous = scale(basePoints.multiply(HALF));
                BigDecimal resulting = scale(basePoints.multiply(QUARTER));
                seal(task, assignment, PointsEventType.STRIKE_2, basePoints, resulting.subtract(previous), resulting, occurredAt, "2nd deadline missed");
            } else {
                BigDecimal previous = scale(basePoints.multiply(QUARTER));
                seal(task, assignment, PointsEventType.FAILED, basePoints, previous.negate(), BigDecimal.ZERO, occurredAt, "3rd deadline missed");
                cycleClosed = true;
            }
        }

        if (!cycleClosed && assignment.getStatus() == AssignmentStatus.COMPLETED) {
            int strikesAtCompletion = Math.min(strikesNow, 2);
            BigDecimal finalPoints = scale(switch (strikesAtCompletion) {
                case 1 -> basePoints.multiply(HALF);
                case 2 -> basePoints.multiply(QUARTER);
                default -> basePoints;
            });
            seal(task, assignment, PointsEventType.COMPLETED, basePoints, BigDecimal.ZERO, finalPoints, Instant.now(), null);
        }
        return cycleClosed;
    }

    /**
     * One-time, idempotent catch-up for tasks that existed before this feature shipped: seals a
     * fresh {@code ASSIGNED} baseline - dated now, not backdated - for every current assignment
     * that has no points history yet, then evaluates it immediately so its strike/completion state
     * is accurate right away instead of staying stale until the next unrelated touch. Deliberately
     * NOT dated at the assignment's real {@code assignedAt}: that would let due-date history from
     * before this call (e.g. an incidental date nudge during an old reassignment) get misread as a
     * strike against the current assignee. Cycle start = now means only what happens from this
     * point forward can ever count. Safe to re-run - an assignment with existing history is
     * skipped entirely, so this never touches anything already being tracked.
     */
    @Transactional
    public int backfillMissingBaselines() {
        List<Task> tasks = taskRepository.findAll();
        int seeded = 0;
        for (Task task : tasks) {
            boolean sealedAny = false;
            for (TaskAssignment assignment : task.getAssignments()) {
                if (!assignment.isCurrent() || assignment.getId() == null) continue;
                if (pointsEventRepository.findFirstByAssignmentIdOrderByOccurredAtDescIdDesc(assignment.getId()).isPresent()) continue;
                sealAssigned(task, assignment);
                seeded++;
                sealedAny = true;
            }
            if (sealedAny) {
                evaluateAndSeal(task);
            }
        }
        return seeded;
    }

    private void seal(Task task, TaskAssignment assignment, PointsEventType type, BigDecimal basePoints, BigDecimal delta, BigDecimal resulting, Instant occurredAt, String reason) {
        TaskPointsEvent event = new TaskPointsEvent();
        event.setTaskId(task.getId());
        event.setAssignmentId(assignment.getId());
        event.setUserId(assignment.getUser().getId());
        event.setEventType(type);
        event.setBasePoints(scale(basePoints));
        event.setPointsDelta(scale(delta));
        event.setResultingPoints(scale(resulting));
        event.setReason(reason);
        event.setOccurredAt(occurredAt);
        pointsEventRepository.save(event);
    }

    private BigDecimal scale(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
