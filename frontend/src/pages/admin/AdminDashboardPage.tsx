import { useMemo, useState } from "react"
import { Link, useNavigate } from "react-router-dom"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { AppLayout } from "@/components/layout/AppLayout"
import { badgeClass, isTaskOverdue, label, OVERDUE_BADGE_CLASS } from "@/components/tasks/taskDisplay"
import { efficiencyDotColor, EmployeePerformanceChart, MonthlyRateBars, strikeLevelColor } from "@/components/reports/PointsCharts"
import { AssignedVsCompletedChart, EmployeeCountBarChart, EmployeeCountTable } from "@/components/reports/TaskCharts"
import { useRealtimeSubscription } from "@/hooks/useRealtimeSubscription"
import { backfillPoints, getDashboardAnalytics } from "@/api/analytics"
import { listTasks, listTaskCategories } from "@/api/tasks"
import { listUsers } from "@/api/users"
import { presetRange, type DatePreset } from "@/lib/dateRange"
import { apiErrorMessage } from "@/lib/apiError"
import type { AnalyticsFilters, AtRiskTaskItem, EmployeeCount, PointsLeaderboardEntry, PriorityCount, StrikeDistributionSlice, WeekCount } from "@/types/analytics"
import type { Assignee, TaskPriority, TaskStatus } from "@/types/tasks"

const STATUS_COLORS: Record<string, string> = {
  COMPLETED: "#0ca30c",
  IN_PROGRESS: "#2a78d6",
  ASSIGNED: "#4a3aa7",
  ON_HOLD: "#fab219",
  BLOCKED: "#ec835a",
  FAILED: "#d03b3b",
  CANCELLED: "#898781",
}
const STATUS_ORDER = ["COMPLETED", "IN_PROGRESS", "ASSIGNED", "ON_HOLD", "BLOCKED", "FAILED", "CANCELLED"]
const STRIKE_LABELS: Record<string, string> = { "0": "No extensions — full points", "1": "1 extension — 50%", "2": "2 extensions — 25%", FAILED: "Failed (3rd extension)" }
const STRIKE_ORDER = ["0", "1", "2", "FAILED"]
const PRIORITY_COLORS: Record<string, string> = {
  LOW: "#86b6ef",
  MEDIUM: "#3987e5",
  HIGH: "#1c5cab",
  CRITICAL: "#0d366b",
}
const AVATAR_COLORS = ["#2a78d6", "#4a3aa7", "#0ca30c", "#ec835a", "#fab219", "#d03b3b"]

function initials(name: string): string {
  return name.split(" ").map((part) => part[0]).slice(0, 2).join("").toUpperCase()
}

function avatarColor(id: number): string {
  return AVATAR_COLORS[id % AVATAR_COLORS.length]
}

function Tile({ label: text, value, tone }: { label: string; value: string | number; tone?: string }) {
  return (
    <div className="rounded-lg border border-border bg-card px-4 py-3.5">
      <div className="text-[11px] font-semibold uppercase tracking-wide text-muted-foreground">{text}</div>
      <div className={`mt-1.5 text-2xl font-bold ${tone ?? ""}`}>{value}</div>
    </div>
  )
}

function Card({ title, subtitle, children }: { title: string; subtitle: string; children: React.ReactNode }) {
  return (
    <div className="rounded-lg border border-border bg-card p-4">
      <p className="text-sm font-bold">{title}</p>
      <p className="mt-0.5 text-xs text-muted-foreground">{subtitle}</p>
      {children}
    </div>
  )
}

