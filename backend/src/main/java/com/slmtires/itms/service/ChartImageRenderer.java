package com.slmtires.itms.service;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

import javax.imageio.ImageIO;

/**
 * Renders the same charts the on-screen reports use (see components/reports/TaskCharts.tsx and
 * PointsCharts.tsx), as PNG images, so the exported PDF/Excel reports carry actual graphics instead
 * of only tables of numbers. Plain Java2D drawing, no charting library dependency - same shapes and
 * theme colors as the frontend's hand-rolled SVG/CSS charts, just rasterized here for embedding.
 */
final class ChartImageRenderer {
    private ChartImageRenderer() {}

    static final Color BLUE = new Color(0x2a, 0x78, 0xd6);
    static final Color RED = new Color(0xd0, 0x3b, 0x3b);
    static final Color GREEN = new Color(0x0c, 0xa3, 0x0c);
    static final Color AMBER = new Color(0xfa, 0xb2, 0x19);
    static final Color ORANGE = new Color(0xec, 0x83, 0x5a);
    private static final Color AXIS = new Color(0xc3, 0xc2, 0xb7);
    private static final Color TEXT = new Color(0x33, 0x32, 0x2e);
    private static final Color MUTED = new Color(0x75, 0x74, 0x6d);
    private static final Font LABEL_FONT = new Font("SansSerif", Font.PLAIN, 13);
    private static final Font VALUE_FONT = new Font("SansSerif", Font.BOLD, 13);

    public record Series(String label, List<Integer> values, Color color) {}
    public record Slice(String label, long count, Color color) {}

    /** "Tasks Assigned vs. Completed, by Person" (or a single "This period" bar for an individual report). */
    static byte[] groupedBars(List<String> groupLabels, Series a, Series b) {
        int n = groupLabels.size();
        int barWidth = 34, gap = 10, groupPad = 46;
        int groupWidth = barWidth * 2 + gap + groupPad;
        int width = Math.max(n * groupWidth + 60, 360);
        int chartHeight = 240;
        int baseY = 50 + chartHeight;
        int height = baseY + 70;

        BufferedImage image = newCanvas(width, height);
        Graphics2D g = begin(image);
        g.setColor(AXIS);
        g.drawLine(40, baseY, width - 20, baseY);

        int maxValue = 1;
        for (int i = 0; i < n; i++) maxValue = Math.max(maxValue, Math.max(a.values().get(i), b.values().get(i)));

        for (int i = 0; i < n; i++) {
            int groupX = 40 + i * groupWidth + groupPad / 2;
            int aVal = a.values().get(i), bVal = b.values().get(i);
            int aH = (int) Math.round((aVal / (double) maxValue) * chartHeight);
            int bH = (int) Math.round((bVal / (double) maxValue) * chartHeight);
            drawBar(g, groupX, baseY, barWidth, aH, a.color(), String.valueOf(aVal));
            drawBar(g, groupX + barWidth + gap, baseY, barWidth, bH, b.color(), String.valueOf(bVal));
            drawCentered(g, groupLabels.get(i), groupX + barWidth + gap / 2, baseY + 22, LABEL_FONT, TEXT);
        }
        drawLegend(g, width, baseY + 48, a.label(), a.color(), b.label(), b.color());
        g.dispose();
        return toPng(image);
    }

    /** "Points Retained vs Lost" - two bars rising from a shared baseline, one up (green) one down (red). */
    static byte[] divergingBars(java.math.BigDecimal retained, java.math.BigDecimal lost) {
        int width = 260, height = 300;
        int baseline = height / 2;
        int maxBar = 100;
        double max = Math.max(1.0, Math.max(retained.doubleValue(), lost.doubleValue()));

        BufferedImage image = newCanvas(width, height);
        Graphics2D g = begin(image);
        g.setColor(AXIS);
        g.drawLine(30, baseline, width - 30, baseline);

        int barWidth = 60;
        int retainedH = (int) Math.round((retained.doubleValue() / max) * maxBar);
        int lostH = (int) Math.round((lost.doubleValue() / max) * maxBar);

        int x1 = width / 2 - barWidth - 15;
        g.setColor(GREEN);
        g.fillRoundRect(x1, baseline - retainedH, barWidth, Math.max(retainedH, 2), 4, 4);
        drawCentered(g, retained.toPlainString(), x1 + barWidth / 2, baseline - retainedH - 8, VALUE_FONT, GREEN);
        drawCentered(g, "Retained", x1 + barWidth / 2, baseline + 20, LABEL_FONT, MUTED);

        int x2 = width / 2 + 15;
        g.setColor(RED);
        g.fillRoundRect(x2, baseline, barWidth, Math.max(lostH, 2), 4, 4);
        drawCentered(g, lost.toPlainString(), x2 + barWidth / 2, baseline + lostH + 18, VALUE_FONT, RED);
        drawCentered(g, "Lost", x2 + barWidth / 2, baseline - 8, LABEL_FONT, MUTED);

        g.dispose();
        return toPng(image);
    }

