package com.slmtires.itms.service;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.slmtires.itms.dto.DashboardAnalyticsResponse;
import com.slmtires.itms.dto.DashboardAnalyticsResponse.CategoryCount;
import com.slmtires.itms.dto.DashboardAnalyticsResponse.EmployeeCount;
import com.slmtires.itms.dto.DashboardAnalyticsResponse.MonthlyRate;
import com.slmtires.itms.dto.DashboardAnalyticsResponse.PointsLeaderboardEntry;
import com.slmtires.itms.dto.DashboardAnalyticsResponse.PriorityCount;
import com.slmtires.itms.dto.DashboardAnalyticsResponse.StatusSlice;
import com.slmtires.itms.dto.DashboardAnalyticsResponse.StrikeDistributionSlice;
import com.slmtires.itms.dto.DashboardAnalyticsResponse.WeekCount;
import com.slmtires.itms.dto.EmployeeReportResponse;
import com.slmtires.itms.dto.EmployeeReportResponse.ActivityItem;
import com.slmtires.itms.dto.EmployeeReportResponse.DueDateEntry;
import com.slmtires.itms.dto.EmployeeReportResponse.MonthCount;
import com.slmtires.itms.dto.EmployeeReportResponse.PointsBreakdownRow;
import com.slmtires.itms.dto.EmployeeReportResponse.ReassignedItem;
import com.slmtires.itms.dto.EmployeeReportResponse.TaskDetailRow;
import com.slmtires.itms.dto.EmployeeReportResponse.WorkloadItem;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.ClientAnchor;
import org.apache.poi.ss.usermodel.Drawing;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Phase 8 export, extended in Phase 10 with real rendered charts (via {@link ChartImageRenderer})
 * and a full per-task points ledger ("Individual Tasks") - the Admin asked for exports that visually
 * match the on-screen reports, not just data tables, so this deliberately departs from the original
 * spec's "data-only, no charts" instruction where that later, more specific request overrides it.
 */