function StatusDonut({ slices, onSelect }: { slices: { status: string; count: number; percent: number }[]; onSelect?: (status: string) => void }) {
  const total = slices.reduce((sum, s) => sum + s.count, 0)
  const radius = 70
  const circumference = 2 * Math.PI * radius
  let offset = 0
  const ordered = STATUS_ORDER.map((status) => slices.find((s) => s.status === status)).filter((s): s is typeof slices[number] => !!s)

  return (
    <div className="mt-3.5 flex items-center gap-4">
      <svg width="150" height="150" viewBox="0 0 180 180">
        <circle cx="90" cy="90" r={radius} fill="none" stroke="#e1e0d9" strokeWidth="24" />
        {total > 0 && ordered.filter((s) => s.count > 0).map((s) => {
          const length = (s.count / total) * circumference
          const el = (
            <circle key={s.status} cx="90" cy="90" r={radius} fill="none" stroke={STATUS_COLORS[s.status]}
              strokeWidth="24" strokeDasharray={`${length} ${circumference - length}`}
              strokeDashoffset={-offset} transform="rotate(-90 90 90)"
              onClick={onSelect ? () => onSelect(s.status) : undefined} className={onSelect ? "cursor-pointer" : undefined} />
          )
          offset += length
          return el
        })}
        <text x="90" y="86" textAnchor="middle" fontSize="24" fontWeight="700">{total}</text>
        <text x="90" y="104" textAnchor="middle" fontSize="10" fill="#898781">TOTAL TASKS</text>
      </svg>
      <div className="flex flex-col gap-2">
        {ordered.map((s) => (
          <div
            key={s.status}
            className={`flex items-center gap-1.5 rounded p-1 -m-1 text-xs ${onSelect ? "cursor-pointer hover:bg-accent" : ""}`}
            onClick={onSelect ? () => onSelect(s.status) : undefined}
          >
            <span className="h-1.5 w-1.5 rounded-full" style={{ backgroundColor: STATUS_COLORS[s.status] }} />
            <span className="text-muted-foreground">{label(s.status)}</span>
            <span className="ml-auto font-bold">{s.percent}%</span>
          </div>
        ))}
      </div>
    </div>
  )
}

function HorizontalBars({ rows, color, max, onSelect }: { rows: { id: number; label: string; count: number }[]; color: string; max: number; onSelect?: (id: number) => void }) {
  const scale = Math.max(max, 1)
  return (
    <div className="mt-4 flex flex-col gap-3">
      {rows.map((row) => (
        <div
          key={row.id}
          className={`flex items-center gap-2.5 rounded p-1 -m-1 ${onSelect ? "cursor-pointer hover:bg-accent" : ""}`}
          onClick={onSelect ? () => onSelect(row.id) : undefined}
        >
          <span className="w-[74px] flex-shrink-0 truncate text-xs text-muted-foreground" title={row.label}>{row.label}</span>
          <div className="h-3.5 flex-grow rounded bg-[#e1e0d9]">
            <div className="h-3.5 rounded" style={{ width: `${(row.count / scale) * 100}%`, backgroundColor: color }} />
          </div>
          <span className="w-4 flex-shrink-0 text-right text-xs font-bold">{row.count}</span>
        </div>
      ))}
    </div>
  )
}

function PriorityBars({ rows, onSelect }: { rows: PriorityCount[]; onSelect?: (priority: string) => void }) {
  const max = Math.max(...rows.map((r) => r.count), 1)
  const barWidth = 60
  const gap = 30
  const chartHeight = 140
  return (
    <svg width="100%" height="170" viewBox="0 0 350 200" className="mt-2">
      <line x1="10" y1="170" x2="340" y2="170" stroke="#c3c2b7" strokeWidth="1" />
      {rows.map((row, i) => {
        const x = 30 + i * (barWidth + gap)
        const h = Math.max((row.count / max) * chartHeight, row.count > 0 ? 8 : 0)
        const y = 170 - h
        return (
          <g key={row.priority} onClick={onSelect ? () => onSelect(row.priority) : undefined} className={onSelect ? "cursor-pointer" : undefined}>
            <rect x={x} y="0" width={barWidth} height="170" fill="transparent" />
            <rect x={x} y={y} width={barWidth} height={h} rx="4" fill={PRIORITY_COLORS[row.priority]} />
            <text x={x + barWidth / 2} y={y - 8} textAnchor="middle" fontSize="13" fontWeight="700">{row.count}</text>
            <text x={x + barWidth / 2} y="188" textAnchor="middle" fontSize="11" fill="#898781">{label(row.priority)}</text>
          </g>
        )
      })}
    </svg>
  )
}