    /** "Monthly Points Trend" / "Team Efficiency Trend" - one bar per month, 0-100%. */
    static byte[] monthlyBars(List<String> monthLabels, List<Integer> rates) {
        int n = monthLabels.size();
        int barWidth = 46, gap = 18;
        int width = Math.max(n * (barWidth + gap) + 40, 320);
        int chartHeight = 200;
        int baseY = 40 + chartHeight;
        int height = baseY + 50;

        BufferedImage image = newCanvas(width, height);
        Graphics2D g = begin(image);
        g.setColor(AXIS);
        g.drawLine(20, baseY, width - 20, baseY);

        for (int i = 0; i < n; i++) {
            int x = 20 + i * (barWidth + gap) + gap / 2;
            int rate = rates.get(i);
            int barH = (int) Math.round((Math.max(rate, 0) / 100.0) * chartHeight);
            Color color = rate >= 80 ? GREEN : rate >= 50 ? AMBER : RED;
            drawBar(g, x, baseY, barWidth, barH, color, rate + "%");
            drawCentered(g, monthLabels.get(i), x + barWidth / 2, baseY + 20, LABEL_FONT, TEXT);
        }
        g.dispose();
        return toPng(image);
    }

    /** "Strike Distribution" - a donut, thick-stroked arcs, one per slice. */
    static byte[] donut(List<Slice> slices) {
        int width = 320, height = 260;
        int cx = 120, cy = 130, radius = 90, thickness = 34;

        BufferedImage image = newCanvas(width, height);
        Graphics2D g = begin(image);
        long total = slices.stream().mapToLong(Slice::count).sum();

        if (total == 0) {
            g.setColor(AXIS);
            g.setStroke(new BasicStroke(thickness, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
            g.draw(new Arc2D.Double(cx - radius, cy - radius, radius * 2d, radius * 2d, 0, 360, Arc2D.OPEN));
        } else {
            double startAngle = 90;
            g.setStroke(new BasicStroke(thickness, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
            for (Slice slice : slices) {
                if (slice.count() == 0) continue;
                double extent = -360.0 * slice.count() / total;
                g.setColor(slice.color());
                g.draw(new Arc2D.Double(cx - radius, cy - radius, radius * 2d, radius * 2d, startAngle, extent, Arc2D.OPEN));
                startAngle += extent;
            }
        }

        int legendX = 240, legendY = 60;
        g.setFont(LABEL_FONT);
        for (Slice slice : slices) {
            g.setColor(slice.color());
            g.fillOval(legendX, legendY - 10, 10, 10);
            g.setColor(TEXT);
            int percent = total == 0 ? 0 : Math.round(slice.count() * 100f / total);
            g.drawString(slice.label() + " - " + percent + "%", legendX + 16, legendY);
            legendY += 22;
        }
        g.dispose();
        return toPng(image);
    }

    // ---------------------------------------------------------------- shared drawing primitives

    private static BufferedImage newCanvas(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, height);
        g.dispose();
        return image;
    }

    private static Graphics2D begin(BufferedImage image) {
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        return g;
    }

    private static void drawBar(Graphics2D g, int x, int baseY, int width, int barHeight, Color color, String valueLabel) {
        int h = Math.max(barHeight, 2);
        g.setColor(color);
        g.fillRoundRect(x, baseY - h, width, h, 3, 3);
        drawCentered(g, valueLabel, x + width / 2, baseY - h - 6, VALUE_FONT, color);
    }

    private static void drawCentered(Graphics2D g, String text, int centerX, int baselineY, Font font, Color color) {
        g.setFont(font);
        g.setColor(color);
        FontMetrics fm = g.getFontMetrics();
        g.drawString(text, centerX - fm.stringWidth(text) / 2, baselineY);
    }

    private static void drawLegend(Graphics2D g, int canvasWidth, int y, String labelA, Color colorA, String labelB, Color colorB) {
        g.setFont(LABEL_FONT);
        FontMetrics fm = g.getFontMetrics();
        int swatch = 10, gapAfterSwatch = 5, gapBetween = 24;
        int totalWidth = swatch + gapAfterSwatch + fm.stringWidth(labelA) + gapBetween + swatch + gapAfterSwatch + fm.stringWidth(labelB);
        int x = Math.max(10, (canvasWidth - totalWidth) / 2);

        g.setColor(colorA);
        g.fillRect(x, y - swatch, swatch, swatch);
        g.setColor(TEXT);
        g.drawString(labelA, x + swatch + gapAfterSwatch, y);
        x += swatch + gapAfterSwatch + fm.stringWidth(labelA) + gapBetween;

        g.setColor(colorB);
        g.fillRect(x, y - swatch, swatch, swatch);
        g.setColor(TEXT);
        g.drawString(labelB, x + swatch + gapAfterSwatch, y);
    }

    private static byte[] toPng(BufferedImage image) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
