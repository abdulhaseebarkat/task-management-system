package com.slmtires.itms.repository;

import com.slmtires.itms.entity.TaskAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TaskAssignmentRepository extends JpaRepository<TaskAssignment, Long> {
    Optional<TaskAssignment> findByTaskIdAndUserIdAndCurrentTrue(Long taskId, Long userId);
}
