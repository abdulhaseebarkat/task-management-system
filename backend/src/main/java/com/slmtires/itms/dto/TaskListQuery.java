package com.slmtires.itms.dto;

import com.slmtires.itms.entity.TaskPriority;
import com.slmtires.itms.entity.TaskStatus;

/**
 * Search, filter, sort and page options for GET /api/tasks. assigneeId only applies for Admins.
 * archived=true shows the Archive tab (cancelled tasks only, Admin-only in the UI); the default
 * (false) is the normal list, which never includes a cancelled task regardless of any status filter.
 */
public record TaskListQuery(
    int page,
    int size,
    String sort,
    String direction,
    String search,
    TaskStatus status,
    TaskPriority priority,
    Long categoryId,
    Long assigneeId,
    boolean archived
) {}
