package com.slmtires.itms.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "task_reassignment_history")
@Getter
@Setter
@NoArgsConstructor
public class TaskReassignmentHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @Column(name = "from_assignment_id", nullable = false)
    private Long fromAssignmentId;

    @Column(name = "from_user_id", nullable = false)
    private Long fromUserId;

    @Column(name = "to_assignment_id", nullable = false)
    private Long toAssignmentId;

    @Column(name = "to_user_id", nullable = false)
    private Long toUserId;

    @Column(name = "reassigned_by", nullable = false)
    private Long reassignedBy;

    @Column(nullable = false, length = 255)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private ReassignmentClassification classification;

    @Column(name = "from_status", nullable = false, length = 20)
    private String fromStatus;

    @Column(name = "from_progress", nullable = false)
    private short fromProgress;

    @Column(name = "to_status", nullable = false, length = 20)
    private String toStatus;

    @Column(name = "to_progress", nullable = false)
    private short toProgress;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
