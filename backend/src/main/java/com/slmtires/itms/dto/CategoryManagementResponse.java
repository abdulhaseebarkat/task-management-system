package com.slmtires.itms.dto;

/** Admin-only view of a category, including inactive ones (the public /api/task-categories list never does). */
public record CategoryManagementResponse(Long id, String name, boolean active) {
}
