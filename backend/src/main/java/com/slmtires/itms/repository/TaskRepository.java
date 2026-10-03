package com.slmtires.itms.repository;

import com.slmtires.itms.entity.Task;
import com.slmtires.itms.entity.TaskStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface TaskRepository extends JpaRepository<Task, Long>, JpaSpecificationExecutor<Task> {

    /** To-one associations only, so paging happens in SQL; assignments load afterwards in batches. */
    @Override
    @EntityGraph(attributePaths = {"category", "createdBy"})
    Page<Task> findAll(Specification<Task> spec, Pageable pageable);

    /** Unpaged - used by analytics, which needs every matching task in memory to aggregate. */
    @Override
    @EntityGraph(attributePaths = {"category", "assignments", "assignments.user"})
    java.util.List<Task> findAll(Specification<Task> spec);

    @Override
    @EntityGraph(attributePaths = {"category", "createdBy", "assignments", "assignments.user"})
    Optional<Task> findById(Long id);

    /** Atomic, so concurrent creates can never be issued the same task number. */
    @Query(value = "SELECT nextval('task_number_seq')", nativeQuery = true)
    long nextTaskNumber();

    /** Cheap id-only lookup, independent of any analytics filter - used to keep still-Draft tasks (not yet real work, invisible to their assignee) out of points totals regardless of what the caller filtered on. */
    @Query("select t.id from Task t where t.status = :status")
    java.util.List<Long> findIdsByStatus(@org.springframework.data.repository.query.Param("status") TaskStatus status);
}
