package com.slmtires.itms.repository;

import com.slmtires.itms.entity.TaskPointsEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TaskPointsEventRepository extends JpaRepository<TaskPointsEvent, Long> {

    /** The latest event for this assignment - tells us whether it's mid-cycle (ASSIGNED/STRIKE_*)
     * or already closed (COMPLETED/FAILED/CANCELLED/REASSIGNED), and its current strike level. */
    Optional<TaskPointsEvent> findFirstByAssignmentIdOrderByOccurredAtDescIdDesc(Long assignmentId);

    /** Full history for one assignment, every cycle - used for a task's "How It Got There" panel. */
    List<TaskPointsEvent> findByAssignmentIdOrderByOccurredAtAsc(Long assignmentId);

    List<TaskPointsEvent> findByTaskIdOrderByOccurredAtAsc(Long taskId);

    /** Org-wide, for the Admin dashboard's leaderboard/strike-distribution/trend. */
    List<TaskPointsEvent> findByOccurredAtBetweenOrderByOccurredAtDesc(Instant from, Instant to);

    /** One employee's events, for their Individual Report. */
    List<TaskPointsEvent> findByUserIdAndOccurredAtBetweenOrderByOccurredAtDesc(Long userId, Instant from, Instant to);
}
