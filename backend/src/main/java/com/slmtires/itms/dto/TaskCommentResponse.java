package com.slmtires.itms.dto;

import com.slmtires.itms.entity.TaskComment;

import java.time.Instant;

public record TaskCommentResponse(Long id, Assignee author, String comment, Instant createdAt) {
    public record Assignee(Long id, String name) {}

    public static TaskCommentResponse from(TaskComment comment) {
        return new TaskCommentResponse(
            comment.getId(),
            new Assignee(comment.getUser().getId(), comment.getUser().getName()),
            comment.getComment(),
            comment.getCreatedAt());
    }
}
