package com.slmtires.itms.service;

import com.slmtires.itms.dto.CategoryResponse;
import com.slmtires.itms.dto.PagedResponse;
import com.slmtires.itms.dto.TaskListQuery;
import com.slmtires.itms.dto.TaskRequest;
import com.slmtires.itms.dto.TaskResponse;
import com.slmtires.itms.dto.UpdateAssignmentRequest;
import com.slmtires.itms.entity.*;
import com.slmtires.itms.entity.TaskDueDateHistory;
import com.slmtires.itms.exception.BadRequestException;
import com.slmtires.itms.exception.ConflictException;
import com.slmtires.itms.exception.ResourceNotFoundException;
import com.slmtires.itms.repository.TaskCategoryRepository;
import com.slmtires.itms.repository.TaskDueDateHistoryRepository;
import com.slmtires.itms.repository.TaskReassignmentHistoryRepository;
import com.slmtires.itms.repository.TaskRepository;
import com.slmtires.itms.repository.TaskSpecifications;
import com.slmtires.itms.repository.UserRepository;
import com.slmtires.itms.security.AppUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Task lifecycle. Task status is never set directly - it is derived from the
 * current assignments (see TaskStatusRules, applied via TaskLifecycleSupport)
 * - except for the explicit actions cancel, reinstate and reopen. Every
 * change also writes to task_status_history / audit_logs via TaskHistoryService.
 */
@Service
@RequiredArgsConstructor
public class TaskService {
    private static final int MAX_PAGE_SIZE = 100;

    /** Public sort keys -> entity properties. */
    private static final Map<String, String> SORT_FIELDS = Map.of(
        "updated", "updatedAt",
        "created", "createdAt",
        "due", "dueDate",
        "priority", "priorityRank",
        "title", "title",
        "number", "taskNumber",
        "progress", "overallProgress"
    );

    private final TaskRepository taskRepository;
    private final TaskCategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final TaskHistoryService history;
    private final TaskDueDateHistoryRepository dueDateHistoryRepository;
    private final TaskReassignmentHistoryRepository reassignmentHistoryRepository;
    private final TaskLifecycleSupport lifecycleSupport;
    private final NotificationService notificationService;
    private final RealtimeEventService realtimeEventService;
    private final PointsService pointsService;

    private record RemovedAssignment(TaskAssignment assignment, AssignmentStatus oldStatus) {}

    private record AssignmentChanges(List<TaskAssignment> added, List<RemovedAssignment> removed) {}

    /**
     * Admin: every task. Employee: only tasks they are a current assignee of, whatever filters they send.
     * Deliberately does NOT compute originalDueDate/dueDateExtensions/reassignmentCount here: those need
     * 2 extra queries per task, and the list is paged (up to 100 rows) - see getTask() for the single-task,
     * fully-enriched response.
     */
    @Transactional(readOnly = true)
    public PagedResponse<TaskResponse> listTasks(TaskListQuery query, AppUserPrincipal principal) {
        boolean admin = lifecycleSupport.isAdmin(principal);
        Long assigneeId = admin ? query.assigneeId() : principal.getId();

        Sort sort = sortFor(query.sort(), query.direction()).and(Sort.by(Sort.Direction.DESC, "id"));
        int size = Math.min(Math.max(query.size(), 1), MAX_PAGE_SIZE);
        PageRequest pageable = PageRequest.of(Math.max(query.page(), 0), size, sort);

        // Archive tab: cancelled tasks only, whatever other filters are set. Otherwise: the normal
        // list never shows a cancelled task (it lives only in Archive), and an employee additionally
        // never sees a DRAFT task (Admin-only until it has both a due date and assignees).
        TaskStatus statusFilter = query.archived() ? TaskStatus.CANCELLED : query.status();
        Specification<Task> spec = TaskSpecifications.matching(query.search(), statusFilter, query.priority(), query.categoryId(), assigneeId);
        if (!query.archived()) {
            spec = spec.and(TaskSpecifications.excludingStatuses(TaskStatus.CANCELLED));
            if (!admin) {
                spec = spec.and(TaskSpecifications.excludingStatuses(TaskStatus.DRAFT));
            }
        }

        Page<Task> page = taskRepository.findAll(spec, pageable);
        return PagedResponse.of(page, task -> admin ? TaskResponse.forAdmin(task) : TaskResponse.forEmployee(task, principal.getId()));
    }

