package com.slmtires.itms.service;

import com.slmtires.itms.dto.DueDateChangeRequest;
import com.slmtires.itms.dto.DueDateHistoryView;
import com.slmtires.itms.dto.TaskDueDateHistoryResponse;
import com.slmtires.itms.dto.TaskResponse;
import com.slmtires.itms.entity.Task;
import com.slmtires.itms.entity.TaskDueDateHistory;
import com.slmtires.itms.entity.TaskStatus;
import com.slmtires.itms.entity.User;
import com.slmtires.itms.exception.BadRequestException;
import com.slmtires.itms.exception.ConflictException;
import com.slmtires.itms.repository.TaskDueDateHistoryRepository;
import com.slmtires.itms.repository.TaskRepository;
import com.slmtires.itms.repository.UserRepository;
import com.slmtires.itms.security.AppUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TaskDueDateService {
    private final TaskRepository taskRepository;
    private final TaskDueDateHistoryRepository taskDueDateHistoryRepository;
    private final UserRepository userRepository;
    private final TaskHistoryService historyService;
    private final TaskLifecycleSupport lifecycleSupport;
    private final NotificationService notificationService;
    private final RealtimeEventService realtimeEventService;

    @Transactional
    public TaskResponse changeDueDate(Long taskId, DueDateChangeRequest request, AppUserPrincipal principal) {
        Task task = lifecycleSupport.findTaskForWrite(taskId);
        if (task.getStatus() == TaskStatus.CANCELLED) {
            throw new ConflictException("This task was cancelled. Reinstate it first.");
        }
        if (task.getStatus() == TaskStatus.COMPLETED) {
            throw new ConflictException("This task is completed. Reopen it first.");
        }

        LocalDate newDueDate = request.dueDate();
        LocalDate current = task.getDueDate();
        if (current != null && current.equals(newDueDate)) {
            throw new BadRequestException("That is already the due date.");
        }
        if (current != null && newDueDate == null) {
            throw new BadRequestException("Once set, a due date cannot be cleared.");
        }
        // Reason is optional (spec D1) - never call .trim() on it without a null check.
        String reason = request.reason() == null ? null : request.reason().trim();
        if (reason != null && reason.isBlank()) {
            reason = null;
        }
        if (reason != null && reason.length() > 255) {
            throw new BadRequestException("Reason must be 255 characters or fewer.");
        }

        String kind = resolveKind(current, newDueDate);
        task.setDueDate(newDueDate);
        recordHistory(task, current, newDueDate, principal.getId(), reason, request.shouldDeductPoints());
        // Phase 9: a due-date extension is the ONLY thing that can ever cost points (unless
        // request.deductPoints() is false) - see PointsService. Must run after the due-date-history
        // row above is saved (its own save() triggers Hibernate's auto-flush before
        // evaluateAndSeal's SELECT on that same table, so the just-recorded extension is already
        // visible to it).
        lifecycleSupport.recalculate(task);

        // task is already managed (loaded above in this transaction); flush()
        // persists the change via the existing managed instance. save()/
        // saveAndFlush() would call merge() here, which is only a problem when
        // new child entities are cascaded - due-date changes add none - but
        // using flush() everywhere keeps every task-mutating method consistent.
        taskRepository.flush();

        Map<String, Object> oldValue = new LinkedHashMap<>();
        oldValue.put("dueDate", current == null ? null : current.toString());
        Map<String, Object> newValue = new LinkedHashMap<>();
        newValue.put("dueDate", newDueDate.toString());
        newValue.put("reason", reason);
        newValue.put("kind", kind);
        newValue.put("deductPoints", request.shouldDeductPoints());
        historyService.audit(principal.getId(), "CHANGE_DUE_DATE", taskId, oldValue, newValue);

        String message = "New due date for \"" + task.getTitle() + "\": " + newDueDate + ".";
        for (var assignment : lifecycleSupport.currentAssignments(task)) {
            notificationService.notifyUser(assignment.getUser().getId(), taskId, "DUE_DATE_CHANGED", "Due date changed", message);
        }
        realtimeEventService.refreshTask(taskId);

        return TaskResponse.forAdmin(task);
    }

    @Transactional(readOnly = true)
    public DueDateHistoryView getDueDateHistory(Long taskId, AppUserPrincipal principal) {
        Task task = lifecycleSupport.findTask(taskId);
        lifecycleSupport.requireVisible(task, principal);
        boolean admin = lifecycleSupport.isAdmin(principal);

        List<TaskDueDateHistory> history = taskDueDateHistoryRepository.findByTaskIdOrderByChangedAtAsc(taskId);
        LocalDate original = task.getDueDate();
        if (!history.isEmpty()) {
            TaskDueDateHistory first = history.getFirst();
            original = first.getPreviousDueDate() == null ? first.getNewDueDate() : first.getPreviousDueDate();
        }

        Map<Long, User> actors = admin
            ? userRepository.findAllById(history.stream().map(TaskDueDateHistory::getChangedBy).distinct().toList())
                .stream().collect(java.util.stream.Collectors.toMap(User::getId, u -> u))
            : Map.of();

        List<TaskDueDateHistoryResponse> changes = new ArrayList<>();
        int extensionCount = 0;
        for (TaskDueDateHistory row : history) {
            String kind = resolveKind(row.getPreviousDueDate(), row.getNewDueDate());
            if ("EXTENDED".equals(kind)) extensionCount++;
            User actor = admin ? actors.get(row.getChangedBy()) : null;
            TaskDueDateHistoryResponse.ChangedBy changedBy = actor == null ? null
                : new TaskDueDateHistoryResponse.ChangedBy(actor.getId(), actor.getName());
            changes.add(new TaskDueDateHistoryResponse(
                row.getId(), row.getPreviousDueDate(), row.getNewDueDate(), kind, row.getReason(), changedBy, row.getChangedAt(), row.isCountsTowardStrikes()));
        }
        return new DueDateHistoryView(original, task.getDueDate(), extensionCount, changes);
    }

    private void recordHistory(Task task, LocalDate previousDueDate, LocalDate newDueDate, Long actorId, String reason, boolean countsTowardStrikes) {
        TaskDueDateHistory history = new TaskDueDateHistory();
        history.setTaskId(task.getId());
        history.setPreviousDueDate(previousDueDate);
        history.setNewDueDate(newDueDate);
        history.setChangedBy(actorId);
        history.setReason(reason);
        history.setCountsTowardStrikes(countsTowardStrikes);
        taskDueDateHistoryRepository.save(history);
    }

    private String resolveKind(LocalDate previous, LocalDate next) {
        if (previous == null) return "SET";
        if (next.isAfter(previous)) return "EXTENDED";
        if (next.isBefore(previous)) return "BROUGHT_FORWARD";
        throw new BadRequestException("That is already the due date.");
    }
}
