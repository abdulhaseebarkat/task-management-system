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
  "0": "Full points (no extensions)",
  "1": "50% (1 deadline extension)",
  "2": "25% (2 deadline extensions)",
  FULL: "Full points (no extensions)",
  STRIKE_1: "50% (1 deadline extension)",
  STRIKE_2: "25% (2 deadline extensions)",
  FAILED: "Failed (3rd deadline extension)",
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
  STRIKE_1: { text: "1st deadline extension applied · −50%", tone: "text-serious" },
  STRIKE_2: { text: "2nd deadline extension applied · −50% of remaining", tone: "text-destructive" },
  FAILED: { text: "Failed · 3rd deadline missed", tone: "text-destructive" },
  COMPLETED: { text: "Completed", tone: "text-success" },
  CANCELLED: { text: "Cancelled · excluded from scoring", tone: "text-muted-foreground" },
  REASSIGNED: { text: "Reassigned away · excluded from scoring", tone: "text-muted-foreground" },
  ASSIGNED: { text: "Assigned · base value set", tone: "text-muted-foreground" },
}

export function pointEventLabel(eventType: string): { text: string; tone: string } {
  return EVENT_LABELS[eventType] ?? { text: eventType, tone: "text-muted-foreground" }
}

export interface EmployeePerformanceRow { employeeId: number; employeeName: string; pointsEarned: number; tasksCompleted: number }

/** Flat fill, no gradient/shadow/icon - the leader gets the app's primary color, everyone else a neutral slate so rank still reads at a glance without any decoration. */
const LEADER_COLOR = "#2a78d6"
const BAR_COLOR = "#9aa0a6"
const CHART_HEIGHT = 110

/**
 * "Employee Performance" - one bar per employee, ranked by points actually earned from completed
 * work (resultingPoints of COMPLETED events only - the same accurate number as the Points
 * Leaderboard, just ranked by raw points instead of efficiency rate, which is a genuinely different
 * ordering: someone who finished a few small LOW-priority tasks cleanly can sit at 100% efficiency
 * while earning far fewer points than someone who tackled bigger CRITICAL work with a strike or two).
 * Plain HTML/CSS bars (no SVG viewBox) so value labels sit in normal document flow and can never be
 * clipped, however tall the leading bar is.
 */
export function EmployeePerformanceChart({ rows, onSelect }: { rows: EmployeePerformanceRow[]; onSelect?: (employeeId: number) => void }) {
  const sorted = [...rows].sort((a, b) => b.pointsEarned - a.pointsEarned)
  const max = Math.max(...sorted.map((r) => r.pointsEarned), 1)

  if (sorted.length === 0) {
    return <p className="mt-3 text-xs text-muted-foreground">No completed tasks in this window.</p>
  }

  return (
    <div className="flex items-end justify-center gap-4 overflow-x-auto px-2 pb-1 pt-6">
      {sorted.map((row, i) => {
        const color = i === 0 ? LEADER_COLOR : BAR_COLOR
        const barHeight = row.pointsEarned <= 0 ? 3 : Math.max((row.pointsEarned / max) * CHART_HEIGHT, 4)
        return (
          <div
            key={row.employeeId}
            className={`flex w-[104px] flex-shrink-0 flex-col items-center rounded-md p-1 ${onSelect ? "cursor-pointer transition-colors hover:bg-accent" : ""}`}
            onClick={onSelect ? () => onSelect(row.employeeId) : undefined}
            title={onSelect ? `View ${row.employeeName}'s completed tasks` : undefined}
          >
            <span className="text-sm font-bold leading-none">{formatPoints(row.pointsEarned)}</span>
            <div className="mt-1.5 flex w-full items-end justify-center" style={{ height: CHART_HEIGHT }}>
              <div className="w-8 rounded-t-sm" style={{ height: barHeight, backgroundColor: color }} />
            </div>
            <div className="mt-2 flex w-full flex-col items-center gap-0.5 border-t border-border pt-1.5 text-center">
              <span className="w-full truncate text-xs font-medium" title={row.employeeName}>{row.employeeName}</span>
              <span className="text-[10px] text-muted-foreground">
                {row.tasksCompleted} task{row.tasksCompleted === 1 ? "" : "s"}
              </span>
            </div>
          </div>
        )
      })}
    </div>
  )
}
