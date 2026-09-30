import type { MonthlyRate } from "@/types/analytics"

/** 0 = full points, 1/2 = after the 1st/2nd strike, 3 = failed. Matches AnalyticsService#strikeLevelOf. */
export function strikeLevelColor(level: number): string {
  return level === 0 ? "var(--success)" : level === 1 ? "var(--warning)" : level === 2 ? "var(--serious)" : "var(--destructive)"
}

export function efficiencyTone(rate: number): string {
  return rate >= 80 ? "text-success" : rate >= 50 ? "text-warning" : "text-destructive"
}

export function efficiencyDotColor(rate: number): string {
  return rate >= 80 ? "var(--success)" : rate >= 50 ? "var(--warning)" : "var(--destructive)"
}

/**
 * The "Points Retained vs Lost" / "Points — This Task" diverging bar: a shared 2px baseline with
 * the retained bar growing up (green) and the lost bar hanging down (red), each column's bar
 * anchored against the line via a fixed-height flex container plus a matching-height spacer on
 * the other side, so both columns' baselines line up exactly.
 */
export function DivergingPointsBar({ retained, lost, height = 90 }: { retained: number; lost: number; height?: number }) {
  const max = Math.max(retained, lost, 1)
  const retainedHeight = retained <= 0 ? 0 : Math.max((retained / max) * height, 6)
  const lostHeight = lost <= 0 ? 0 : Math.max((lost / max) * height, 6)

  return (
    <div className="flex justify-center gap-14">
      <div className="flex w-20 flex-col items-center">
        <div className="flex flex-col items-center justify-end gap-1.5" style={{ height }}>
          {retained > 0 && <span className="text-sm font-bold text-success">+{formatPoints(retained)}</span>}
          <div className="w-14 rounded-t-md bg-success" style={{ height: retainedHeight }} />
        </div>
        <div className="h-0.5 w-full bg-foreground" />
        <div style={{ height: 20 }} />
        <span className="mt-1 text-[11px] text-muted-foreground">Retained</span>
      </div>
      <div className="flex w-20 flex-col items-center">
        <div style={{ height }} />
        <div className="h-0.5 w-full bg-foreground" />
        <div className="flex flex-col items-center justify-start gap-1.5" style={{ height }}>
          <div className="w-14 rounded-b-md bg-destructive" style={{ height: lostHeight }} />
          {lost > 0 && <span className="text-sm font-bold text-destructive">−{formatPoints(lost)}</span>}
        </div>
        <span className="mt-1 text-[11px] text-muted-foreground">Lost</span>
      </div>
    </div>
  )
}

export function formatPoints(value: number): string {
  return Number.isInteger(value) ? String(value) : value.toFixed(2).replace(/0+$/, "").replace(/\.$/, "")
}

/** Efficiency-rate-per-month bar chart, shared by the department's Team Efficiency Trend and the individual's Monthly Points Trend. */
export function MonthlyRateBars({ months }: { months: MonthlyRate[] }) {
  return (
    <div className="mt-3 flex h-[110px] items-end gap-3">
      {months.map((m) => (
        <div key={m.monthStart} className="flex flex-1 flex-col items-center gap-1.5">
          <span className="text-[11px] font-bold">{m.rate}%</span>
          <div className="w-full rounded" style={{ height: Math.max((m.rate / 100) * 82, m.rate > 0 ? 6 : 2), backgroundColor: efficiencyDotColor(m.rate) }} />
          <span className="text-[10px] text-muted-foreground">{m.monthLabel}</span>
        </div>
      ))}
    </div>
  )
}

const BREAKDOWN_LABELS: Record<string, string> = {
  "0": "Full points (0 strikes)",
  "1": "50% (1 strike)",
  "2": "25% (2 strikes)",
  FULL: "Full points (0 strikes)",
  STRIKE_1: "50% (1 strike)",
  STRIKE_2: "25% (2 strikes)",
  FAILED: "Failed (3 strikes)",
}

export function breakdownLabel(level: string): string {
  return BREAKDOWN_LABELS[level] ?? level
}

export function strikeLevelDotColor(level: string): string {
  if (level === "0" || level === "FULL") return "var(--success)"
  if (level === "1" || level === "STRIKE_1") return "var(--warning)"
  if (level === "2" || level === "STRIKE_2") return "var(--serious)"
  return "var(--destructive)"
}

const EVENT_LABELS: Record<string, { text: string; tone: string }> = {
  STRIKE_1: { text: "Strike 1 applied · −50%", tone: "text-serious" },
  STRIKE_2: { text: "Strike 2 applied · −50% of remaining", tone: "text-destructive" },
  FAILED: { text: "Failed · 3rd deadline missed", tone: "text-destructive" },
  COMPLETED: { text: "Completed", tone: "text-success" },
  CANCELLED: { text: "Cancelled · excluded from scoring", tone: "text-muted-foreground" },
  REASSIGNED: { text: "Reassigned away · excluded from scoring", tone: "text-muted-foreground" },
  ASSIGNED: { text: "Assigned · base value set", tone: "text-muted-foreground" },
}

export function pointEventLabel(eventType: string): { text: string; tone: string } {
  return EVENT_LABELS[eventType] ?? { text: eventType, tone: "text-muted-foreground" }
}
