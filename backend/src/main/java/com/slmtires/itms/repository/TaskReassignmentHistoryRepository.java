package com.slmtires.itms.repository;

import com.slmtires.itms.entity.TaskReassignmentHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface TaskReassignmentHistoryRepository extends JpaRepository<TaskReassignmentHistory, Long> {
    List<TaskReassignmentHistory> findByTaskIdOrderByCreatedAtAsc(Long taskId);
    long countByTaskId(Long taskId);
    long countByFromUserId(Long fromUserId);
    long countByTaskIdInAndCreatedAtBetween(Collection<Long> taskIds, Instant from, Instant to);
    long countByFromUserIdAndCreatedAtBetween(Long fromUserId, Instant from, Instant to);
    List<TaskReassignmentHistory> findByFromUserIdAndCreatedAtBetweenOrderByCreatedAtDesc(Long fromUserId, Instant from, Instant to);
}