@Service
public class ReportExportService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    // ---------------------------------------------------------------- Department: Excel

    public byte[] departmentExcel(DashboardAnalyticsResponse report) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle header = headerStyle(workbook);

            Sheet summary = workbook.createSheet("Summary");
            writeRow(summary, 0, header, "Metric", "Value");
            var s = report.summary();
            int r = 1;
            r = writeRow(summary, r, null, "Date range", report.from() + " to " + report.to());
            r = writeRow(summary, r, null, "Total tasks", s.totalTasks());
            r = writeRow(summary, r, null, "Active", s.active());
            r = writeRow(summary, r, null, "Completed", s.completed());
            r = writeRow(summary, r, null, "Overdue", s.overdue());
            r = writeRow(summary, r, null, "Blocked", s.blocked());
            r = writeRow(summary, r, null, "On hold", s.onHold());
            r = writeRow(summary, r, null, "Reassigned", s.reassigned());
            writeRow(summary, r, null, "Completion rate", s.completionRate() + "%");
            autosize(summary, 2);

            Sheet status = workbook.createSheet("Status distribution");
            writeRow(status, 0, header, "Status", "Count", "Percent");
            int i = 1;
            for (StatusSlice slice : report.statusDistribution()) {
                i = writeRow(status, i, null, slice.status(), slice.count(), slice.percent() + "%");
            }
            autosize(status, 3);

            Sheet priority = workbook.createSheet("Priority distribution");
            writeRow(priority, 0, header, "Priority", "Count");
            i = 1;
            for (PriorityCount p : report.priorityDistribution()) {
                i = writeRow(priority, i, null, p.priority(), p.count());
            }
            autosize(priority, 2);

            Sheet category = workbook.createSheet("Category distribution");
            writeRow(category, 0, header, "Category", "Count");
            i = 1;
            for (CategoryCount c : report.categoryDistribution()) {
                i = writeRow(category, i, null, c.categoryName(), c.count());
            }
            autosize(category, 2);

            Sheet workload = workbook.createSheet("Team workload");
            writeRow(workload, 0, header, "Employee", "Active tasks");
            i = 1;
            for (EmployeeCount e : report.teamWorkload()) {
                i = writeRow(workload, i, null, e.employeeName(), e.count());
            }
            autosize(workload, 2);

            Sheet completion = workbook.createSheet("Employee completion");
            writeRow(completion, 0, header, "Employee", "Completed tasks");
            i = 1;
            for (EmployeeCount e : report.employeeCompletion()) {
                i = writeRow(completion, i, null, e.employeeName(), e.count());
            }
            autosize(completion, 2);

            Sheet assignedVsCompleted = workbook.createSheet("Assigned vs completed");
            writeRow(assignedVsCompleted, 0, header, "Employee", "Tasks assigned", "Tasks completed by them");
            i = 1;
            Map<Long, Long> completedByEmployee = report.employeeCompletion().stream()
                .collect(Collectors.toMap(EmployeeCount::employeeId, EmployeeCount::count));
            for (EmployeeCount e : report.employeeAssigned()) {
                i = writeRow(assignedVsCompleted, i, null, e.employeeName(), e.count(), completedByEmployee.getOrDefault(e.employeeId(), 0L));
            }
            autosize(assignedVsCompleted, 3);

            Sheet breakdown = workbook.createSheet("Status breakdown");
            writeRow(breakdown, 0, header, "Status", "Count");
            i = 1;
            for (StatusSlice slice : report.statusDistribution()) {
                i = writeRow(breakdown, i, null, slice.status(), slice.count());
            }
            i = writeRow(breakdown, i, null, "OVERDUE (live)", report.summary().overdue());
            autosize(breakdown, 2);

            Sheet trend = workbook.createSheet("Completion trend");
            writeRow(trend, 0, header, "Week starting", "Completions");
            i = 1;
            for (WeekCount w : report.completionTrend()) {
                i = writeRow(trend, i, null, w.weekStart().format(DATE), w.count());
            }
            autosize(trend, 2);

            Sheet overdueTrend = workbook.createSheet("Overdue trend");
            writeRow(overdueTrend, 0, header, "Due-date week", "Overdue count");
            i = 1;
            for (WeekCount w : report.overdueTrend()) {
                i = writeRow(overdueTrend, i, null, w.weekStart().format(DATE), w.count());
            }
            autosize(overdueTrend, 2);

            Sheet leaderboard = workbook.createSheet("Points leaderboard");
            writeRow(leaderboard, 0, header, "Employee", "Points earned", "Points possible", "Points lost", "Efficiency");
            i = 1;
            for (PointsLeaderboardEntry entry : report.pointsLeaderboard()) {
                i = writeRow(leaderboard, i, null, entry.employeeName(), entry.pointsEarned(), entry.pointsPossible(), entry.pointsLost(), entry.efficiencyRate() + "%");
            }
            autosize(leaderboard, 5);

            Sheet strikes = workbook.createSheet("Strike distribution");
            writeRow(strikes, 0, header, "Level", "Count", "Percent");
            i = 1;
            for (StrikeDistributionSlice slice : report.strikeDistribution()) {
                i = writeRow(strikes, i, null, strikeLevelLabel(slice.level()), slice.count(), slice.percent() + "%");
            }
            autosize(strikes, 3);

            Sheet efficiencyTrend = workbook.createSheet("Team efficiency trend");
            writeRow(efficiencyTrend, 0, header, "Month", "Efficiency rate");
            i = 1;
            for (MonthlyRate m : report.teamEfficiencyTrend()) {
                i = writeRow(efficiencyTrend, i, null, m.monthLabel(), m.rate() + "%");
            }
            autosize(efficiencyTrend, 2);

            addChartsSheet(workbook, "Charts", List.of(
                Map.entry("Tasks Assigned vs. Completed, by Person", assignedVsCompletedChart(report.employeeAssigned(), report.employeeCompletion())),
                Map.entry("Strike Distribution", strikeDonutChart(report.strikeDistribution())),
                Map.entry("Team Efficiency Trend", ChartImageRenderer.monthlyBars(
                    report.teamEfficiencyTrend().stream().map(MonthlyRate::monthLabel).toList(),
                    report.teamEfficiencyTrend().stream().map(MonthlyRate::rate).toList()))
            ));

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // ---------------------------------------------------------------- Department: PDF

    /** Deliberately just the charts - no data tables. Per the Admin's explicit instruction, the PDF carries only the same graphs the on-screen report shows, nothing else. */
    public byte[] departmentPdf(DashboardAnalyticsResponse report) {
        return withPdfDocument(document -> {
            document.add(title("Department Report"));
            document.add(subtitle("IT department task activity, " + report.from() + " to " + report.to()));

            document.add(sectionHeading("Tasks Assigned vs. Completed, by Person"));
            document.add(chartImage(assignedVsCompletedChart(report.employeeAssigned(), report.employeeCompletion()), 500f));

            document.add(sectionHeading("Team Efficiency Trend"));
            document.add(chartImage(ChartImageRenderer.monthlyBars(
                report.teamEfficiencyTrend().stream().map(MonthlyRate::monthLabel).toList(),
                report.teamEfficiencyTrend().stream().map(MonthlyRate::rate).toList()), 400f));

            document.add(sectionHeading("Strike Distribution"));
            document.add(chartImage(strikeDonutChart(report.strikeDistribution()), 320f));
        });
    }

    // ---------------------------------------------------------------- Individual: Excel

    public byte[] employeeExcel(EmployeeReportResponse report) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle header = headerStyle(workbook);

            Sheet summary = workbook.createSheet("Summary");
            writeRow(summary, 0, header, "Metric", "Value");
            var s = report.summary();
            int r = 1;
            r = writeRow(summary, r, null, "Employee", report.employeeName());
            r = writeRow(summary, r, null, "Date range", report.from() + " to " + report.to());
            r = writeRow(summary, r, null, "Assigned", s.assigned());
            r = writeRow(summary, r, null, "Completed", s.completed());
            r = writeRow(summary, r, null, "Active", s.active());
            r = writeRow(summary, r, null, "Overdue", s.overdue());
            r = writeRow(summary, r, null, "Blocked", s.blocked());
            r = writeRow(summary, r, null, "On hold", s.onHold());
            r = writeRow(summary, r, null, "Cancelled", s.cancelled());
            r = writeRow(summary, r, null, "Reassigned away", s.reassignedAway());
            r = writeRow(summary, r, null, "Completion rate", s.completionRate() + "%");
            writeRow(summary, r, null, "Average completion time (days)", s.avgCompletionDays() == null ? "-" : s.avgCompletionDays());
            autosize(summary, 2);

            Sheet status = workbook.createSheet("Status distribution");
            writeRow(status, 0, header, "Status", "Count", "Percent");
            int i = 1;
            for (StatusSlice slice : report.statusDistribution()) {
                i = writeRow(status, i, null, slice.status(), slice.count(), slice.percent() + "%");
            }
            autosize(status, 3);

            Sheet priority = workbook.createSheet("Priority distribution");
            writeRow(priority, 0, header, "Priority", "Count");
            i = 1;
            for (PriorityCount p : report.priorityDistribution()) {
                i = writeRow(priority, i, null, p.priority(), p.count());
            }
            autosize(priority, 2);

            Sheet category = workbook.createSheet("Category distribution");
            writeRow(category, 0, header, "Category", "Count");
            i = 1;
            for (CategoryCount c : report.categoryDistribution()) {
                i = writeRow(category, i, null, c.categoryName(), c.count());
            }
            autosize(category, 2);

            Sheet trend = workbook.createSheet("Monthly completion trend");
            writeRow(trend, 0, header, "Month", "Completions");
            i = 1;
            for (MonthCount m : report.completionTrend()) {
                i = writeRow(trend, i, null, m.monthLabel(), m.count());
            }
            autosize(trend, 2);

            Sheet assignedVsCompleted = workbook.createSheet("Assigned vs completed");
            writeRow(assignedVsCompleted, 0, header, "Period", "Tasks assigned", "Tasks completed");
            writeRow(assignedVsCompleted, 1, null, "This period", report.summary().assigned(), report.summary().completed());
            autosize(assignedVsCompleted, 3);

            Sheet statusBreakdown = workbook.createSheet("Status breakdown");
            writeRow(statusBreakdown, 0, header, "Status", "Count");
            i = 1;
            for (StatusSlice slice : report.statusDistribution()) {
                i = writeRow(statusBreakdown, i, null, slice.status(), slice.count());
            }
            i = writeRow(statusBreakdown, i, null, "OVERDUE (live)", report.summary().overdue());
            autosize(statusBreakdown, 2);

            Sheet workload = workbook.createSheet("Current workload");
            writeRow(workload, 0, header, "Task", "Priority", "Status", "Progress", "Due date");
            i = 1;
            for (WorkloadItem w : report.currentWorkload()) {
                i = writeRow(workload, i, null, w.taskNumber() + " - " + w.title(), w.priority(), w.status(), w.progress() + "%", w.dueDate() == null ? "-" : w.dueDate().format(DATE));
            }
            autosize(workload, 5);

            Sheet reassigned = workbook.createSheet("Reassigned away");
            writeRow(reassigned, 0, header, "Task", "Reassigned at", "To", "Reason");
            i = 1;
            for (ReassignedItem item : report.reassignedTasks()) {
                i = writeRow(reassigned, i, null, item.taskNumber() + " - " + item.title(), item.reassignedAt().toString(), item.toUserName(), item.reason());
            }
            autosize(reassigned, 4);

            Sheet activity = workbook.createSheet("Recent activity");
            writeRow(activity, 0, header, "When", "Task", "Change", "Comment");
            i = 1;
            for (ActivityItem item : report.recentActivity()) {
                String change = (item.oldStatus() == null ? "-" : item.oldStatus()) + " -> " + item.newStatus();
                i = writeRow(activity, i, null, item.occurredAt().toString(), item.taskNumber() + " - " + item.title(), change, item.comment() == null ? "" : item.comment());
            }
            autosize(activity, 4);

            // pointsSummary is only ever null defensively - the endpoint 404s before this report is
            // ever built for anyone who isn't the Admin or this employee themself, so points are
            // always visible to whoever legitimately reaches this export.
            if (report.pointsSummary() != null) {
                var p = report.pointsSummary();
                Sheet points = workbook.createSheet("Points summary");
                writeRow(points, 0, header, "Metric", "Value");
                int pr = 1;
                pr = writeRow(points, pr, null, "Points earned", p.pointsEarned());
                pr = writeRow(points, pr, null, "Points possible", p.pointsPossible());
                pr = writeRow(points, pr, null, "Points lost", p.pointsLost());
                pr = writeRow(points, pr, null, "Efficiency", p.efficiencyRate() + "%");
                writeRow(points, pr, null, "Tasks failed", p.tasksFailed());
                autosize(points, 2);

                Sheet breakdown = workbook.createSheet("Points breakdown");
                writeRow(breakdown, 0, header, "Outcome", "Count", "Percent");
                i = 1;
                for (PointsBreakdownRow row : report.pointsBreakdown()) {
                    i = writeRow(breakdown, i, null, breakdownLabel(row.level()), row.count(), row.percent() + "%");
                }
                autosize(breakdown, 3);

                Sheet monthlyTrend = workbook.createSheet("Monthly points trend");
                writeRow(monthlyTrend, 0, header, "Month", "Efficiency rate");
                i = 1;
                for (MonthlyRate m : report.monthlyPointsTrend()) {
                    i = writeRow(monthlyTrend, i, null, m.monthLabel(), m.rate() + "%");
                }
                autosize(monthlyTrend, 2);

                Sheet taskDetails = workbook.createSheet("Individual tasks");
                writeRow(taskDetails, 0, header, "Task", "Priority", "Assigned date", "Status", "Due dates", "Possible points", "Deducted points");
                i = 1;
                for (TaskDetailRow row : report.taskDetails()) {
                    i = writeRow(taskDetails, i, null, row.taskNumber() + " - " + row.title(), row.priority(),
                        row.assignedAt().toString().substring(0, 10), row.status(), formatDueDates(row.dueDates()),
                        row.possiblePoints(), row.deductedPoints());
                }
                autosize(taskDetails, 7);

                addChartsSheet(workbook, "Charts", List.of(
                    Map.entry("Tasks Assigned vs. Completed", assignedVsCompletedChart(
                        List.of(new EmployeeCount(report.employeeId(), report.employeeName(), report.summary().assigned())),
                        List.of(new EmployeeCount(report.employeeId(), report.employeeName(), report.summary().completed())))),
                    Map.entry("Points Retained vs Lost", ChartImageRenderer.divergingBars(p.pointsEarned(), p.pointsLost())),
                    Map.entry("Monthly Points Trend", ChartImageRenderer.monthlyBars(
                        report.monthlyPointsTrend().stream().map(MonthlyRate::monthLabel).toList(),
                        report.monthlyPointsTrend().stream().map(MonthlyRate::rate).toList()))
                ));
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // ---------------------------------------------------------------- Individual: PDF

    /**
     * Deliberately just the charts plus the per-task ledger - no other data tables. Per the Admin's
     * explicit instruction, the PDF carries only the same graphs the on-screen report shows, plus
     * the "Individual Tasks" detail table, and nothing else.
     */
    public byte[] employeePdf(EmployeeReportResponse report) {
        return withPdfDocument(document -> {
            document.add(title("Individual Report - " + report.employeeName()));
            document.add(subtitle(report.from() + " to " + report.to()));

            var s = report.summary();
            document.add(sectionHeading("Tasks Assigned vs. Completed"));
            document.add(chartImage(assignedVsCompletedChart(
                List.of(new EmployeeCount(report.employeeId(), report.employeeName(), s.assigned())),
                List.of(new EmployeeCount(report.employeeId(), report.employeeName(), s.completed()))), 300f));

            // pointsSummary is only ever null defensively - the endpoint 404s before this report is
            // ever built for anyone who isn't the Admin or this employee themself, so points are
            // always visible to whoever legitimately reaches this export.
            if (report.pointsSummary() != null) {
                var p = report.pointsSummary();
                document.add(sectionHeading("Points Retained vs Lost"));
                document.add(chartImage(ChartImageRenderer.divergingBars(p.pointsEarned(), p.pointsLost()), 240f));

                document.add(sectionHeading("Monthly Points Trend"));
                document.add(chartImage(ChartImageRenderer.monthlyBars(
                    report.monthlyPointsTrend().stream().map(MonthlyRate::monthLabel).toList(),
                    report.monthlyPointsTrend().stream().map(MonthlyRate::rate).toList()), 400f));

                document.add(sectionHeading("Individual Tasks"));
                document.add(dataTable(
                    List.of("Task", "Priority", "Assigned date", "Status", "Due dates", "Possible", "Deducted"),
                    report.taskDetails(),
                    row -> List.of(row.taskNumber() + " - " + row.title(), row.priority(),
                        row.assignedAt().toString().substring(0, 10), row.status(), formatDueDates(row.dueDates()),
                        row.possiblePoints().toString(), row.deductedPoints().signum() > 0 ? row.deductedPoints().toString() : "-")));
            }
        });
    }

    // ---------------------------------------------------------------- Excel helpers

    private CellStyle headerStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        org.apache.poi.ss.usermodel.Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private int writeRow(Sheet sheet, int rowIndex, CellStyle headerStyle, Object... values) {
        Row row = sheet.createRow(rowIndex);
        for (int c = 0; c < values.length; c++) {
            Cell cell = row.createCell(c);
            Object value = values[c];
            if (value instanceof Number n) {
                cell.setCellValue(n.doubleValue());
            } else {
                cell.setCellValue(value == null ? "" : value.toString());
            }
            if (headerStyle != null) cell.setCellStyle(headerStyle);
        }
        return rowIndex + 1;
    }

    private String strikeLevelLabel(String level) {
        return switch (level) {
            case "0" -> "Full points";
            case "1" -> "After 1st strike";
            case "2" -> "After 2nd strike";
            default -> "Failed";
        };
    }

    private String breakdownLabel(String level) {
        return switch (level) {
            case "FULL" -> "Full points";
            case "STRIKE_1" -> "After 1st strike";
            case "STRIKE_2" -> "After 2nd strike";
            default -> "Failed";
        };
    }

    private void autosize(Sheet sheet, int columns) {
        for (int c = 0; c < columns; c++) {
            sheet.autoSizeColumn(c);
        }
    }

    /** One sheet, every chart stacked vertically under its own bold title - simpler and more robust than anchoring pictures into the middle of a data sheet. */
    private void addChartsSheet(Workbook workbook, String sheetName, List<Map.Entry<String, byte[]>> charts) {
        Sheet sheet = workbook.createSheet(sheetName);
        Drawing<?> drawing = sheet.createDrawingPatriarch();
        CellStyle header = headerStyle(workbook);
        int row = 0;
        for (Map.Entry<String, byte[]> chart : charts) {
            Row titleRow = sheet.createRow(row);
            Cell cell = titleRow.createCell(0);
            cell.setCellValue(chart.getKey());
            cell.setCellStyle(header);
            row += 1;

            int pictureIdx = workbook.addPicture(chart.getValue(), Workbook.PICTURE_TYPE_PNG);
            ClientAnchor anchor = drawing.createAnchor(0, 0, 0, 0, 0, row, 9, row + 18);
            drawing.createPicture(anchor, pictureIdx);
            row += 20;
        }
    }

    // ---------------------------------------------------------------- Chart data helpers (shared by PDF and Excel)

    private byte[] assignedVsCompletedChart(List<EmployeeCount> assigned, List<EmployeeCount> completed) {
        Map<Long, Long> completedByEmployee = completed.stream().collect(Collectors.toMap(EmployeeCount::employeeId, EmployeeCount::count));
        List<String> labels = assigned.stream().map(EmployeeCount::employeeName).toList();
        List<Integer> assignedValues = assigned.stream().map(e -> (int) e.count()).toList();
        List<Integer> completedValues = assigned.stream().map(e -> completedByEmployee.getOrDefault(e.employeeId(), 0L).intValue()).toList();
        return ChartImageRenderer.groupedBars(labels,
            new ChartImageRenderer.Series("Tasks Assigned", assignedValues, ChartImageRenderer.BLUE),
            new ChartImageRenderer.Series("Tasks Completed By Them", completedValues, ChartImageRenderer.RED));
    }

    private byte[] strikeDonutChart(List<StrikeDistributionSlice> slices) {
        List<ChartImageRenderer.Slice> mapped = slices.stream()
            .map(s -> new ChartImageRenderer.Slice(strikeLevelLabel(s.level()), s.count(), strikeLevelColor(s.level())))
            .toList();
        return ChartImageRenderer.donut(mapped);
    }

    private java.awt.Color strikeLevelColor(String level) {
        return switch (level) {
            case "0" -> ChartImageRenderer.GREEN;
            case "1" -> ChartImageRenderer.AMBER;
            case "2" -> ChartImageRenderer.ORANGE;
            default -> ChartImageRenderer.RED;
        };
    }

    /** "1st due date -> extension -> extension (waived)" - the same due-date story the on-screen History panel tells, condensed to one cell. */
    private String formatDueDates(List<DueDateEntry> dueDates) {
        if (dueDates.isEmpty()) return "-";
        StringBuilder sb = new StringBuilder(dueDates.get(0).dueDate().toString());
        for (int i = 1; i < dueDates.size(); i++) {
            DueDateEntry entry = dueDates.get(i);
            sb.append(" -> ").append(entry.dueDate());
            if (!entry.countedTowardStrikes()) sb.append(" (waived)");
        }
        return sb.toString();
    }

    // ---------------------------------------------------------------- PDF helpers

    private static final Font TITLE_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
    private static final Font SUBTITLE_FONT = FontFactory.getFont(FontFactory.HELVETICA, 11, Font.ITALIC);
    private static final Font HEADING_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13);
    private static final Font TABLE_HEADER_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);
    private static final Font TABLE_CELL_FONT = FontFactory.getFont(FontFactory.HELVETICA, 9);

    private byte[] withPdfDocument(java.util.function.Consumer<Document> body) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 40, 40, 50, 40);
            PdfWriter.getInstance(document, out);
            document.open();
            body.accept(document);
            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate PDF report.", e);
        }
    }

    private Paragraph title(String text) {
        Paragraph p = new Paragraph(text, TITLE_FONT);
        p.setSpacingAfter(4);
        return p;
    }

    private Paragraph subtitle(String text) {
        Paragraph p = new Paragraph(text, SUBTITLE_FONT);
        p.setSpacingAfter(16);
        return p;
    }

    private Paragraph sectionHeading(String text) {
        Paragraph p = new Paragraph(text, HEADING_FONT);
        p.setSpacingBefore(14);
        p.setSpacingAfter(6);
        return p;
    }

    private <T> PdfPTable dataTable(List<String> columns, List<T> rows, Function<T, List<String>> toRow) {
        PdfPTable table = new PdfPTable(columns.size());
        table.setWidthPercentage(100);
        for (String column : columns) {
            table.addCell(headerCell(column));
        }
        if (rows.isEmpty()) {
            PdfPCell empty = bodyCell("None");
            empty.setColspan(columns.size());
            table.addCell(empty);
        } else {
            for (T row : rows) {
                for (String value : toRow.apply(row)) {
                    table.addCell(bodyCell(value));
                }
            }
        }
        return table;
    }

    private PdfPCell headerCell(String text) {
        PdfPCell cell = new PdfPCell(new Paragraph(text, TABLE_HEADER_FONT));
        cell.setGrayFill(0.9f);
        cell.setPadding(4);
        cell.setHorizontalAlignment(Element.ALIGN_LEFT);
        return cell;
    }

    private PdfPCell bodyCell(String text) {
        PdfPCell cell = new PdfPCell(new Paragraph(text == null ? "" : text, TABLE_CELL_FONT));
        cell.setPadding(4);
        return cell;
    }

    /** Embeds a chart PNG, centered, scaled down (never up) to fit within maxWidthPt. */
    private Image chartImage(byte[] png, float maxWidthPt) {
        try {
            Image image = Image.getInstance(png);
            if (image.getWidth() > maxWidthPt) {
                image.scalePercent(maxWidthPt / image.getWidth() * 100f);
            }
            image.setAlignment(Element.ALIGN_CENTER);
            image.setSpacingBefore(6);
            image.setSpacingAfter(14);
            return image;
        } catch (Exception e) {
            throw new RuntimeException("Failed to embed chart image.", e);
        }
    }
}
