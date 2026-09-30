package com.slmtires.itms.repository;

import com.slmtires.itms.entity.TaskDueDateHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaskDueDateHistoryRepository extends JpaRepository<TaskDueDateHistory, Long> {
    List<TaskDueDateHistory> findByTaskIdOrderByChangedAtAsc(Long taskId);
    long countByTaskIdAndNewDueDateNotNull(Long taskId);
}