function TrendLine({ weeks }: { weeks: WeekCount[] }) {
  const max = Math.max(...weeks.map((w) => w.count), 1)
  const width = 660
  const chartHeight = 150
  const top = 20
  const step = (width - 40) / (weeks.length - 1)
  const points = weeks.map((w, i) => {
    const x = 20 + i * step
    const y = top + chartHeight - (w.count / max) * chartHeight
    return { x, y, week: w }
  })
  const path = points.map((p, i) => `${i === 0 ? "M" : "L"}${p.x},${p.y}`).join(" ")
  const last = points[points.length - 1]

  return (
    <svg width="100%" height="210" viewBox={`0 0 ${width} 210`} className="mt-2">
      <line x1="20" y1={top + chartHeight} x2={width - 20} y2={top + chartHeight} stroke="#e1e0d9" strokeWidth="1" />
      <path d={path} fill="none" stroke="#2a78d6" strokeWidth="2" />
      {last && <circle cx={last.x} cy={last.y} r="5" fill="#2a78d6" />}
      {last && <text x={last.x} y={last.y - 10} textAnchor="middle" fontSize="12" fontWeight="700" fill="#2a78d6">{last.week.count}</text>}
      {points.map((p, i) => (i === 0 || i === points.length - 1 || i % 2 === 0) && (
        <text key={p.week.weekStart} x={p.x} y={top + chartHeight + 22} fontSize="11" fill="#898781" textAnchor="middle">{p.week.weekLabel}</text>
      ))}
    </svg>
  )
}

function OverdueBars({ weeks }: { weeks: WeekCount[] }) {
  const max = Math.max(...weeks.map((w) => w.count), 1)
  const barWidth = 18
  const gap = 14
  const chartHeight = 96
  const total = weeks.reduce((sum, w) => sum + w.count, 0)
  return (
    <svg width="100%" height="210" viewBox="0 0 300 210" className="mt-2">
      <line x1="10" y1="170" x2="290" y2="170" stroke="#c3c2b7" strokeWidth="1" />
      {weeks.map((week, i) => {
        const x = 20 + i * (barWidth + gap)
        const h = Math.max((week.count / max) * chartHeight, week.count > 0 ? 6 : 0)
        return (
          <g key={week.weekStart}>
            <rect x={x} y={170 - h} width={barWidth} height={h} rx="3" fill="#d03b3b" />
            {week.count > 0 && (
              <text x={x + barWidth / 2} y={170 - h - 6} textAnchor="middle" fontSize="11" fontWeight="700" fill="#d03b3b">{week.count}</text>
            )}
            <text x={x + barWidth / 2} y="184" textAnchor="middle" fontSize="9" fill="#898781">{week.weekLabel}</text>
          </g>
        )
      })}
      <text x="150" y="202" textAnchor="middle" fontSize="11" fill="#898781">{total} overdue right now, by the week their due date fell in</text>
    </svg>
  )
}

function PointsLeaderboard({ rows, onSelect }: { rows: PointsLeaderboardEntry[]; onSelect?: (employeeId: number) => void }) {
  return (
    <div className="mt-3.5 flex flex-col gap-3">
      {rows.map((row) => (
        <div
          key={row.employeeId}
          className={`flex items-center gap-2.5 rounded p-1 -m-1 ${onSelect ? "cursor-pointer hover:bg-accent" : ""}`}
          onClick={onSelect ? () => onSelect(row.employeeId) : undefined}
        >
          <span className="h-2 w-2 flex-shrink-0 rounded-full" style={{ backgroundColor: efficiencyDotColor(row.efficiencyRate) }} />
          <span className="w-[100px] flex-shrink-0 truncate text-xs" title={row.employeeName}>{row.employeeName}</span>
          <div className="h-3.5 flex-grow rounded bg-[#e1e0d9]">
            <div className="h-3.5 rounded" style={{ width: `${row.efficiencyRate}%`, backgroundColor: efficiencyDotColor(row.efficiencyRate) }} />
          </div>
          <span className="w-9 flex-shrink-0 text-right text-xs font-bold">{row.efficiencyRate}%</span>
          <span className="w-14 flex-shrink-0 text-right text-[11px] text-muted-foreground">{row.pointsEarned} pts</span>
        </div>
      ))}
      <div className="mt-1 flex gap-3.5 text-[10px] text-muted-foreground">
        <span className="flex items-center gap-1"><span className="h-1.5 w-1.5 rounded-full bg-success" />≥80%</span>
        <span className="flex items-center gap-1"><span className="h-1.5 w-1.5 rounded-full bg-warning" />50–79%</span>
        <span className="flex items-center gap-1"><span className="h-1.5 w-1.5 rounded-full bg-destructive" />&lt;50%</span>
      </div>
    </div>
  )
}

