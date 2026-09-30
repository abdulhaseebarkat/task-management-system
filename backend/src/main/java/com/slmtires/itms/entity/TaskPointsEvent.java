package com.slmtires.itms.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Append-only (see V7 migration trigger). One row per scoring event on one assignment - see
 * PointsService for the full rules on when each event_type is written.
 */
@Entity
@Table(name = "task_points_events")
@Getter
@Setter
@NoArgsConstructor
public class TaskPointsEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @Column(name = "assignment_id", nullable = false)
    private Long assignmentId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 20)
    private PointsEventType eventType;

    @Column(name = "base_points", nullable = false, precision = 5, scale = 2)
    private BigDecimal basePoints;

    @Column(name = "points_delta", nullable = false, precision = 5, scale = 2)
    private BigDecimal pointsDelta;

    @Column(name = "resulting_points", nullable = false, precision = 5, scale = 2)
    private BigDecimal resultingPoints;

    @Column(length = 255)
    private String reason;

    /** The real moment this happened (e.g. a due-date-history row's changedAt for a strike), which
     * may be earlier than createdAt if this row was sealed retroactively when the assignment closed. */
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
