package com.slmtires.itms.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "task_due_date_history")
@Getter
@Setter
@NoArgsConstructor
public class TaskDueDateHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @Column(name = "previous_due_date")
    private LocalDate previousDueDate;

    @Column(name = "new_due_date", nullable = false)
    private LocalDate newDueDate;

    @Column(name = "changed_by", nullable = false)
    private Long changedBy;

    @Column(length = 255)
    private String reason;

    /** Admin's per-extension choice: whether this due-date change counts as a strike-eligible
     * extension for scoring (PointsService). Still always counts toward the visible extension
     * count / advisory cap regardless of this flag - that's a separate, operational concern. */
    @Column(name = "counts_toward_strikes", nullable = false)
    private boolean countsTowardStrikes = true;

    @CreationTimestamp
    @Column(name = "changed_at", nullable = false, updatable = false)
    private Instant changedAt;
}
