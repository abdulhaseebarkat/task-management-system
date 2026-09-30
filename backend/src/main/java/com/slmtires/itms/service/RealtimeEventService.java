package com.slmtires.itms.service;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Thin wrapper around SimpMessagingTemplate (Phase 7). Every payload here is a lightweight
 * "something changed" signal, never a full state sync - the frontend reacts by invalidating the
 * relevant TanStack Query cache and refetching over the normal REST API, exactly as it already
 * does on a 60s poll or window focus. This keeps one source of truth (the REST response shapes)
 * instead of a second, parallel WebSocket payload schema to keep in sync.
 */
@Service
@RequiredArgsConstructor
public class RealtimeEventService {
    private final SimpMessagingTemplate messagingTemplate;

    /** A notification was created for this user - push straight to their own queue. */
    public void notifyUser(String email) {
        messagingTemplate.convertAndSendToUser(email, "/queue/notifications", (Object) Map.of("type", "NOTIFICATIONS_CHANGED"));
    }

    /** A task reached COMPLETED or BLOCKED - the two events ARCHITECTURE.md scopes this to. */
    public void refreshAdminDashboard() {
        messagingTemplate.convertAndSend("/topic/admin-dashboard", (Object) Map.of("type", "DASHBOARD_REFRESH"));
    }

    /** A task was mutated - anyone with its detail view open should refetch it. */
    public void refreshTask(Long taskId) {
        messagingTemplate.convertAndSend("/topic/tasks/" + taskId, (Object) Map.of("type", "TASK_UPDATED", "taskId", taskId));
    }
}
