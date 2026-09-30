package com.slmtires.itms.service;

import com.slmtires.itms.dto.AuditLogRowResponse;
import com.slmtires.itms.dto.PagedResponse;
import com.slmtires.itms.dto.UserResponse;
import com.slmtires.itms.entity.Task;
import com.slmtires.itms.entity.User;
import com.slmtires.itms.exception.BadRequestException;
import com.slmtires.itms.exception.ResourceNotFoundException;
import com.slmtires.itms.repository.TaskRepository;
import com.slmtires.itms.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuditLogService {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;
    private final TaskRepository taskRepository;

    public PagedResponse<AuditLogRowResponse> listAuditLogs(Integer page, Integer size, String action, Long actorId, Long taskId, LocalDate from, LocalDate to) {
        int offset = page == null || page < 0 ? 0 : page;
        int limit = Math.min(size == null ? 20 : Math.max(1, Math.min(size, 100)), 100);
        StringBuilder sql = new StringBuilder("SELECT a.id, a.user_id, a.entity_type, a.entity_id, a.action, a.old_value, a.new_value, a.created_at FROM audit_logs a WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (action != null && !action.isBlank()) {
            sql.append(" AND a.action = ?");
            params.add(action);
        }
        if (actorId != null) {
            sql.append(" AND a.user_id = ?");
            params.add(actorId);
        }
        if (taskId != null) {
            sql.append(" AND a.entity_type = 'TASK' AND a.entity_id = ?");
            params.add(taskId);
        }
        if (from != null) {
            sql.append(" AND a.created_at >= ?");
            params.add(startOfDayUtc(from));
        }
        if (to != null) {
            sql.append(" AND a.created_at < ?");
            params.add(startOfDayUtc(to.plusDays(1)));
        }
        sql.append(" ORDER BY a.created_at DESC, a.id DESC LIMIT ? OFFSET ?");
        params.add(limit); params.add(offset * limit);

        List<AuditLogRowResponse> rows = jdbcTemplate.query(sql.toString(), mapper(), params.toArray());
        String countSql = "SELECT COUNT(*) FROM audit_logs a WHERE 1=1";
        // same filters
        StringBuilder countWhere = new StringBuilder();
        List<Object> countParams = new ArrayList<>();
        if (action != null && !action.isBlank()) { countWhere.append(" AND a.action = ?"); countParams.add(action); }
        if (actorId != null) { countWhere.append(" AND a.user_id = ?"); countParams.add(actorId); }
        if (taskId != null) { countWhere.append(" AND a.entity_type = 'TASK' AND a.entity_id = ?"); countParams.add(taskId); }
        if (from != null) { countWhere.append(" AND a.created_at >= ?"); countParams.add(startOfDayUtc(from)); }
        if (to != null) { countWhere.append(" AND a.created_at < ?"); countParams.add(startOfDayUtc(to.plusDays(1))); }
        long total = jdbcTemplate.queryForObject(countSql + countWhere, Long.class, countParams.toArray());
        return new PagedResponse<>(rows, offset, limit, total, Math.max(1, (int) Math.ceil((double) total / limit)));
    }

    private RowMapper<AuditLogRowResponse> mapper() {
        return (rs, rowNum) -> {
            Long userId = rs.getObject("user_id", Long.class);
            User actor = userId == null ? null : userRepository.findById(userId).orElse(null);
            String entityType = rs.getString("entity_type");
            Long entityId = rs.getLong("entity_id");
            String taskNumber = entityType != null && entityType.equals("TASK") ? taskRepository.findById(entityId).map(Task::getTaskNumber).orElse(null) : null;
            String action = rs.getString("action");
            Map<String, Object> oldValue = readJson(rs.getString("old_value"));
            Map<String, Object> newValue = readJson(rs.getString("new_value"));
            String summary = summarize(action, oldValue, newValue);
            return new AuditLogRowResponse(rs.getLong("id"), rs.getTimestamp("created_at").toInstant(), actor == null ? null : UserResponse.from(actor), entityType, entityId, taskNumber, action, summary, oldValue, newValue);
        };
    }

    /** The Postgres JDBC driver can't infer a SQL type for a raw java.time.Instant bind parameter - java.sql.Timestamp works. */
    private static java.sql.Timestamp startOfDayUtc(LocalDate date) {
        return java.sql.Timestamp.from(date.atStartOfDay().toInstant(java.time.ZoneOffset.UTC));
    }

    private Map<String, Object> readJson(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception ex) {
            return Map.of("raw", json);
        }
    }

    private String summarize(String action, Map<String, Object> oldValue, Map<String, Object> newValue) {
        if (action == null) return "Task activity";
        return switch (action) {
            case "CREATE_TASK" -> "Task created";
            case "CHANGE_DUE_DATE" -> "Due date moved from " + oldValue.getOrDefault("dueDate", "-") + " to " + newValue.getOrDefault("dueDate", "-");
            case "REASSIGN_TASK" -> "Task reassigned";
            default -> action.replace('_', ' ').toLowerCase();
        };
    }
}
