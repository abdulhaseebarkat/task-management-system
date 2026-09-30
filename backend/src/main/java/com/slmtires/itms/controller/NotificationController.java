package com.slmtires.itms.controller;

import com.slmtires.itms.dto.NotificationResponse;
import com.slmtires.itms.dto.PagedResponse;
import com.slmtires.itms.security.AppUserPrincipal;
import com.slmtires.itms.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {
    private final NotificationService notificationService;

    @GetMapping
    public PagedResponse<NotificationResponse> list(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @AuthenticationPrincipal AppUserPrincipal principal
    ) {
        return notificationService.list(principal.getId(), page, size);
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount(@AuthenticationPrincipal AppUserPrincipal principal) {
        return Map.of("count", notificationService.unreadCount(principal.getId()));
    }

    @PutMapping("/{id}/read")
    public void markRead(@PathVariable Long id, @AuthenticationPrincipal AppUserPrincipal principal) {
        notificationService.markRead(id, principal.getId());
    }

    @PutMapping("/read-all")
    public void markAllRead(@AuthenticationPrincipal AppUserPrincipal principal) {
        notificationService.markAllRead(principal.getId());
    }
}
