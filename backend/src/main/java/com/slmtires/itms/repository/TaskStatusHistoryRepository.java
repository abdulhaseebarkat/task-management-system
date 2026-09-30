package com.slmtires.itms.repository;

import com.slmtires.itms.entity.TaskStatusHistory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TaskStatusHistoryRepository extends JpaRepository<TaskStatusHistory, Long> {
    List<TaskStatusHistory> findByTaskIdOrderByCreatedAtDesc(Long taskId);

    /** Recent activity for one person's own report - events on assignments that belong to them, across every task. */
    @Query("SELECT h FROM TaskStatusHistory h JOIN TaskAssignment a ON h.assignmentId = a.id WHERE a.user.id = :userId ORDER BY h.createdAt DESC")
    List<TaskStatusHistory> findRecentForAssignee(@Param("userId") Long userId, Pageable pageable);
}
