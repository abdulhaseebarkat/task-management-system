package com.slmtires.itms.service;

import com.slmtires.itms.repository.TaskReassignmentHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Small per-user stats that aren't derivable from the paged task list the
 * employee dashboard already fetches (e.g. reassignment history isn't
 * returned on Task, since a task no longer lists an employee once they're
 * reassigned off it).
 */
@Service
@RequiredArgsConstructor
public class DashboardService {
    private final TaskReassignmentHistoryRepository reassignmentHistoryRepository;

    public Map<String, Long> employeeSummary(Long userId) {
        return Map.of("reassignedAway", reassignmentHistoryRepository.countByFromUserId(userId));
    }
}
