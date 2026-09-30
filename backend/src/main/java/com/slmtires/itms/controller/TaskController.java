package com.slmtires.itms.controller;

import com.slmtires.itms.dto.*;
import com.slmtires.itms.entity.TaskPriority;
import com.slmtires.itms.entity.TaskStatus;
import com.slmtires.itms.security.AppUserPrincipal;
import com.slmtires.itms.service.TaskCommentService;
import com.slmtires.itms.service.TaskDueDateService;
import com.slmtires.itms.service.TaskActivityService;
import com.slmtires.itms.service.TaskReassignmentService;
import com.slmtires.itms.service.TaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {
    private final TaskService taskService;
    private final TaskDueDateService taskDueDateService;
    private final TaskReassignmentService taskReassignmentService;
    private final TaskActivityService taskActivityService;
    private final TaskCommentService taskCommentService;

    @GetMapping
    public PagedResponse<TaskResponse> list(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "updated") String sort,
        @RequestParam(defaultValue = "desc") String direction,
        @RequestParam(required = false) String search,
        @RequestParam(required = false) TaskStatus status,
        @RequestParam(required = false) TaskPriority priority,
        @RequestParam(required = false) Long categoryId,
        @RequestParam(required = false) Long assigneeId,
        @RequestParam(defaultValue = "false") boolean archived,
        @AuthenticationPrincipal AppUserPrincipal principal
    ) {
        return taskService.listTasks(new TaskListQuery(page, size, sort, direction, search, status, priority, categoryId, assigneeId, archived), principal);
    }

    @GetMapping("/categories")
    public List<CategoryResponse> categories() {
        return taskService.listCategories();
    }

    @GetMapping("/{id}")
    public TaskResponse get(@PathVariable Long id, @AuthenticationPrincipal AppUserPrincipal principal) {
        return taskService.getTask(id, principal);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public TaskResponse create(@Valid @RequestBody TaskRequest request, @AuthenticationPrincipal AppUserPrincipal principal) {
        return taskService.createTask(request, principal);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public TaskResponse update(
        @PathVariable Long id,
        @Valid @RequestBody TaskRequest request,
        @AuthenticationPrincipal AppUserPrincipal principal
    ) {
        return taskService.updateTask(id, request, principal);
    }

    @PutMapping("/{id}/assignment")
    @PreAuthorize("hasRole('TEAM_MEMBER')")
    public TaskResponse updateAssignment(
        @PathVariable Long id,
        @Valid @RequestBody UpdateAssignmentRequest request,
        @AuthenticationPrincipal AppUserPrincipal principal
    ) {
        return taskService.updateAssignment(id, request, principal);
    }

    @PutMapping("/{id}/assignments/{assignmentId}")
    @PreAuthorize("hasRole('ADMIN')")
    public TaskResponse adjustAssignment(
        @PathVariable Long id,
        @PathVariable Long assignmentId,
        @Valid @RequestBody UpdateAssignmentRequest request,
        @AuthenticationPrincipal AppUserPrincipal principal
    ) {
        return taskService.adjustAssignment(id, assignmentId, request, principal);
    }

    @PostMapping("/{id}/assignments/{assignmentId}/fail")
    @PreAuthorize("hasRole('ADMIN')")
    public TaskResponse failAssignment(
        @PathVariable Long id,
        @PathVariable Long assignmentId,
        @Valid @RequestBody FailAssignmentRequest request,
        @AuthenticationPrincipal AppUserPrincipal principal
    ) {
        return taskService.failAssignment(id, assignmentId, request.reason(), principal);
    }

    @PutMapping("/{id}/due-date")
    @PreAuthorize("hasRole('ADMIN')")
    public TaskResponse changeDueDate(
        @PathVariable Long id,
        @Valid @RequestBody DueDateChangeRequest request,
        @AuthenticationPrincipal AppUserPrincipal principal
    ) {
        return taskDueDateService.changeDueDate(id, request, principal);
    }

    @GetMapping("/{id}/due-date-history")
    public DueDateHistoryView getDueDateHistory(@PathVariable Long id, @AuthenticationPrincipal AppUserPrincipal principal) {
        return taskDueDateService.getDueDateHistory(id, principal);
    }

    @PostMapping("/{id}/reassign")
    @PreAuthorize("hasRole('ADMIN')")
    public TaskResponse reassign(
        @PathVariable Long id,
        @Valid @RequestBody ReassignTaskRequest request,
        @AuthenticationPrincipal AppUserPrincipal principal
    ) {
        return taskReassignmentService.reassignTask(id, request, principal);
    }

    @GetMapping("/{id}/reassignment-history")
    @PreAuthorize("hasRole('ADMIN')")
    public List<ReassignmentHistoryResponse> getReassignmentHistory(@PathVariable Long id) {
        return taskReassignmentService.getReassignmentHistory(id);
    }

    @GetMapping("/{id}/activity")
    public List<TaskActivityResponse> activity(@PathVariable Long id, @AuthenticationPrincipal AppUserPrincipal principal) {
        return taskActivityService.getActivity(id, principal);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void cancel(@PathVariable Long id, @AuthenticationPrincipal AppUserPrincipal principal) {
        taskService.cancelTask(id, principal);
    }

    @PostMapping("/{id}/reopen")
    @PreAuthorize("hasRole('ADMIN')")
    public TaskResponse reopen(
        @PathVariable Long id,
        @RequestBody(required = false) ReopenRequest request,
        @AuthenticationPrincipal AppUserPrincipal principal
    ) {
        boolean keepProgress = request != null && request.keep();
        return taskService.reopenTask(id, keepProgress, principal);
    }

    @PostMapping("/{id}/reinstate")
    @PreAuthorize("hasRole('ADMIN')")
    public TaskResponse reinstate(@PathVariable Long id, @AuthenticationPrincipal AppUserPrincipal principal) {
        return taskService.reinstateTask(id, principal);
    }

    @GetMapping("/{id}/comments")
    public List<TaskCommentResponse> listComments(@PathVariable Long id, @AuthenticationPrincipal AppUserPrincipal principal) {
        return taskCommentService.listComments(id, principal);
    }

    @PostMapping("/{id}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public TaskCommentResponse addComment(
        @PathVariable Long id,
        @Valid @RequestBody TaskCommentRequest request,
        @AuthenticationPrincipal AppUserPrincipal principal
    ) {
        return taskCommentService.addComment(id, request, principal);
    }
}