function StrikeDonut({ slices }: { slices: StrikeDistributionSlice[] }) {
  const total = slices.reduce((sum, s) => sum + s.count, 0)
  const radius = 62
  const circumference = 2 * Math.PI * radius
  let offset = 0
  const ordered = STRIKE_ORDER.map((level) => slices.find((s) => s.level === level)).filter((s): s is StrikeDistributionSlice => !!s)
  const zeroPercent = slices.find((s) => s.level === "0")?.percent ?? 0

  return (
    <div className="mt-3.5 flex items-center gap-5">
      <svg width="140" height="140" viewBox="0 0 160 160">
        <circle cx="80" cy="80" r={radius} fill="none" stroke="#e1e0d9" strokeWidth="22" />
        {total > 0 && ordered.filter((s) => s.count > 0).map((s) => {
          const length = (s.count / total) * circumference
          const el = (
            <circle key={s.level} cx="80" cy="80" r={radius} fill="none" stroke={strikeLevelColor(s.level === "FAILED" ? 3 : Number(s.level))}
              strokeWidth="22" strokeDasharray={`${length} ${circumference - length}`}
              strokeDashoffset={-offset} transform="rotate(-90 80 80)" />
          )
          offset += length
          return el
        })}
        <text x="80" y="76" textAnchor="middle" fontSize="20" fontWeight="700">{zeroPercent}%</text>
        <text x="80" y="94" textAnchor="middle" fontSize="9" fill="#898781">NO EXTENSIONS</text>
      </svg>
      <div className="flex flex-col gap-2">
        {ordered.map((s) => (
          <div key={s.level} className="flex items-center gap-1.5 text-xs">
            <span className="h-1.5 w-1.5 rounded-full" style={{ backgroundColor: strikeLevelColor(s.level === "FAILED" ? 3 : Number(s.level)) }} />
            <span className="text-muted-foreground">{STRIKE_LABELS[s.level]}</span>
            <span className="ml-auto font-bold">{s.percent}%</span>
          </div>
        ))}
      </div>
    </div>
  )
}

function AtRiskList({ items }: { items: AtRiskTaskItem[] }) {
  function dueLabel(dueDate: string | null): string {
    if (!dueDate) return "no due date"
    const days = Math.ceil((new Date(dueDate).getTime() - Date.now()) / 86_400_000)
    if (days < 0) return `overdue by ${Math.abs(days)}d`
    if (days === 0) return "due today"
    return `due in ${days}d`
  }

  if (items.length === 0) {
    return <p className="mt-3.5 text-xs text-muted-foreground">No open task currently has a point-deducting deadline extension against it.</p>
  }
  return (
    <div className="mt-3.5 flex flex-col">
      {items.map((item) => (
        <Link key={item.assignmentId} to={`/admin/tasks?taskId=${item.taskId}&assignmentId=${item.assignmentId}`}
          className="flex items-center justify-between gap-2 border-t border-border py-2.5 first:border-0">
          <div className="min-w-0">
            <div className="truncate text-xs font-semibold">{item.title}</div>
            <div className="truncate text-[11px] text-muted-foreground">{item.taskNumber} · {item.employeeName}</div>
          </div>
          <span className={`flex-shrink-0 rounded-full px-2 py-1 text-[11px] font-bold ${item.strikeLevel === 1 ? "bg-warning/15 text-[#a8790f]" : "bg-serious/15 text-serious"}`}>
            Extension {item.strikeLevel} · {dueLabel(item.dueDate)}
          </span>
        </Link>
      ))}
    </div>
  )
}

const selectClass = "rounded-md border border-input bg-card px-2.5 py-2 text-xs"

