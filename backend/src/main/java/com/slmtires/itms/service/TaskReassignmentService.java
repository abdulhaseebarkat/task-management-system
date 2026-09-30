package com.slmtires.itms.service;

import com.slmtires.itms.dto.ReassignTaskRequest;
import com.slmtires.itms.dto.ReassignmentHistoryResponse;
import com.slmtires.itms.dto.TaskResponse;
import com.slmtires.itms.entity.*;
import com.slmtires.itms.exception.BadRequestException;
import com.slmtires.itms.exception.ConflictException;
import com.slmtires.itms.exception.ResourceNotFoundException;
import com.slmtires.itms.repository.TaskReassignmentHistoryRepository;
import com.slmtires.itms.repository.TaskRepository;
import com.slmtires.itms.repository.UserRepository;
import com.slmtires.itms.security.AppUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class TaskReassignmentService {
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final TaskReassignmentHistoryRepository historyRepository;
    private final TaskHistoryService taskHistoryService;
    private final TaskLifecycleSupport lifecycleSupport;
    private final NotificationService notificationService;
    private final RealtimeEventService realtimeEventService;
    private final PointsService pointsService;

    @Transactional
    public TaskResponse reassignTask(Long taskId, ReassignTaskRequest request, AppUserPrincipal principal) {
        Task task = lifecycleSupport.findTaskForWrite(taskId);
        if (task.getStatus() == TaskStatus.CANCELLED || task.getStatus() == TaskStatus.COMPLETED) {
            throw new ConflictException("This task is not open for reassignment.");
        }

        TaskAssignment fromAssignment = lifecycleSupport.currentAssignments(task).stream()
            .filter(a -> Objects.equals(a.getId(), request.fromAssignmentId()))
            .findFirst()
            .orElseThrow(() -> new ResourceNotFoundException("Assignment not found."));

        if (fromAssignment.getStatus() == AssignmentStatus.COMPLETED) {
            throw new ConflictException("Completed work can't be reassigned. Reopen the task first.");
        }

        User toUser = userRepository.findById(request.toUserId())
            .orElseThrow(() -> new ResourceNotFoundException("User not found."));
        if (toUser.getRole() != Role.TEAM_MEMBER || !toUser.isActive()) {
            throw new BadRequestException("Only active team members can be assigned to a task.");
        }
        boolean alreadyAssigned = lifecycleSupport.currentAssignments(task).stream()
            .anyMatch(a -> Objects.equals(a.getUser().getId(), toUser.getId()) && !Objects.equals(a.getId(), fromAssignment.getId()));
        if (alreadyAssigned) {
            throw new ConflictException("That person is already assigned to this task.");
        }

        String reason = request.reason() == null ? "" : request.reason().trim();
        if (reason.isBlank()) throw new BadRequestException("A reassignment reason is required.");
        if (reason.length() > 255) throw new BadRequestException("Reason must be 255 characters or fewer.");

        AssignmentStatus previousStatus = fromAssignment.getStatus();
        short previousProgress = fromAssignment.getProgress();
        fromAssignment.setCurrent(false);
        fromAssignment.setStatus(AssignmentStatus.REASSIGNED);
        fromAssignment.setBlockedReason(null);
        fromAssignment.setOnHoldReason(null);

        TaskAssignment newAssignment = new TaskAssignment();
        newAssignment.setTask(task);
        newAssignment.setUser(toUser);
        newAssignment.setStatus(request.keepProgress() != null && request.keepProgress() && fromAssignment.getProgress() > 0 ? AssignmentStatus.IN_PROGRESS : AssignmentStatus.ASSIGNED);
        newAssignment.setProgress((short) (request.keepProgress() != null && request.keepProgress() ? fromAssignment.getProgress() : 0));
        newAssignment.setCurrent(true);
        newAssignment.setAssignedAt(Instant.now());
        if (newAssignment.getProgress() > 0) {
            newAssignment.setStartedAt(Instant.now());
        }
        task.getAssignments().add(newAssignment);

        lifecycleSupport.recalculate(task);
        // `task` is already managed (loaded earlier in this transaction), so
        // taskRepository.save()/saveAndFlush() would call EntityManager.merge()
        // here - merge() cascades a MERGE (not PERSIST) to the new assignment,
        // which silently persists a *copy* and leaves newAssignment's generated
        // id null on this object. flush() alone uses the existing managed
        // instance and its normal PERSIST cascade, so the id populates in place.
        taskRepository.flush();
        Task saved = task;

        // Phase 9: neutral for the original assignee by default - excluded from scoring entirely,
        // never a penalty - unless the Admin explicitly chose to deduct points for this
        // reassignment, in which case it's sealed as a permanent FAILED (0 points), same as a
        // 3rd-strike failure. Either way, a no-op if they'd already hit a 3rd strike (that FAILED
        // outcome is already permanent). The new assignee starts a completely fresh cycle.
        if (request.shouldDeductPoints()) {
            pointsService.sealFailedByAdmin(task, fromAssignment, "Reassigned away by Admin choice - points forfeited: " + reason);
        } else {
            pointsService.sealNeutralClose(task, fromAssignment, PointsEventType.REASSIGNED, "Reassigned to " + toUser.getName());
        }
        pointsService.sealAssigned(task, newAssignment);

        taskHistoryService.recordStatus(saved.getId(), fromAssignment.getId(), previousStatus.name(), AssignmentStatus.REASSIGNED.name(), principal.getId(), "Reassigned to " + toUser.getName() + ": " + reason);
        taskHistoryService.recordStatus(saved.getId(), newAssignment.getId(), null, newAssignment.getStatus().name(), principal.getId(), "Reassigned from " + fromAssignment.getUser().getName() + ": " + reason);

        TaskReassignmentHistory record = new TaskReassignmentHistory();
        record.setTaskId(task.getId());
        record.setFromAssignmentId(fromAssignment.getId());
        record.setFromUserId(fromAssignment.getUser().getId());
        record.setToAssignmentId(newAssignment.getId());
        record.setToUserId(toUser.getId());
        record.setReassignedBy(principal.getId());
        record.setReason(reason);
        record.setClassification(request.classification());
        record.setFromStatus(previousStatus.name());
        record.setFromProgress(previousProgress);
        record.setToStatus(newAssignment.getStatus().name());
        record.setToProgress(newAssignment.getProgress());
        historyRepository.save(record);

        var oldValue = new LinkedHashMap<String, Object>();
        oldValue.put("fromUserId", fromAssignment.getUser().getId());
        oldValue.put("fromUser", fromAssignment.getUser().getName());
        oldValue.put("status", previousStatus.name());
        oldValue.put("progress", previousProgress);
        var newValue = new LinkedHashMap<String, Object>();
        newValue.put("toUserId", toUser.getId());
        newValue.put("toUser", toUser.getName());
        newValue.put("status", newAssignment.getStatus().name());
        newValue.put("progress", newAssignment.getProgress());
        newValue.put("reason", reason);
        newValue.put("classification", request.classification() == null ? null : request.classification().name());
        newValue.put("keepProgress", Boolean.TRUE.equals(request.keepProgress()));
        newValue.put("deductPoints", request.shouldDeductPoints());
        taskHistoryService.audit(principal.getId(), "REASSIGN_TASK", task.getId(), oldValue, newValue);

        // Per spec: the recipient just hears "assigned to you" - never who they replaced or why.
        notificationService.notifyUser(toUser.getId(), task.getId(), "TASK_ASSIGNED", "Task assigned to you",
            "\"" + task.getTitle() + "\" (" + task.getTaskNumber() + ") was assigned to you.");
        realtimeEventService.refreshTask(task.getId());

        return TaskResponse.forAdmin(saved);
    }

    @Transactional(readOnly = true)
    public List<ReassignmentHistoryResponse> getReassignmentHistory(Long taskId) {
        Task task = lifecycleSupport.findTask(taskId);
        List<TaskReassignmentHistory> records = historyRepository.findByTaskIdOrderByCreatedAtAsc(taskId);
        List<ReassignmentHistoryResponse> response = new ArrayList<>();
        for (TaskReassignmentHistory record : records) {
            User fromUser = userRepository.findById(record.getFromUserId()).orElse(null);
            User toUser = userRepository.findById(record.getToUserId()).orElse(null);
            User reassignedBy = userRepository.findById(record.getReassignedBy()).orElse(null);
            response.add(new ReassignmentHistoryResponse(
                record.getId(),
                record.getCreatedAt(),
                new ReassignmentHistoryResponse.AssigneeSummary(fromUser == null ? null : fromUser.getId(), fromUser == null ? "Unknown" : fromUser.getName(), fromUser != null && fromUser.isActive()),
                new ReassignmentHistoryResponse.AssigneeSummary(toUser == null ? null : toUser.getId(), toUser == null ? "Unknown" : toUser.getName(), toUser != null && toUser.isActive()),
                new ReassignmentHistoryResponse.AssigneeSummary(reassignedBy == null ? null : reassignedBy.getId(), reassignedBy == null ? "Unknown" : reassignedBy.getName(), reassignedBy != null && reassignedBy.isActive()),
                record.getReason(),
                record.getClassification(),
                record.getFromStatus(),
                record.getFromProgress(),
                record.getToStatus(),
                record.getToProgress()));
        }
        return response;
    }
}
