package com.slmtires.itms.controller;

import com.slmtires.itms.dto.AnalyticsQuery;
import com.slmtires.itms.dto.DashboardAnalyticsResponse;
import com.slmtires.itms.dto.EmployeeReportResponse;
import com.slmtires.itms.entity.Role;
import com.slmtires.itms.entity.TaskPriority;
import com.slmtires.itms.entity.TaskStatus;
import com.slmtires.itms.exception.ResourceNotFoundException;
import com.slmtires.itms.security.AppUserPrincipal;
import com.slmtires.itms.service.AnalyticsService;
import com.slmtires.itms.service.ReportExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsController {
    private static final MediaType XLSX = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final AnalyticsService analyticsService;
    private final ReportExportService reportExportService;
    private final com.slmtires.itms.service.PointsService pointsService;

    @GetMapping("/dashboard")
    @PreAuthorize("hasRole('ADMIN')")
    public DashboardAnalyticsResponse dashboard(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        @RequestParam(required = false) Long employeeId,
        @RequestParam(required = false) TaskStatus status,
        @RequestParam(required = false) TaskPriority priority,
        @RequestParam(required = false) Long categoryId
    ) {
        return analyticsService.getDashboard(new AnalyticsQuery(from, to, employeeId, status, priority, categoryId));
    }

    /**
     * Admin: any employee's report (spec: VIEW_ALL_REPORTS). A team member: only their own
     * (VIEW_OWN_REPORT) - someone else's report looks exactly like it doesn't exist, matching
     * every other visibility rule in this app (404, never 403). Points (Phase 9) are included for
     * both - a team member sees their own points, never anyone else's; there is no department-wide
     * comparison available to them (the dashboard/leaderboard endpoints stay Admin-only).
     */
    @GetMapping("/employee/{id}")
    public EmployeeReportResponse employeeReport(
        @PathVariable Long id,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        @RequestParam(required = false) TaskStatus status,
        @RequestParam(required = false) TaskPriority priority,
        @RequestParam(required = false) Long categoryId,
        @AuthenticationPrincipal AppUserPrincipal principal
    ) {
        requireVisible(id, principal);
        return analyticsService.getEmployeeReport(id, new AnalyticsQuery(from, to, null, status, priority, categoryId));
    }

    @GetMapping("/dashboard/export.xlsx")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<byte[]> departmentExcel(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        @RequestParam(required = false) Long employeeId,
        @RequestParam(required = false) TaskStatus status,
        @RequestParam(required = false) TaskPriority priority,
        @RequestParam(required = false) Long categoryId
    ) {
        var report = analyticsService.getDashboard(new AnalyticsQuery(from, to, employeeId, status, priority, categoryId));
        return fileResponse(reportExportService.departmentExcel(report), XLSX, "department-report-" + report.from() + "-to-" + report.to() + ".xlsx");
    }

    @GetMapping("/dashboard/export.pdf")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<byte[]> departmentPdf(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        @RequestParam(required = false) Long employeeId,
        @RequestParam(required = false) TaskStatus status,
        @RequestParam(required = false) TaskPriority priority,
        @RequestParam(required = false) Long categoryId
    ) {
        var report = analyticsService.getDashboard(new AnalyticsQuery(from, to, employeeId, status, priority, categoryId));
        return fileResponse(reportExportService.departmentPdf(report), MediaType.APPLICATION_PDF, "department-report-" + report.from() + "-to-" + report.to() + ".pdf");
    }

    @GetMapping("/employee/{id}/export.xlsx")
    public ResponseEntity<byte[]> employeeExcel(
        @PathVariable Long id,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        @RequestParam(required = false) TaskStatus status,
        @RequestParam(required = false) TaskPriority priority,
        @RequestParam(required = false) Long categoryId,
        @AuthenticationPrincipal AppUserPrincipal principal
    ) {
        requireVisible(id, principal);
        var report = analyticsService.getEmployeeReport(id, new AnalyticsQuery(from, to, null, status, priority, categoryId));
        return fileResponse(reportExportService.employeeExcel(report), XLSX, "employee-report-" + slug(report.employeeName()) + "-" + report.from() + "-to-" + report.to() + ".xlsx");
    }

    @GetMapping("/employee/{id}/export.pdf")
    public ResponseEntity<byte[]> employeePdf(
        @PathVariable Long id,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        @RequestParam(required = false) TaskStatus status,
        @RequestParam(required = false) TaskPriority priority,
        @RequestParam(required = false) Long categoryId,
        @AuthenticationPrincipal AppUserPrincipal principal
    ) {
        requireVisible(id, principal);
        var report = analyticsService.getEmployeeReport(id, new AnalyticsQuery(from, to, null, status, priority, categoryId));
        return fileResponse(reportExportService.employeePdf(report), MediaType.APPLICATION_PDF, "employee-report-" + slug(report.employeeName()) + "-" + report.from() + "-to-" + report.to() + ".pdf");
    }

    /**
     * One assignment's points ledger for a task. Admin: any assignment. Team member: only an
     * assignment that's theirs - anyone else's looks like it doesn't exist (404, never 403), same
     * visibility rule as the individual report. Enforced in the service, not just here, since it
     * has to load the assignment to know who owns it.
     */
    @GetMapping("/tasks/{taskId}/points/{assignmentId}")
    public com.slmtires.itms.dto.TaskPointsResponse taskPoints(@PathVariable Long taskId, @PathVariable Long assignmentId, @AuthenticationPrincipal AppUserPrincipal principal) {
        return analyticsService.getTaskPoints(taskId, assignmentId, principal);
    }

    /**
     * One-time, idempotent catch-up for tasks/assignments that existed before the points system
     * shipped (see PointsService#backfillMissingBaselines) - never backdated, never touches
     * anything already tracked. Safe to call more than once; a no-op the second time.
     */
    @PostMapping("/points/backfill")
    @PreAuthorize("hasRole('ADMIN')")
    public java.util.Map<String, Integer> backfillPoints() {
        return java.util.Map.of("assignmentsSeeded", pointsService.backfillMissingBaselines());
    }

    private void requireVisible(Long employeeId, AppUserPrincipal principal) {
        boolean admin = principal.getUser().getRole() == Role.ADMIN;
        if (!admin && !principal.getId().equals(employeeId)) {
            throw new ResourceNotFoundException("Employee not found.");
        }
    }

    private ResponseEntity<byte[]> fileResponse(byte[] content, MediaType type, String filename) {
        return ResponseEntity.ok()
            .contentType(type)
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
            .body(content);
    }

    private String slug(String name) {
        return name.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }
}
