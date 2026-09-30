package com.slmtires.itms.dto;

import com.slmtires.itms.entity.Notification;

import java.time.Instant;

public record NotificationResponse(
    Long id,
    String type,
    String title,
    String message,
    Long taskId,
    boolean read,
    Instant createdAt
) {
    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
            notification.getId(), notification.getType(), notification.getTitle(), notification.getMessage(),
            notification.getTaskId(), notification.isRead(), notification.getCreatedAt());
    }
}