export function AdminDashboardPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [preset, setPreset] = useState<DatePreset>("30")
  const [employeeId, setEmployeeId] = useState("")
  const [status, setStatus] = useState("")
  const [priority, setPriority] = useState("")
  const [categoryId, setCategoryId] = useState("")

  const { from, to } = useMemo(() => presetRange(preset), [preset])
  const filters: AnalyticsFilters = {
    from,
    to,
    employeeId: employeeId ? Number(employeeId) : undefined,
    status: (status || undefined) as TaskStatus | undefined,
    priority: (priority || undefined) as TaskPriority | undefined,
    categoryId: categoryId ? Number(categoryId) : undefined,
  }

  const analyticsQuery = useQuery({ queryKey: ["analytics", "dashboard", filters], queryFn: () => getDashboardAnalytics(filters) })
  const categoriesQuery = useQuery({ queryKey: ["task-categories"], queryFn: listTaskCategories })
  const usersQuery = useQuery({ queryKey: ["users"], queryFn: listUsers })
  const recentTasksQuery = useQuery({
    queryKey: ["tasks", "list", "dashboard-recent", filters.employeeId, filters.status, filters.priority, filters.categoryId],
    queryFn: () => listTasks({
      page: 0, size: 5, sort: "updated", direction: "desc",
      assigneeId: filters.employeeId, status: filters.status, priority: filters.priority, categoryId: filters.categoryId,
    }),
  })

  useRealtimeSubscription("/topic/admin-dashboard", () => {
    queryClient.invalidateQueries({ queryKey: ["analytics"] })
    queryClient.invalidateQueries({ queryKey: ["tasks", "list", "dashboard-recent"] })
  })

  const [backfillResult, setBackfillResult] = useState<string | null>(null)
  const backfillMutation = useMutation({
    mutationFn: backfillPoints,
    onSuccess: (result) => {
      setBackfillResult(result.assignmentsSeeded === 0 ? "Already up to date - nothing to backfill." : `Seeded ${result.assignmentsSeeded} assignment${result.assignmentsSeeded === 1 ? "" : "s"} that predated the points system.`)
      queryClient.invalidateQueries({ queryKey: ["analytics"] })
    },
    onError: (error) => setBackfillResult(apiErrorMessage(error, "Could not run the backfill.")),
  })

  const teamMembers = (usersQuery.data ?? []).filter((u) => u.role === "TEAM_MEMBER")
  const categories = categoriesQuery.data ?? []
  const data = analyticsQuery.data

  function clearFilters() {
    setPreset("30")
    setEmployeeId("")
    setStatus("")
    setPriority("")
    setCategoryId("")
  }

  const hasFilters = employeeId !== "" || status !== "" || priority !== "" || categoryId !== "" || preset !== "30"

  // Click-through from a chart/table into the exact underlying tasks (or, for points-related
  // employee metrics, into their Individual Report - which already has the precise breakdown a
  // generic task-list filter can't express, e.g. "reassigned away" or "due-date history").
  function goToTasks(params: Record<string, string | number>) {
    navigate(`/admin/tasks?${new URLSearchParams(Object.fromEntries(Object.entries(params).map(([k, v]) => [k, String(v)]))).toString()}`)
  }
  function goToIndividualReport(selectedEmployeeId: number) {
    navigate(`/admin/reports?tab=individual&employeeId=${selectedEmployeeId}`)
  }
  // Same as goToIndividualReport but pre-filters the "Individual Tasks" table to only the
  // employee's tasks with an extended due date, since that's the precise list the "Due Date
  // Extensions by Employee" chart/table promises - not the full, unfiltered report.
  function goToExtendedTasks(selectedEmployeeId: number) {
    navigate(`/admin/reports?tab=individual&employeeId=${selectedEmployeeId}&onlyExtended=true`)
  }

  return (
    <AppLayout title="Dashboard" subtitle="Overview of IT department task activity">
      <div className="flex flex-col gap-5">
        <div className="flex flex-wrap items-center gap-2">
          <select aria-label="Date range" value={preset} onChange={(e) => setPreset(e.target.value as DatePreset)} className={selectClass}>
            <option value="7">Last 7 days</option>
            <option value="30">Last 30 days</option>
            <option value="90">Last 90 days</option>
            <option value="all">All time</option>
          </select>
          <select aria-label="Filter by employee" value={employeeId} onChange={(e) => setEmployeeId(e.target.value)} className={selectClass}>
            <option value="">All employees</option>
            {teamMembers.map((m) => <option key={m.id} value={m.id}>{m.name}</option>)}
          </select>
          <select aria-label="Filter by status" value={status} onChange={(e) => setStatus(e.target.value)} className={selectClass}>
            <option value="">All statuses</option>
            {["ASSIGNED", "IN_PROGRESS", "ON_HOLD", "BLOCKED", "COMPLETED", "REOPENED", "CANCELLED"].map((s) => <option key={s} value={s}>{label(s)}</option>)}
          </select>
          <select aria-label="Filter by priority" value={priority} onChange={(e) => setPriority(e.target.value)} className={selectClass}>
            <option value="">All priorities</option>
            {["LOW", "MEDIUM", "HIGH", "CRITICAL"].map((p) => <option key={p} value={p}>{label(p)}</option>)}
          </select>
          <select aria-label="Filter by category" value={categoryId} onChange={(e) => setCategoryId(e.target.value)} className={selectClass}>
            <option value="">All categories</option>
            {categories.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
          </select>
          {hasFilters && <button onClick={clearFilters} className="ml-auto text-xs font-semibold text-primary">Clear filters</button>}
        </div>

        {analyticsQuery.isLoading || !data ? (
          <p className="text-sm text-muted-foreground">Loading…</p>
        ) : (
          <>
            <Card title="Employee Performance" subtitle={`Ranked by points earned from completed tasks, ${from} to ${to} · Admin-only`}>
              <EmployeePerformanceChart
                rows={employeePerformanceRows(data.pointsLeaderboard, data.employeeCompletion)}
                onSelect={(id) => goToTasks({ assigneeId: id, status: "COMPLETED" })}
              />
            </Card>

            <div className="grid grid-cols-2 gap-3 sm:grid-cols-4 lg:grid-cols-8">
              <Tile label="Total Tasks" value={data.summary.totalTasks} />
              <Tile label="Active" value={data.summary.active} tone="text-primary" />
              <Tile label="Completed" value={data.summary.completed} tone="text-success" />
              <Tile label="Overdue" value={data.summary.overdue} tone={data.summary.overdue > 0 ? "text-destructive" : undefined} />
              <Tile label="Blocked" value={data.summary.blocked} tone={data.summary.blocked > 0 ? "text-serious" : undefined} />
              <Tile label="On Hold" value={data.summary.onHold} tone={data.summary.onHold > 0 ? "text-warning" : undefined} />
              <Tile label="Reassigned" value={data.summary.reassigned} />
              <Tile label="Completion Rate" value={`${data.summary.completionRate}%`} tone="text-success" />
            </div>

            <div className="grid grid-cols-1 gap-5 lg:grid-cols-[1.4fr_1fr]">
              <Card title="Tasks Assigned vs. Completed, by Person" subtitle={`Created ${from} to ${to} - excludes Admin`}>
                <AssignedVsCompletedChart
                  rows={data.employeeAssigned.map((e) => ({
                    label: e.employeeName,
                    assigned: e.count,
                    completed: data.employeeCompletion.find((c) => c.employeeId === e.employeeId)?.count ?? 0,
                  }))}
                />
              </Card>
              <Card title="Team Workload" subtitle="Active tasks per employee (live)">
                <HorizontalBars rows={data.teamWorkload.map(employeeRow)} color="#2a78d6" max={Math.max(...data.teamWorkload.map((r) => r.count), 1)} onSelect={(id) => goToTasks({ assigneeId: id })} />
              </Card>
            </div>

            <div>
              <div className="flex flex-wrap items-center gap-2.5">
                <h2 className="text-base font-bold">Points &amp; Performance</h2>
                <span className="text-[10px] text-muted-foreground">Admin-only · never shown to employees</span>
                <button
                  onClick={() => { setBackfillResult(null); backfillMutation.mutate() }}
                  disabled={backfillMutation.isPending}
                  className="ml-auto rounded-md border border-border px-2.5 py-1 text-[11px] font-semibold disabled:opacity-60"
                  title="One-time catch-up for tasks assigned before the points system existed. Safe to run more than once - already-tracked assignments are skipped."
                >
                  {backfillMutation.isPending ? "Backfilling…" : "Backfill points for existing tasks"}
                </button>
                {backfillResult && <span className="w-full text-[11px] text-muted-foreground">{backfillResult}</span>}
              </div>
              <div className="mt-3 grid grid-cols-1 gap-5 lg:grid-cols-[1.3fr_1fr]">
                <Card title="Points Leaderboard" subtitle={`Efficiency = points earned ÷ points possible, ${from} to ${to}`}>
                  <PointsLeaderboard rows={data.pointsLeaderboard} onSelect={goToIndividualReport} />
                </Card>
                <Card title="Team Efficiency Trend" subtitle="Department average, last 6 months">
                  <MonthlyRateBars months={data.teamEfficiencyTrend} />
                </Card>
              </div>
              <div className="mt-5 grid grid-cols-1 gap-5 lg:grid-cols-[1fr_1.2fr]">
                <Card title="Points Impact by Deadline Extensions" subtitle={`Org-wide, ${from} to ${to} - open tasks included at their current standing`}>
                  <StrikeDonut slices={data.strikeDistribution} />
                </Card>
                <Card title="At-Risk Tasks" subtitle="Currently open, sitting at 1 or 2 deadline extensions right now">
                  <AtRiskList items={data.atRiskTasks} />
                </Card>
              </div>
              <div className="mt-5 grid grid-cols-1 gap-5 lg:grid-cols-[1.4fr_1fr]">
                <Card title="Due Date Extensions by Employee" subtitle={`Tasks created ${from} to ${to} with an extended due date - with or without a points deduction`}>
                  <EmployeeCountBarChart rows={data.employeeDueDateExtensions} color="#d9a62c" emptyMessage="No due dates have been extended in this window." onSelect={goToExtendedTasks} />
                </Card>
                <Card title="Due Date Extensions" subtitle="Counts behind the graph">
                  <EmployeeCountTable rows={data.employeeDueDateExtensions} countLabel="Tasks extended" onSelect={goToExtendedTasks} />
                </Card>
              </div>
              <div className="mt-5 grid grid-cols-1 gap-5 lg:grid-cols-[1.4fr_1fr]">
                <Card title="Reassigned Away by Employee" subtitle={`Tasks created ${from} to ${to}, reassigned away from each employee`}>
                  <EmployeeCountBarChart rows={data.employeeReassignedAway} color="#5b6b82" emptyMessage="No tasks have been reassigned away in this window." onSelect={goToIndividualReport} />
                </Card>
                <Card title="Reassigned Away" subtitle="Counts behind the graph">
                  <EmployeeCountTable rows={data.employeeReassignedAway} countLabel="Tasks reassigned away" onSelect={goToIndividualReport} />
                </Card>
              </div>
            </div>

            <div className="grid grid-cols-1 gap-5 lg:grid-cols-2">
              <Card title="Task Status Distribution" subtitle={`All tasks, ${from} to ${to}`}>
                <StatusDonut slices={data.statusDistribution} onSelect={(s) => goToTasks({ status: s })} />
              </Card>
              <Card title="Priority Distribution" subtitle="All currently-open tasks">
                <PriorityBars rows={data.priorityDistribution} onSelect={(p) => goToTasks({ priority: p })} />
              </Card>
            </div>

            <div className="grid grid-cols-1 gap-5 lg:grid-cols-[2fr_1fr]">
              <Card title="Task Completion Trend" subtitle="Completions per week - last 8 weeks">
                <TrendLine weeks={data.completionTrend} />
              </Card>
              <Card title="Overdue Trend" subtitle="Current backlog by due-date week">
                <OverdueBars weeks={data.overdueTrend} />
              </Card>
            </div>

            <div className="grid grid-cols-1 gap-5 lg:grid-cols-2">
              <Card title="Employee Completion" subtitle={`Completed tasks, ${from} to ${to}`}>
                <HorizontalBars
                  rows={data.employeeCompletion.map(employeeRow)} color="#0ca30c" max={Math.max(...data.employeeCompletion.map((r) => r.count), 1)}
                  onSelect={(id) => goToTasks({ assigneeId: id, status: "COMPLETED" })}
                />
              </Card>
              <Card title="Category Distribution" subtitle={`Top categories, ${from} to ${to}`}>
                <HorizontalBars
                  rows={data.categoryDistribution.map((c) => ({ id: c.categoryId, label: c.categoryName, count: c.count }))} color="#2a78d6" max={Math.max(...data.categoryDistribution.map((r) => r.count), 1)}
                  onSelect={(id) => goToTasks({ categoryId: id })}
                />
              </Card>
            </div>

            <div className="rounded-lg border border-border bg-card p-4">
              <div className="mb-3 flex items-center justify-between">
                <div>
                  <p className="text-sm font-bold">Recent Tasks</p>
                  <p className="mt-0.5 text-xs text-muted-foreground">Most recently updated across the department</p>
                </div>
                <Link to="/admin/tasks" className="text-xs font-semibold text-primary">View all tasks →</Link>
              </div>
              {recentTasksQuery.data?.content.length === 0 ? (
                <p className="text-xs text-muted-foreground">No tasks match these filters.</p>
              ) : (
                <table className="w-full text-xs">
                  <thead>
                    <tr className="text-left text-[11px] uppercase tracking-wide text-muted-foreground">
                      <th className="pb-2 font-semibold">Task</th>
                      <th className="pb-2 font-semibold">Category</th>
                      <th className="pb-2 font-semibold">Priority</th>
                      <th className="pb-2 font-semibold">Assigned To</th>
                      <th className="pb-2 font-semibold">Progress</th>
                      <th className="pb-2 font-semibold">Status</th>
                      <th className="pb-2 font-semibold">Due Date</th>
                      <th className="pb-2 font-semibold">Actions</th>
                    </tr>
                  </thead>
                  <tbody>
                    {recentTasksQuery.data?.content.map((task) => (
                      <tr key={task.id} className="border-t border-border">
                        <td className="py-2.5 pr-2">
                          <div className="font-semibold">{task.taskNumber}</div>
                          <div className="text-muted-foreground">{task.title}</div>
                        </td>
                        <td className="py-2.5 pr-2">{task.category.name}</td>
                        <td className="py-2.5 pr-2"><span className={`rounded-full px-2 py-1 font-semibold ${badgeClass(task.priority)}`}>{label(task.priority)}</span></td>
                        <td className="py-2.5 pr-2">
                          <div className="flex -space-x-2">
                            {task.assignments.map((a: { user: Assignee }) => (
                              <div key={a.user.id} title={a.user.name} className="flex h-6 w-6 items-center justify-center rounded-full border-2 border-card text-[9px] font-bold text-white" style={{ backgroundColor: avatarColor(a.user.id) }}>
                                {initials(a.user.name)}
                              </div>
                            ))}
                          </div>
                        </td>
                        <td className="py-2.5 pr-2">
                          <div className="flex items-center gap-2">
                            <div className="h-2 w-[60px] rounded bg-[#e1e0d9]"><div className="h-2 rounded bg-primary" style={{ width: `${task.overallProgress}%` }} /></div>
                            <span className="text-muted-foreground">{task.overallProgress}%</span>
                          </div>
                        </td>
                        <td className="py-2.5 pr-2"><span className={`rounded-full px-2 py-1 font-semibold ${badgeClass(task.status)}`}>{label(task.status)}</span></td>
                        <td className={`py-2.5 pr-2 ${isTaskOverdue(task) ? "text-destructive" : "text-muted-foreground"}`}>
                          {task.dueDate ?? "—"}
                          {isTaskOverdue(task) && <span className={`ml-1.5 ${OVERDUE_BADGE_CLASS}`}>Overdue</span>}
                        </td>
                        <td className="py-2.5"><Link to="/admin/tasks" className="font-semibold text-primary">View</Link></td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </div>
          </>
        )}
      </div>
    </AppLayout>
  )
}

function employeeRow(row: EmployeeCount): { id: number; label: string; count: number } {
  return { id: row.employeeId, label: row.employeeName, count: row.count }
}

/** Joins the leaderboard's pointsEarned with employeeCompletion's task count - both already computed server-side, just combined for this one chart. */
function employeePerformanceRows(leaderboard: PointsLeaderboardEntry[], completion: EmployeeCount[]) {
  const completedByEmployee = new Map(completion.map((c) => [c.employeeId, c.count]))
  return leaderboard.map((entry) => ({
    employeeId: entry.employeeId,
    employeeName: entry.employeeName,
    pointsEarned: entry.pointsEarned,
    tasksCompleted: completedByEmployee.get(entry.employeeId) ?? 0,
  }))
}
