package com.slmtires.itms.controller;

import com.slmtires.itms.dto.AuditLogRowResponse;
import com.slmtires.itms.dto.PagedResponse;
import com.slmtires.itms.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AuditLogController {
    private final AuditLogService auditLogService;

    @GetMapping("/audit-logs")
    @PreAuthorize("hasRole('ADMIN')")
    public PagedResponse<AuditLogRowResponse> list(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(required = false) String action,
        @RequestParam(required = false) Long actorId,
        @RequestParam(required = false) Long taskId,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return auditLogService.listAuditLogs(page, size, action, actorId, taskId, from, to);
    }
}