    @Transactional(readOnly = true)
    public TaskResponse getTask(Long id, AppUserPrincipal principal) {
        Task task = lifecycleSupport.findTask(id);
        lifecycleSupport.requireVisible(task, principal);
        if (!lifecycleSupport.isAdmin(principal)) {
            return TaskResponse.forEmployee(task, principal.getId());
        }
        return buildEnrichedAdminResponse(task);
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> listCategories() {
        return categoryRepository.findAllByActiveTrueOrderByNameAsc().stream()
            .map(category -> new CategoryResponse(category.getId(), category.getName()))
            .toList();
    }

    @Transactional
    public TaskResponse createTask(TaskRequest request, AppUserPrincipal principal) {
        Task task = new Task();
        task.setTaskNumber("TASK-%06d".formatted(taskRepository.nextTaskNumber()));
        task.setCreatedBy(principal.getUser());
        applyFields(task, request, true);
        AssignmentChanges changes = replaceAssignments(task, request.safeAssigneeIds());
        lifecycleSupport.recalculate(task);
        // task is brand new here (never persisted), so save() correctly calls
        // EntityManager.persist() - cascade-persists the new assignments too,
        // populating their generated ids in place. See TaskReassignmentService
        // for what goes wrong when the parent is already managed instead.
        Task saved = taskRepository.saveAndFlush(task);

        Long actor = principal.getId();
        history.recordStatus(saved.getId(), null, null, saved.getStatus().name(), actor, "Task created");
        history.audit(actor, "CREATE_TASK", saved.getId(), null, snapshot(saved));
        recordAssignmentChanges(saved, changes, actor);
        return TaskResponse.forAdmin(saved);
    }

    @Transactional
    public TaskResponse updateTask(Long id, TaskRequest request, AppUserPrincipal principal) {
        Task task = lifecycleSupport.findTask(id);
        if (task.getStatus() == TaskStatus.CANCELLED) {
            throw new ConflictException("This task was cancelled and can no longer be edited. Reinstate it first.");
        }
        Map<String, Object> before = snapshot(task);
        TaskStatus oldStatus = task.getStatus();
        TaskPriority oldPriority = task.getPriority();

        // Filling in a due date the task never had (completing a DRAFT) is a plain field edit -
        // there's no prior deadline to record history against. Changing or clearing an EXISTING
        // due date must still pass through TaskDueDateService, so it is validated and recorded in
        // the append-only history table (and, per Phase 9, evaluated for points).
        boolean fillingInDueDate = task.getDueDate() == null && request.dueDate() != null;
        if (!fillingInDueDate && !Objects.equals(task.getDueDate(), request.dueDate())) {
            throw new BadRequestException("Use the dedicated due-date action to change a task deadline.");
        }

        applyFields(task, request, fillingInDueDate);
        // Re-base any still-open assignment onto the new priority's points - otherwise "possible
        // points" keeps reflecting whatever priority was in effect when that cycle started.
        pointsService.recalculateBasePointsForPriorityChange(task, oldPriority);
        AssignmentChanges changes = replaceAssignments(task, request.safeAssigneeIds());
        lifecycleSupport.recalculate(task);
        // task is already managed (loaded above) and this may have added new
        // TaskAssignment rows: save()/saveAndFlush() would call merge() here,
        // which cascades a MERGE (not PERSIST) to those new rows and leaves
        // their generated id null on the objects we still hold a reference to
        // (recordAssignmentChanges below needs those ids). flush() alone uses
        // the existing managed instance, so ids populate in place.
        taskRepository.flush();
        Task saved = task;

        Long actor = principal.getId();
        recordFieldChanges(saved.getId(), before, snapshot(saved), actor);
        recordAssignmentChanges(saved, changes, actor);
        recordTaskStatusChange(saved, oldStatus, actor, "Recalculated after assignment change");
        realtimeEventService.refreshTask(saved.getId());
        return TaskResponse.forAdmin(saved);
    }

    /** An employee updating their own work. A task they are not on looks exactly like a task that does not exist. */
    @Transactional
    public TaskResponse updateAssignment(Long taskId, UpdateAssignmentRequest request, AppUserPrincipal principal) {
        Task task = lifecycleSupport.findTask(taskId);
        TaskAssignment assignment = lifecycleSupport.currentAssignments(task).stream()
            .filter(item -> Objects.equals(item.getUser().getId(), principal.getId()))
            .findFirst()
            .orElseThrow(() -> new ResourceNotFoundException("Task not found."));

        if (task.getStatus() == TaskStatus.CANCELLED) {
            throw new ConflictException("This task was cancelled, so it can no longer be updated.");
        }
        if (assignment.getStatus() == AssignmentStatus.COMPLETED) {
            throw new ConflictException("Your work on this task is completed. Ask an Admin to reopen the task if it needs more work.");
        }
        Task saved = applyAssignmentUpdate(task, assignment, request, principal.getId(), false);
        return TaskResponse.forEmployee(saved, principal.getId());
    }

    /** An Admin adjusting one person's status / progress. Same validation rules as an employee's own update. */
    @Transactional
    public TaskResponse adjustAssignment(Long taskId, Long assignmentId, UpdateAssignmentRequest request, AppUserPrincipal principal) {
        Task task = lifecycleSupport.findTask(taskId);
        if (task.getStatus() == TaskStatus.CANCELLED) {
            throw new ConflictException("This task was cancelled, so its work can no longer be adjusted. Reinstate it first.");
        }
        TaskAssignment assignment = lifecycleSupport.currentAssignment(task, assignmentId);
        Task saved = applyAssignmentUpdate(task, assignment, request, principal.getId(), true);
        return TaskResponse.forAdmin(saved);
    }

    /**
     * Admin marks one still-current assignment as failed directly - a third way into the terminal
     * FAILED points outcome, alongside the due-date-extension 3-strike path and a reassignment with
     * the points-loss checkbox. Same mechanism either way (see PointsService#sealFailedByAdmin):
     * permanent 0 points for that assignee, and the task's own derived status becomes FAILED since
     * this assignment stays current. The assignment's own status/progress are left untouched -
     * status and scoring are tracked separately by design, same as the strike path.
     */
    @Transactional
    public TaskResponse failAssignment(Long taskId, Long assignmentId, String reason, AppUserPrincipal principal) {
        Task task = lifecycleSupport.findTask(taskId);
        if (task.getStatus() == TaskStatus.CANCELLED) {
            throw new ConflictException("This task was cancelled, so it can no longer be marked as failed. Reinstate it first.");
        }
        if (task.getStatus() == TaskStatus.COMPLETED) {
            throw new ConflictException("This task is completed, so it can no longer be marked as failed. Reopen it first.");
        }
        TaskAssignment assignment = lifecycleSupport.currentAssignment(task, assignmentId);
        if (assignment.getStatus() == AssignmentStatus.COMPLETED) {
            throw new ConflictException("This person's work is already completed, so it can no longer be marked as failed.");
        }

        TaskStatus oldTaskStatus = task.getStatus();
        Long actor = principal.getId();
        String trimmedReason = reason.trim();

        pointsService.sealFailedByAdmin(task, assignment, trimmedReason);
        lifecycleSupport.recalculate(task);
        taskRepository.flush();
        Task saved = task;

        history.audit(actor, "FAIL_ASSIGNMENT", taskId,
            mapOf("userId", assignment.getUser().getId(), "user", assignment.getUser().getName()),
            mapOf("userId", assignment.getUser().getId(), "user", assignment.getUser().getName(), "reason", trimmedReason));
        recordTaskStatusChange(saved, oldTaskStatus, actor, "Marked as failed by Admin: " + trimmedReason);
        notificationService.notifyUser(assignment.getUser().getId(), taskId, "TASK_FAILED", "Task marked as failed",
            "\"" + task.getTitle() + "\" (" + task.getTaskNumber() + ") was marked as failed by an Admin.");
        realtimeEventService.refreshAdminDashboard();
        realtimeEventService.refreshTask(taskId);
        return TaskResponse.forAdmin(saved);
    }

    /**
     * Cancels the task. Unfinished assignments become CANCELLED (their progress is
     * kept); assignments already COMPLETED are left alone so finished work is never rewritten.
     */
    @Transactional
    public void cancelTask(Long id, AppUserPrincipal principal) {
        Task task = lifecycleSupport.findTask(id);
        if (task.getStatus() == TaskStatus.CANCELLED) {
            return;
        }
        TaskStatus oldStatus = task.getStatus();
        Long actor = principal.getId();

        for (TaskAssignment assignment : lifecycleSupport.currentAssignments(task)) {
            if (assignment.getStatus() == AssignmentStatus.COMPLETED) {
                continue;
            }
            AssignmentStatus old = assignment.getStatus();
            assignment.setStatus(AssignmentStatus.CANCELLED);
            assignment.setBlockedReason(null);
            assignment.setOnHoldReason(null);
            history.recordStatus(id, assignment.getId(), old.name(), AssignmentStatus.CANCELLED.name(), actor, "Task cancelled");
            pointsService.sealNeutralClose(task, assignment, PointsEventType.CANCELLED, "Task cancelled");
        }
        task.setStatus(TaskStatus.CANCELLED);
        task.setCancelledAt(Instant.now());
        taskRepository.flush();

        history.recordStatus(id, null, oldStatus.name(), TaskStatus.CANCELLED.name(), actor, "Task cancelled");
        history.audit(actor, "CANCEL_TASK", id, mapOf("status", oldStatus.name()), mapOf("status", TaskStatus.CANCELLED.name()));
        notifyAssignees(task, "TASK_CANCELLED", "Task cancelled", "\"" + task.getTitle() + "\" was cancelled.");
        realtimeEventService.refreshTask(id);
    }

    /**
     * Brings a cancelled task back instead of re-creating it. People whose work was cancelled resume
     * where they stopped (progress kept): IN_PROGRESS if they had progress, otherwise ASSIGNED.
     * Blocked/on-hold reasons are not restored - the person can set those again.
     */
    @Transactional
    public TaskResponse reinstateTask(Long id, AppUserPrincipal principal) {
        Task task = lifecycleSupport.findTask(id);
        if (task.getStatus() != TaskStatus.CANCELLED) {
            throw new ConflictException("Only cancelled tasks can be reinstated.");
        }
        Long actor = principal.getId();
        Instant now = Instant.now();

        for (TaskAssignment assignment : lifecycleSupport.currentAssignments(task)) {
            if (assignment.getStatus() != AssignmentStatus.CANCELLED) {
                continue;
            }
            AssignmentStatus restored = TaskStatusRules.statusAfterReinstate(assignment.getProgress());
            assignment.setStatus(restored);
            if (restored == AssignmentStatus.IN_PROGRESS && assignment.getStartedAt() == null) {
                assignment.setStartedAt(now);
            }
            history.recordStatus(id, assignment.getId(), AssignmentStatus.CANCELLED.name(), restored.name(), actor, "Task reinstated");
            // Fresh scoring cycle - the CANCELLED close above was neutral and excluded, so this
            // is a clean restart, not a continuation of a penalized run.
            pointsService.sealAssigned(task, assignment);
        }
        task.setCancelledAt(null);
        task.setStatus(TaskStatus.DRAFT);
        lifecycleSupport.recalculate(task);
        taskRepository.flush();
        Task saved = task;

        history.recordStatus(id, null, TaskStatus.CANCELLED.name(), saved.getStatus().name(), actor, "Task reinstated");
        history.audit(actor, "REINSTATE_TASK", id, mapOf("status", TaskStatus.CANCELLED.name()), mapOf("status", saved.getStatus().name()));
        realtimeEventService.refreshTask(id);
        return TaskResponse.forAdmin(saved);
    }

    /**
     * Reopens a completed task.
     *  keepProgress = false: everyone restarts at ASSIGNED / 0%, start and completion times cleared.
     *  keepProgress = true:  everyone resumes as IN_PROGRESS at the progress they had before completing
     *                        (their start time is kept, completion time cleared).
     * The original timestamps always remain in task_status_history.
     */
    @Transactional
    public TaskResponse reopenTask(Long id, boolean keepProgress, AppUserPrincipal principal) {
        Task task = lifecycleSupport.findTask(id);
        if (task.getStatus() != TaskStatus.COMPLETED) {
            throw new ConflictException("Only completed tasks can be reopened.");
        }
        Long actor = principal.getId();
        Instant now = Instant.now();
        String comment = keepProgress ? "Task reopened (progress kept)" : "Task reopened (progress reset)";

        for (TaskAssignment assignment : lifecycleSupport.currentAssignments(task)) {
            AssignmentStatus restored;
            if (keepProgress) {
                restored = AssignmentStatus.IN_PROGRESS;
                assignment.setProgress((short) TaskStatusRules.restoredProgress(assignment.getProgressBeforeCompletion()));
                if (assignment.getStartedAt() == null) {
                    assignment.setStartedAt(now);
                }
            } else {
                restored = AssignmentStatus.ASSIGNED;
                assignment.setProgress((short) 0);
                assignment.setStartedAt(null);
            }
            assignment.setStatus(restored);
            assignment.setCompletedAt(null);
            assignment.setProgressBeforeCompletion(null);
            history.recordStatus(id, assignment.getId(), AssignmentStatus.COMPLETED.name(), restored.name(), actor, comment);
            // Fresh scoring cycle - a genuine second chance, independent of how the last one ended.
            pointsService.sealAssigned(task, assignment);
        }
        task.setStatus(TaskStatus.REOPENED);
        lifecycleSupport.recalculate(task);
        taskRepository.flush();
        Task saved = task;

        history.recordStatus(id, null, TaskStatus.COMPLETED.name(), saved.getStatus().name(), actor, comment);
        history.audit(actor, "REOPEN_TASK", id,
            mapOf("status", TaskStatus.COMPLETED.name()),
            mapOf("status", saved.getStatus().name(), "progress", keepProgress ? "KEPT" : "RESET"));
        notifyAssignees(saved, "TASK_REOPENED", "Task reopened", "\"" + saved.getTitle() + "\" was reopened.");
        realtimeEventService.refreshTask(id);
        return TaskResponse.forAdmin(saved);
    }

    /** Shared by an employee's own update and an Admin's adjustment. Callers do their own permission checks first. */
    private Task applyAssignmentUpdate(Task task, TaskAssignment assignment, UpdateAssignmentRequest request, Long actor, boolean byAdmin) {
        int progress = request.progress();
        TaskStatusRules.validateAssignmentUpdate(request.status(), progress, request.reason());

        AssignmentStatus oldStatus = assignment.getStatus();
        int oldProgress = assignment.getProgress();
        TaskStatus oldTaskStatus = task.getStatus();
        String reason = request.reason() == null ? null : request.reason().trim();
        Instant now = Instant.now();

        assignment.setStatus(request.status());
        assignment.setProgress((short) progress);
        assignment.setBlockedReason(request.status() == AssignmentStatus.BLOCKED ? reason : null);
        assignment.setOnHoldReason(request.status() == AssignmentStatus.ON_HOLD ? reason : null);
        if (assignment.getStartedAt() == null) {
            assignment.setStartedAt(now);
        }
        if (request.status() == AssignmentStatus.COMPLETED) {
            if (oldStatus != AssignmentStatus.COMPLETED) {
                assignment.setProgressBeforeCompletion((short) Math.min(oldProgress, 99));
                assignment.setCompletedAt(now);
            }
        } else {
            assignment.setCompletedAt(null);
            assignment.setProgressBeforeCompletion(null);
        }

        lifecycleSupport.recalculate(task);
        taskRepository.flush();
        Task saved = task;

        Long userId = assignment.getUser().getId();
        String note = byAdmin ? (reason == null ? "Adjusted by Admin" : "Adjusted by Admin: " + reason) : reason;
        if (oldStatus != request.status()) {
            history.recordStatus(task.getId(), assignment.getId(), oldStatus.name(), request.status().name(), actor, note);
            history.audit(actor, "CHANGE_STATUS", task.getId(),
                mapOf("scope", "assignment", "userId", userId, "status", oldStatus.name()),
                mapOf("scope", "assignment", "userId", userId, "status", request.status().name(), "reason", reason, "adjustedByAdmin", byAdmin));
        }
        if (oldProgress != progress) {
            history.audit(actor, "CHANGE_PROGRESS", task.getId(),
                mapOf("userId", userId, "progress", oldProgress),
                mapOf("userId", userId, "progress", progress, "adjustedByAdmin", byAdmin));
        }
        recordTaskStatusChange(saved, oldTaskStatus, actor, "Derived from assignment updates");
        if (!byAdmin && oldStatus != request.status()) {
            notifyAdminsOfEmployeeUpdate(saved, assignment.getUser().getName(), request.status(), reason);
        }
        // Scoped narrowly to completion/block, per ARCHITECTURE.md's real-time section - not every
        // assignment edit needs to push a live KPI refresh to every open Admin dashboard.
        if (oldStatus != request.status() && (request.status() == AssignmentStatus.COMPLETED || request.status() == AssignmentStatus.BLOCKED)) {
            realtimeEventService.refreshAdminDashboard();
        }
        realtimeEventService.refreshTask(task.getId());
        return saved;
    }

    /** The three employee-driven events the Admin is meant to hear about immediately (spec section 26). */
    private void notifyAdminsOfEmployeeUpdate(Task task, String employeeName, AssignmentStatus newStatus, String reason) {
        String taskLabel = "\"" + task.getTitle() + "\" (" + task.getTaskNumber() + ")";
        switch (newStatus) {
            case COMPLETED -> notificationService.notifyAdmins(task.getId(), "TASK_COMPLETED",
                "Task completed", employeeName + " completed " + taskLabel + ".");
            case BLOCKED -> notificationService.notifyAdmins(task.getId(), "TASK_BLOCKED",
                "Task blocked", employeeName + " marked " + taskLabel + " as blocked" + (reason == null ? "." : ": " + reason));
            case ON_HOLD -> notificationService.notifyAdmins(task.getId(), "TASK_ON_HOLD",
                "Task on hold", employeeName + " put " + taskLabel + " on hold" + (reason == null ? "." : ": " + reason));
            default -> { }
        }
    }

    private void notifyAssignees(Task task, String type, String title, String message) {
        for (TaskAssignment assignment : lifecycleSupport.currentAssignments(task)) {
            notificationService.notifyUser(assignment.getUser().getId(), task.getId(), type, title, message);
        }
    }

    private void applyFields(Task task, TaskRequest request, boolean setDueDate) {
        task.setTitle(request.title().trim());
        task.setDescription(request.description());
        task.setPriority(request.priority());
        if (setDueDate) {
            task.setDueDate(request.dueDate());
        }
        // Must be active to be newly SELECTED (on create, or switching a task to it) - but a task
        // keeps whatever category it already has even if that category is deactivated afterward,
        // same as an already-assigned team member staying assigned after deactivation. Without this
        // check, editing any other field on a task would break the moment its category is retired.
        Long currentCategoryId = task.getCategory() == null ? null : task.getCategory().getId();
        boolean categoryUnchanged = Objects.equals(currentCategoryId, request.categoryId());
        TaskCategory category = categoryRepository.findById(request.categoryId())
            .orElseThrow(() -> new ResourceNotFoundException("Task category not found."));
        if (!categoryUnchanged && !category.isActive()) {
            throw new ResourceNotFoundException("Task category not found.");
        }
        task.setCategory(category);
    }

    /**
     * Makes the task's current assignees match assigneeIds. People who are already assigned stay
     * assigned even if they have since been deactivated; anyone newly added must be an active team
     * member. Removed people are marked REMOVED (or keep COMPLETED if they had finished) - never
     * REASSIGNED, which is reserved for the reassignment flow.
     */
    private AssignmentChanges replaceAssignments(Task task, List<Long> assigneeIds) {
        List<TaskAssignment> current = lifecycleSupport.currentAssignments(task);
        Set<Long> currentUserIds = current.stream().map(item -> item.getUser().getId()).collect(Collectors.toSet());

        List<Long> newUserIds = assigneeIds.stream().filter(id -> !currentUserIds.contains(id)).toList();
        List<User> newUsers = userRepository.findAllById(newUserIds);
        boolean invalid = newUsers.size() != newUserIds.size()
            || newUsers.stream().anyMatch(user -> user.getRole() != Role.TEAM_MEMBER || !user.isActive());
        if (invalid) {
            throw new BadRequestException("Tasks can only be assigned to active team members.");
        }

        List<RemovedAssignment> removed = new ArrayList<>();
        for (TaskAssignment assignment : current) {
            if (assigneeIds.contains(assignment.getUser().getId())) {
                continue;
            }
            AssignmentStatus old = assignment.getStatus();
            assignment.setCurrent(false);
            if (old != AssignmentStatus.COMPLETED) {
                assignment.setStatus(AssignmentStatus.REMOVED);
                assignment.setBlockedReason(null);
                assignment.setOnHoldReason(null);
            }
            removed.add(new RemovedAssignment(assignment, old));
        }

        List<TaskAssignment> added = new ArrayList<>();
        for (User user : newUsers) {
            TaskAssignment assignment = new TaskAssignment();
            assignment.setTask(task);
            assignment.setUser(user);
            task.getAssignments().add(assignment);
            added.add(assignment);
        }
        return new AssignmentChanges(added, removed);
    }

    private void recordAssignmentChanges(Task task, AssignmentChanges changes, Long actor) {
        for (TaskAssignment added : changes.added()) {
            User user = added.getUser();
            history.recordStatus(task.getId(), added.getId(), null, AssignmentStatus.ASSIGNED.name(), actor, "Assigned to " + user.getName());
            history.audit(actor, "ASSIGN_TASK", task.getId(), null, mapOf("userId", user.getId(), "user", user.getName()));
            pointsService.sealAssigned(task, added);
            notificationService.notifyUser(user.getId(), task.getId(), "TASK_ASSIGNED", "Task assigned to you",
                "\"" + task.getTitle() + "\" (" + task.getTaskNumber() + ") was assigned to you.");
        }
        for (RemovedAssignment removed : changes.removed()) {
            TaskAssignment assignment = removed.assignment();
            User user = assignment.getUser();
            if (assignment.getStatus() == AssignmentStatus.REMOVED) {
                // Taken off the task via a plain edit, not a formal reassignment - same neutral,
                // excluded-from-scoring outcome (a no-op if this assignment was already FAILED).
                pointsService.sealNeutralClose(task, assignment, PointsEventType.REASSIGNED, "Removed from task via edit");
            }
            if (assignment.getStatus() != removed.oldStatus()) {
                history.recordStatus(task.getId(), assignment.getId(), removed.oldStatus().name(), assignment.getStatus().name(), actor, "Removed " + user.getName() + " from task");
            }
            history.audit(actor, "UNASSIGN_TASK", task.getId(),
                mapOf("userId", user.getId(), "user", user.getName(), "status", removed.oldStatus().name()), null);
        }
    }

    private void recordTaskStatusChange(Task task, TaskStatus oldStatus, Long actor, String comment) {
        if (task.getStatus() == oldStatus) {
            return;
        }
        history.recordStatus(task.getId(), null, oldStatus.name(), task.getStatus().name(), actor, comment);
        String action = task.getStatus() == TaskStatus.COMPLETED ? "COMPLETE_TASK" : "CHANGE_STATUS";
        history.audit(actor, action, task.getId(), mapOf("status", oldStatus.name()), mapOf("status", task.getStatus().name()));
    }

    private void recordFieldChanges(Long taskId, Map<String, Object> before, Map<String, Object> after, Long actor) {
        auditFieldChange(taskId, actor, "CHANGE_DUE_DATE", before, after, "dueDate");
        auditFieldChange(taskId, actor, "CHANGE_PRIORITY", before, after, "priority");
        auditFieldChange(taskId, actor, "UPDATE_TASK", before, after, "title", "description", "category");
    }

    private void auditFieldChange(Long taskId, Long actor, String action, Map<String, Object> before, Map<String, Object> after, String... keys) {
        Map<String, Object> oldValues = new LinkedHashMap<>();
        Map<String, Object> newValues = new LinkedHashMap<>();
        for (String key : keys) {
            if (!Objects.equals(before.get(key), after.get(key))) {
                oldValues.put(key, before.get(key));
                newValues.put(key, after.get(key));
            }
        }
        if (!oldValues.isEmpty()) {
            history.audit(actor, action, taskId, oldValues, newValues);
        }
    }

    private Map<String, Object> snapshot(Task task) {
        return mapOf(
            "title", task.getTitle(),
            "description", task.getDescription(),
            "priority", task.getPriority().name(),
            "category", task.getCategory().getName(),
            "dueDate", task.getDueDate() == null ? null : task.getDueDate().toString(),
            "status", task.getStatus().name());
    }

    /** Like Map.of but keeps insertion order and tolerates null values. */
    private static Map<String, Object> mapOf(Object... keysAndValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            map.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return map;
    }

    private static Sort sortFor(String key, String direction) {
        String property = SORT_FIELDS.get(key == null || key.isBlank() ? "updated" : key);
        if (property == null) {
            throw new BadRequestException("Unknown sort option. Use one of: " + String.join(", ", SORT_FIELDS.keySet()) + ".");
        }
        String dir = direction == null || direction.isBlank() ? "desc" : direction.toLowerCase();
        if (!dir.equals("asc") && !dir.equals("desc")) {
            throw new BadRequestException("Sort direction must be asc or desc.");
        }
        return Sort.by(Sort.Direction.fromString(dir), property);
    }

    /** Only used for a single task (getTask) - the 2 extra queries here are not paid per-row in listTasks. */
    private TaskResponse buildEnrichedAdminResponse(Task task) {
        List<TaskDueDateHistory> changes = dueDateHistoryRepository.findByTaskIdOrderByChangedAtAsc(task.getId());
        LocalDate original = changes.isEmpty()
            ? task.getDueDate()
            : (changes.getFirst().getPreviousDueDate() == null
                ? changes.getFirst().getNewDueDate()
                : changes.getFirst().getPreviousDueDate());
        int extensions = (int) changes.stream()
            .filter(change -> change.getPreviousDueDate() != null && change.getNewDueDate().isAfter(change.getPreviousDueDate()))
            .count();
        return TaskResponse.forAdmin(task, original, extensions,
            Math.toIntExact(reassignmentHistoryRepository.countByTaskId(task.getId())));
    }
}
