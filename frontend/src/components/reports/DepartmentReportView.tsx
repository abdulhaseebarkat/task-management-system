import { useMemo, useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { exportDepartmentReport, type ExportFormat } from "@/api/reports"
import { getDashboardAnalytics } from "@/api/analytics"
import { label } from "@/components/tasks/taskDisplay"
import { AssignedVsCompletedChart, StatusBreakdownTable } from "@/components/reports/TaskCharts"
import { presetRange, type DatePreset } from "@/lib/dateRange"
import { apiErrorMessage } from "@/lib/apiError"

const selectClass = "rounded-md border border-input bg-card px-2.5 py-2 text-xs"

function Tile({ label: text, value, tone }: { label: string; value: string | number; tone?: string }) {
  return (
    <div className="rounded-lg border border-border bg-card px-4 py-3.5">
      <div className="text-[11px] font-semibold uppercase tracking-wide text-muted-foreground">{text}</div>
      <div className={`mt-1.5 text-2xl font-bold ${tone ?? ""}`}>{value}</div>
    </div>
  )
}

function Card({ title, subtitle, children }: { title: string; subtitle?: string; children: React.ReactNode }) {
  return (
    <div className="rounded-lg border border-border bg-card p-4">
      <p className="text-sm font-bold">{title}</p>
      {subtitle && <p className="mt-0.5 text-xs text-muted-foreground">{subtitle}</p>}
      <div className="mt-3">{children}</div>
    </div>
  )
}

export function DepartmentReportView() {
  const [preset, setPreset] = useState<DatePreset>("30")
  const { from, to } = useMemo(() => presetRange(preset), [preset])
  const [error, setError] = useState<string | null>(null)
  const [exporting, setExporting] = useState<ExportFormat | null>(null)

  const query = useQuery({
    queryKey: ["reports", "department", from, to],
    queryFn: () => getDashboardAnalytics({ from, to }),
  })

  async function handleExport(format: ExportFormat) {
    setError(null)
    setExporting(format)
    try {
      await exportDepartmentReport(format, { from, to })
    } catch (err) {
      setError(apiErrorMessage(err, "Could not export the report."))
    } finally {
      setExporting(null)
    }
  }

  const data = query.data

  return (
    <div className="flex flex-col gap-5">
      <div className="flex flex-wrap items-center gap-2">
        <select aria-label="Date range" value={preset} onChange={(event) => setPreset(event.target.value as DatePreset)} className={selectClass}>
          <option value="7">Last 7 days</option>
          <option value="30">Last 30 days</option>
          <option value="90">Last 90 days</option>
          <option value="all">All time</option>
        </select>
        <div className="ml-auto flex gap-2">
          <button onClick={() => handleExport("pdf")} disabled={exporting !== null || !data} className="rounded-md border border-border px-3 py-2 text-xs font-semibold disabled:opacity-60">
            {exporting === "pdf" ? "Exporting…" : "Export PDF"}
          </button>
          <button onClick={() => handleExport("xlsx")} disabled={exporting !== null || !data} className="rounded-md border border-border px-3 py-2 text-xs font-semibold disabled:opacity-60">
            {exporting === "xlsx" ? "Exporting…" : "Export Excel"}
          </button>
        </div>
      </div>

      {error && <p className="text-xs text-destructive">{error}</p>}

      {query.isLoading || !data ? (
        <p className="text-sm text-muted-foreground">Loading…</p>
      ) : (
        <>
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
            <Card title="Status Breakdown" subtitle={`Created ${from} to ${to}, plus the live overdue count`}>
              <StatusBreakdownTable statusDistribution={data.statusDistribution} overdue={data.summary.overdue} />
            </Card>
          </div>

          <div className="grid grid-cols-1 gap-5 lg:grid-cols-3">
            <Card title="Task Status Distribution" subtitle={`${from} to ${to}`}>
              <table className="w-full text-xs">
                <tbody>
                  {data.statusDistribution.map((s) => (
                    <tr key={s.status} className="border-t border-border first:border-0">
                      <td className="py-1.5">{label(s.status)}</td>
                      <td className="py-1.5 text-right font-semibold">{s.count}</td>
                      <td className="py-1.5 pl-2 text-right text-muted-foreground">{s.percent}%</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </Card>
            <Card title="Priority Distribution" subtitle="All currently-open tasks">
              <table className="w-full text-xs">
                <tbody>
                  {data.priorityDistribution.map((p) => (
                    <tr key={p.priority} className="border-t border-border first:border-0">
                      <td className="py-1.5">{label(p.priority)}</td>
                      <td className="py-1.5 text-right font-semibold">{p.count}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </Card>
            <Card title="Category Distribution" subtitle={`${from} to ${to}`}>
              <table className="w-full text-xs">
                <tbody>
                  {data.categoryDistribution.length === 0 && <tr><td className="py-1.5 text-muted-foreground">None</td></tr>}
                  {data.categoryDistribution.map((c) => (
                    <tr key={c.categoryId} className="border-t border-border first:border-0">
                      <td className="py-1.5">{c.categoryName}</td>
                      <td className="py-1.5 text-right font-semibold">{c.count}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </Card>
          </div>

          <div className="grid grid-cols-1 gap-5 lg:grid-cols-2">
            <Card title="Team Workload" subtitle="Active tasks per employee (live)">
              <table className="w-full text-xs">
                <tbody>
                  {data.teamWorkload.map((e) => (
                    <tr key={e.employeeId} className="border-t border-border first:border-0">
                      <td className="py-1.5">{e.employeeName}</td>
                      <td className="py-1.5 text-right font-semibold">{e.count}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </Card>
            <Card title="Employee Completion" subtitle={`${from} to ${to}`}>
              <table className="w-full text-xs">
                <tbody>
                  {data.employeeCompletion.map((e) => (
                    <tr key={e.employeeId} className="border-t border-border first:border-0">
                      <td className="py-1.5">{e.employeeName}</td>
                      <td className="py-1.5 text-right font-semibold">{e.count}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </Card>
          </div>

          <div className="grid grid-cols-1 gap-5 lg:grid-cols-2">
            <Card title="Task Completion Trend" subtitle="Completions per week - last 8 weeks">
              <table className="w-full text-xs">
                <tbody>
                  {data.completionTrend.map((w) => (
                    <tr key={w.weekStart} className="border-t border-border first:border-0">
                      <td className="py-1.5">{w.weekLabel}</td>
                      <td className="py-1.5 text-right font-semibold">{w.count}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </Card>
            <Card title="Overdue Trend" subtitle="Current backlog by due-date week">
              <table className="w-full text-xs">
                <tbody>
                  {data.overdueTrend.map((w) => (
                    <tr key={w.weekStart} className="border-t border-border first:border-0">
                      <td className="py-1.5">{w.weekLabel}</td>
                      <td className="py-1.5 text-right font-semibold">{w.count}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </Card>
          </div>

          <div className="grid grid-cols-1 gap-5 lg:grid-cols-2">
            <Card title="Points Leaderboard" subtitle={`Efficiency = points earned ÷ points possible, ${from} to ${to}`}>
              <table className="w-full text-xs">
                <thead>
                  <tr className="text-left text-[11px] uppercase tracking-wide text-muted-foreground">
                    <th className="pb-1.5 font-semibold">Employee</th>
                    <th className="pb-1.5 text-right font-semibold">Earned</th>
                    <th className="pb-1.5 text-right font-semibold">Possible</th>
                    <th className="pb-1.5 text-right font-semibold">Efficiency</th>
                  </tr>
                </thead>
                <tbody>
                  {data.pointsLeaderboard.map((e) => (
                    <tr key={e.employeeId} className="border-t border-border">
                      <td className="py-1.5">{e.employeeName}</td>
                      <td className="py-1.5 text-right font-semibold">{e.pointsEarned}</td>
                      <td className="py-1.5 text-right text-muted-foreground">{e.pointsPossible}</td>
                      <td className="py-1.5 text-right font-semibold">{e.efficiencyRate}%</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </Card>
            <Card title="Points Impact by Deadline Extensions" subtitle={`Org-wide, ${from} to ${to} - open tasks included at their current standing`}>
              <table className="w-full text-xs">
                <tbody>
                  {data.strikeDistribution.map((s) => (
                    <tr key={s.level} className="border-t border-border first:border-0">
                      <td className="py-1.5">{STRIKE_LABELS[s.level] ?? s.level}</td>
                      <td className="py-1.5 text-right font-semibold">{s.count}</td>
                      <td className="py-1.5 pl-2 text-right text-muted-foreground">{s.percent}%</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </Card>
          </div>
        </>
      )}
    </div>
  )
}

const STRIKE_LABELS: Record<string, string> = { "0": "No extensions — full points", "1": "1 extension — 50%", "2": "2 extensions — 25%", FAILED: "Failed (3rd extension)" }
