package com.slmtires.itms.service;

import com.slmtires.itms.dto.NotificationResponse;
import com.slmtires.itms.dto.PagedResponse;
import com.slmtires.itms.entity.Notification;
import com.slmtires.itms.entity.Role;
import com.slmtires.itms.exception.ResourceNotFoundException;
import com.slmtires.itms.repository.NotificationRepository;
import com.slmtires.itms.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Creation methods are called from the task services at the moments listed in the spec
 * (assigned/reassigned/due-date changed/reopened/cancelled for the employee side;
 * completed/blocked/on-hold/commented for the Admin side). Every notification also pushes a
 * lightweight "something changed" event over WebSocket (Phase 7); polling remains as a fallback
 * for a client whose socket happens to be disconnected.
 */
@Service
@RequiredArgsConstructor
public class NotificationService {
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final RealtimeEventService realtimeEventService;

    @Transactional
    public void notifyUser(Long userId, Long taskId, String type, String title, String message) {
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setTaskId(taskId);
        notification.setType(type);
        notification.setTitle(title);
        notification.setMessage(message);
        notificationRepository.save(notification);
        userRepository.findById(userId).ifPresent(user -> realtimeEventService.notifyUser(user.getEmail()));
    }

    @Transactional
    public void notifyUsers(Iterable<Long> userIds, Long taskId, String type, String title, String message) {
        for (Long userId : userIds) {
            notifyUser(userId, taskId, type, title, message);
        }
    }

    /** Every Admin gets a copy - there is one today, but this holds if a second is ever added. */
    @Transactional
    public void notifyAdmins(Long taskId, String type, String title, String message) {
        List<Long> adminIds = userRepository.findAll().stream()
            .filter(user -> user.getRole() == Role.ADMIN)
            .map(com.slmtires.itms.entity.User::getId)
            .toList();
        notifyUsers(adminIds, taskId, type, title, message);
    }

    @Transactional(readOnly = true)
    public PagedResponse<NotificationResponse> list(Long userId, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        var result = notificationRepository.findByUserIdOrderByCreatedAtDesc(
            userId, PageRequest.of(Math.max(page, 0), safeSize, Sort.by(Sort.Direction.DESC, "id")));
        return PagedResponse.of(result, NotificationResponse::from);
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long userId) {
        return notificationRepository.countByUserIdAndReadFalse(userId);
    }

    @Transactional
    public void markRead(Long id, Long userId) {
        Notification notification = notificationRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Notification not found."));
        if (!notification.getUserId().equals(userId)) {
            throw new ResourceNotFoundException("Notification not found.");
        }
        notification.setRead(true);
        notificationRepository.save(notification);
    }

    @Transactional
    public void markAllRead(Long userId) {
        notificationRepository.markAllRead(userId);
    }
}
