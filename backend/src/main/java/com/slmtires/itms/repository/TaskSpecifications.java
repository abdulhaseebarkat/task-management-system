package com.slmtires.itms.repository;

import com.slmtires.itms.entity.Task;
import com.slmtires.itms.entity.TaskAssignment;
import com.slmtires.itms.entity.TaskPriority;
import com.slmtires.itms.entity.TaskStatus;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class TaskSpecifications {

    private TaskSpecifications() {
    }

    /** Excludes any task whose status is in the given set - used to keep CANCELLED out of the main
     * list (it lives in the Archive tab instead) and DRAFT out of an employee's view. */
    public static Specification<Task> excludingStatuses(TaskStatus... statuses) {
        return (root, query, cb) -> root.get("status").in((Object[]) statuses).not();
    }

    /**
     * Every argument is optional. assigneeId means "has this person as a CURRENT assignee";
     * it is an EXISTS subquery rather than a join so a task with several matches is never
     * duplicated and paging/counting stay correct.
     */
    public static Specification<Task> matching(String search, TaskStatus status, TaskPriority priority, Long categoryId, Long assigneeId) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (search != null && !search.isBlank()) {
                String pattern = "%" + escapeLike(search.trim().toLowerCase()) + "%";
                predicates.add(cb.or(
                    cb.like(cb.lower(root.get("title")), pattern, '\\'),
                    cb.like(cb.lower(root.get("taskNumber")), pattern, '\\'),
                    cb.like(cb.lower(root.get("description")), pattern, '\\')
                ));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (priority != null) {
                predicates.add(cb.equal(root.get("priority"), priority));
            }
            if (categoryId != null) {
                predicates.add(cb.equal(root.get("category").get("id"), categoryId));
            }
            if (assigneeId != null) {
                Subquery<Long> assigned = query.subquery(Long.class);
                Root<TaskAssignment> assignment = assigned.from(TaskAssignment.class);
                assigned.select(assignment.get("id")).where(
                    cb.equal(assignment.get("task"), root),
                    cb.isTrue(assignment.get("current")),
                    cb.equal(assignment.get("user").get("id"), assigneeId)
                );
                predicates.add(cb.exists(assigned));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    /**
     * For analytics: the same status/priority/category/employee filters as {@link #matching}, plus an
     * optional [from, to) window on the task's createdAt. Either bound may be null for an open range.
     */
    public static Specification<Task> forAnalytics(TaskStatus status, TaskPriority priority, Long categoryId, Long assigneeId, Instant createdFrom, Instant createdTo) {
        return matching(null, status, priority, categoryId, assigneeId).and((root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (createdFrom != null) predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), createdFrom));
            if (createdTo != null) predicates.add(cb.lessThan(root.get("createdAt"), createdTo));
            return cb.and(predicates.toArray(Predicate[]::new));
        });
    }

    /** Same as {@link #forAnalytics}, but windowed on completedAt (and implicitly restricted to completed tasks). */
    public static Specification<Task> forAnalyticsByCompleted(TaskStatus status, TaskPriority priority, Long categoryId, Long assigneeId, Instant completedFrom, Instant completedTo) {
        return matching(null, status, priority, categoryId, assigneeId).and((root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isNotNull(root.get("completedAt")));
            if (completedFrom != null) predicates.add(cb.greaterThanOrEqualTo(root.get("completedAt"), completedFrom));
            if (completedTo != null) predicates.add(cb.lessThan(root.get("completedAt"), completedTo));
            return cb.and(predicates.toArray(Predicate[]::new));
        });
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
