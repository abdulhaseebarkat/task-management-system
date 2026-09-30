package com.slmtires.itms.repository;

import com.slmtires.itms.entity.TaskComment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaskCommentRepository extends JpaRepository<TaskComment, Long> {
    @EntityGraph(attributePaths = "user")
    List<TaskComment> findByTaskIdOrderByCreatedAtAsc(Long taskId);
}
