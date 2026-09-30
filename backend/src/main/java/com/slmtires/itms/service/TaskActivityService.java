package com.slmtires.itms.service;

import com.slmtires.itms.dto.TaskActivityResponse;
import com.slmtires.itms.entity.Task;
import com.slmtires.itms.entity.TaskAssignment;
import com.slmtires.itms.entity.TaskDueDateHistory;
import com.slmtires.itms.entity.TaskStatusHistory;
import com.slmtires.itms.entity.TaskReassignmentHistory;
import com.slmtires.itms.entity.User;
import com.slmtires.itms.repository.TaskDueDateHistoryRepository;
import com.slmtires.itms.repository.TaskStatusHistoryRepository;
import com.slmtires.itms.repository.TaskReassignmentHistoryRepository;
import com.slmtires.itms.repository.UserRepository;
import com.slmtires.itms.security.AppUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TaskActivityService {
    private final TaskLifecycleSupport lifecycleSupport;
    private final TaskStatusHistoryRepository statusHistoryRepository;
    private final TaskDueDateHistoryRepository dueDateHistoryRepository;
    private final TaskReassignmentHistoryRepository reassignmentHistoryRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<TaskActivityResponse> getActivity(Long taskId, AppUserPrincipal principal) {
        Task task = lifecycleSupport.findTask(taskId);
        lifecycleSupport.requireVisible(task, principal);
        boolean admin = lifecycleSupport.isAdmin(principal);
        Map<Long, Long> assignmentOwners = task.getAssignments().stream()
            .collect(Collectors.toMap(TaskAssignment::getId, assignment -> assignment.getUser().getId()));
        List<TaskStatusHistory> statuses = statusHistoryRepository.findByTaskIdOrderByCreatedAtDesc(taskId);
        Map<Long, String> actorNames = admin ? userRepository.findAllById(
            statuses.stream().map(TaskStatusHistory::getChangedBy).distinct().toList())
            .stream().collect(Collectors.toMap(User::getId, User::getName)) : Map.of();

        List<TaskActivityResponse> events = new ArrayList<>();
        for (TaskStatusHistory row : statuses) {
            boolean ownAssignment = row.getAssignmentId() != null && principal.getId().equals(assignmentOwners.get(row.getAssignmentId()));
            if (!admin && row.getAssignmentId() != null && !ownAssignment) continue;
            String message;
            if (row.getOldStatus() == null) {
                // A brand-new assignment row (plain add, or the receiving side of a reassignment).
                // The recipient sees this plainly - never who they replaced or why (spec: "Task assigned to you").
                message = !admin && ownAssignment ? "Task assigned to you" : "Status changed to " + row.getNewStatus().replace('_', ' ');
            } else {
                message = row.getOldStatus().replace('_', ' ') + " → " + row.getNewStatus().replace('_', ' ');
            }
            events.add(new TaskActivityResponse(row.getId(), row.getCreatedAt(), "STATUS", message,
                admin ? actorNames.get(row.getChangedBy()) : null));
        }
        for (TaskDueDateHistory row : dueDateHistoryRepository.findByTaskIdOrderByChangedAtAsc(taskId)) {
            String message = "Due date " + (row.getPreviousDueDate() == null ? "set" : "changed")
                + " to " + row.getNewDueDate() + (row.getReason() == null || row.getReason().isBlank() ? "" : ": " + row.getReason());
            events.add(new TaskActivityResponse(-row.getId(), row.getChangedAt(), "DUE_DATE", message, null));
        }
        if (admin) {
            for (TaskReassignmentHistory row : reassignmentHistoryRepository.findByTaskIdOrderByCreatedAtAsc(taskId)) {
                String message = "Work reassigned from " + actorName(row.getFromUserId()) + " to " + actorName(row.getToUserId())
                    + ": " + row.getReason();
                events.add(new TaskActivityResponse(-10_000_000L - row.getId(), row.getCreatedAt(), "REASSIGNMENT", message,
                    actorName(row.getReassignedBy())));
            }
        }
        events.sort(Comparator.comparing(TaskActivityResponse::occurredAt, Comparator.nullsLast(Comparator.reverseOrder())));
        return events;
    }

    private String actorName(Long id) {
        return userRepository.findById(id).map(User::getName).orElse("Unknown user");
    }
}
