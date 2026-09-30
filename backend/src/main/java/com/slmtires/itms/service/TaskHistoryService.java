package com.slmtires.itms.service;

import com.slmtires.itms.entity.TaskStatusHistory;
import com.slmtires.itms.repository.TaskStatusHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * Writes the append-only trail: task_status_history (who moved which task or
 * assignment between which statuses) and audit_logs (management-level record
 * with old/new values). Both run inside the caller's transaction, so a
 * rolled-back change never leaves a history row behind.
 */
@Service
@RequiredArgsConstructor
public class TaskHistoryService {

    private final TaskStatusHistoryRepository statusHistoryRepository;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public void recordStatus(Long taskId, Long assignmentId, String oldStatus, String newStatus, Long actorId, String comment) {
        TaskStatusHistory row = new TaskStatusHistory();
        row.setTaskId(taskId);
        row.setAssignmentId(assignmentId);
        row.setOldStatus(oldStatus);
        row.setNewStatus(newStatus);
        row.setChangedBy(actorId);
        row.setComment(comment);
        statusHistoryRepository.save(row);
    }

    public void audit(Long actorId, String action, Long taskId, Map<String, Object> oldValue, Map<String, Object> newValue) {
        jdbcTemplate.update(
            "INSERT INTO audit_logs (user_id, entity_type, entity_id, action, old_value, new_value) "
                + "VALUES (?, 'TASK', ?, ?, CAST(? AS jsonb), CAST(? AS jsonb))",
            actorId, taskId, action, toJson(oldValue), toJson(newValue));
    }

    private String toJson(Map<String, Object> value) {
        return value == null ? null : objectMapper.writeValueAsString(value);
    }
}
