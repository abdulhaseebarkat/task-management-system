package com.slmtires.itms.service;

import com.slmtires.itms.dto.TaskCommentRequest;
import com.slmtires.itms.dto.TaskCommentResponse;
import com.slmtires.itms.entity.Task;
import com.slmtires.itms.entity.TaskAssignment;
import com.slmtires.itms.entity.TaskComment;
import com.slmtires.itms.repository.TaskCommentRepository;
import com.slmtires.itms.security.AppUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * A task's comments are one shared thread: every current assignee and the Admin see the same
 * messages, so co-assignees can coordinate. Visibility otherwise follows the task itself - anyone
 * not currently on the task gets the usual "not found".
 */
@Service
@RequiredArgsConstructor
public class TaskCommentService {
    private final TaskCommentRepository commentRepository;
    private final TaskLifecycleSupport lifecycleSupport;
    private final TaskHistoryService historyService;
    private final NotificationService notificationService;
    private final RealtimeEventService realtimeEventService;

    @Transactional(readOnly = true)
    public List<TaskCommentResponse> listComments(Long taskId, AppUserPrincipal principal) {
        Task task = lifecycleSupport.findTask(taskId);
        lifecycleSupport.requireVisible(task, principal);
        return commentRepository.findByTaskIdOrderByCreatedAtAsc(taskId).stream()
            .map(TaskCommentResponse::from)
            .toList();
    }

    @Transactional
    public TaskCommentResponse addComment(Long taskId, TaskCommentRequest request, AppUserPrincipal principal) {
        Task task = lifecycleSupport.findTask(taskId);
        lifecycleSupport.requireVisible(task, principal);

        TaskComment comment = new TaskComment();
        comment.setTaskId(taskId);
        comment.setUser(principal.getUser());
        comment.setComment(request.comment().trim());
        TaskComment saved = commentRepository.save(comment);

        historyService.audit(principal.getId(), "ADD_COMMENT", taskId, null,
            Map.of("comment", comment.getComment()));

        notifyAboutComment(task, principal);
        realtimeEventService.refreshTask(taskId);
        return TaskCommentResponse.from(saved);
    }

    private void notifyAboutComment(Task task, AppUserPrincipal principal) {
        String authorName = principal.getUser().getName();
        String title = "New update on " + task.getTaskNumber();
        String message = authorName + " commented on \"" + task.getTitle() + "\".";

        if (lifecycleSupport.isAdmin(principal)) {
            for (TaskAssignment assignment : lifecycleSupport.currentAssignments(task)) {
                notificationService.notifyUser(assignment.getUser().getId(), task.getId(), "TASK_COMMENT", title, message);
            }
        } else {
            notificationService.notifyAdmins(task.getId(), "TASK_COMMENT", title, message);
        }
    }
}
