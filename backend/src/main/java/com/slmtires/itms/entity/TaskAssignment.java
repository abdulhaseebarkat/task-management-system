package com.slmtires.itms.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "task_assignments")
@Getter
@Setter
@NoArgsConstructor
public class TaskAssignment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id")
    private Task task;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AssignmentStatus status = AssignmentStatus.ASSIGNED;

    @Column(nullable = false)
    private short progress;

    @Column(name = "is_current", nullable = false)
    private boolean current = true;

    @Column(name = "blocked_reason", length = 255)
    private String blockedReason;

    @Column(name = "on_hold_reason", length = 255)
    private String onHoldReason;

    @Version
    @Column(nullable = false)
    private int version;

    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt = Instant.now();

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    /** Progress just before the employee marked their part COMPLETED; used to restore it when an Admin reopens the task. */
    @Column(name = "progress_before_completion")
    private Short progressBeforeCompletion;
}
