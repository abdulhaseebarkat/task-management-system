import { useMemo, useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { Link } from "react-router-dom"
import { exportEmployeeReport, getEmployeeReport, type ExportFormat } from "@/api/reports"
import { badgeClass, label, OVERDUE_BADGE_CLASS } from "@/components/tasks/taskDisplay"
import { breakdownLabel, DivergingPointsBar, efficiencyTone, MonthlyRateBars, pointEventLabel, strikeLevelDotColor } from "@/components/reports/PointsCharts"
import { AssignedVsCompletedChart, StatusBreakdownTable } from "@/components/reports/TaskCharts"
import { useAuth } from "@/auth/AuthContext"
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

function formatWhen(iso: string): string {
  return new Date(iso).toLocaleString(undefined, { month: "short", day: "numeric", hour: "2-digit", minute: "2-digit" })
}

/** Every row in currentWorkload is already an open assignment by construction, so overdue is just a date check. */
function isWorkloadItemOverdue(dueDate: string | null): boolean {
  return !!dueDate && dueDate < new Date().toISOString().slice(0, 10)
}

/** Shared by the Admin Reports page (with an employee picker around it) and an employee's own "My Report" page. */
export function IndividualReportView({ employeeId }: { employeeId: number }) {
  const { user } = useAuth()
  const viewingOwnReport = user?.id === employeeId
  const tasksBasePath = user?.role === "ADMIN" ? "/admin/tasks" : "/employee/tasks"
  const [preset, setPreset] = useState<DatePreset>("30")
  const { from, to } = useMemo(() => presetRange(preset), [preset])
  const [error, setError] = useState<string | null>(null)
  const [exporting, setExporting] = useState<ExportFormat | null>(null)

  const query = useQuery({
    queryKey: ["reports", "employee", employeeId, from, to],
    queryFn: () => getEmployeeReport(employeeId, { from, to }),
  })

  async function handleExport(format: ExportFormat) {
    setError(null)
    setExporting(format)
    try {
      await exportEmployeeReport(employeeId, format, { from, to })
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
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-5">
            <Tile label="Assigned" value={data.summary.assigned} />
            <Tile label="Completed" value={data.summary.completed} tone="text-success" />
            <Tile label="Active" value={data.summary.active} tone="text-primary" />
            <Tile label="Overdue" value={data.summary.overdue} tone={data.summary.overdue > 0 ? "text-destructive" : undefined} />
            <Tile label="Blocked" value={data.summary.blocked} tone={data.summary.blocked > 0 ? "text-serious" : undefined} />
            <Tile label="On Hold" value={data.summary.onHold} tone={data.summary.onHold > 0 ? "text-warning" : undefined} />
            <Tile label="Cancelled" value={data.summary.cancelled} />
            <Tile label="Reassigned" value={data.summary.reassignedAway} />
            <Tile label="Completion Rate" value={`${data.summary.completionRate}%`} tone="text-success" />
            <Tile label="Avg. Completion Time" value={data.summary.avgCompletionDays == null ? "—" : `${data.summary.avgCompletionDays}d`} />
          </div>

          {data.pointsSummary && (
            <div>
              <div className="flex items-center gap-2.5">
                <h2 className="text-base font-bold">Points &amp; Performance</h2>
                <span className="text-[10px] text-muted-foreground">
                  {viewingOwnReport ? "Visible only to you and the Admin" : `Visible only to the Admin and ${data.employeeName.split(" ")[0]}`}
                </span>
              </div>

              <div className="mt-3 grid grid-cols-2 gap-3 sm:grid-cols-5">
                <Tile label="Points Earned" value={data.pointsSummary.pointsEarned} />
                <Tile label="Points Possible" value={data.pointsSummary.pointsPossible} />
                <Tile label="Points Lost" value={data.pointsSummary.pointsLost} tone={data.pointsSummary.pointsLost > 0 ? "text-destructive" : undefined} />
                <Tile label="Efficiency Rate" value={`${data.pointsSummary.efficiencyRate}%`} tone={efficiencyTone(data.pointsSummary.efficiencyRate)} />
                <Tile label="Tasks Failed" value={data.pointsSummary.tasksFailed} tone={data.pointsSummary.tasksFailed > 0 ? "text-destructive" : undefined} />
              </div>

              <div className="mt-5 grid grid-cols-1 gap-5 lg:grid-cols-[1fr_0.85fr_1.25fr]">
                <Card title="Points Retained vs Lost" subtitle="Live standing this window">
                  <DivergingPointsBar retained={data.pointsSummary.pointsEarned} lost={data.pointsSummary.pointsLost} />
                  <div className="mt-3 border-t border-border pt-3.5 text-center text-xs text-muted-foreground">
                    {data.pointsSummary.pointsEarned} earned · {data.pointsSummary.pointsLost} lost
                  </div>
                </Card>
                <Card title="Points Breakdown" subtitle="This window, by outcome - open tasks included at their current standing">
                  <table className="w-full text-xs">
                    <tbody>
                      {data.pointsBreakdown.map((row) => (
                        <tr key={row.level} className="border-t border-border first:border-0">
                          <td className="py-2">
                            <span className="mr-2 inline-block h-2 w-2 rounded-full" style={{ backgroundColor: strikeLevelDotColor(row.level) }} />
                            {breakdownLabel(row.level)}
                          </td>
                          <td className="py-2 text-right font-semibold">{row.count}</td>
                          <td className="py-2 pl-2 text-right text-muted-foreground">{row.percent}%</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </Card>
                <Card title="Monthly Points Trend" subtitle="Efficiency rate per month, last 6 months">
                  <MonthlyRateBars months={data.monthlyPointsTrend} />
                </Card>
              </div>

              <Card title="Point Events" subtitle="Every strike, failure, and award — chronological, permanent once recorded">
                {data.pointEvents.length === 0 ? (
                  <p className="text-xs text-muted-foreground">No points activity yet.</p>
                ) : (
                  <ul className="flex flex-col">
                    {data.pointEvents.map((item, i) => {
                      const badge = pointEventLabel(item.eventType)
                      return (
                        <li key={i} className="flex items-center justify-between gap-3 border-t border-border py-2.5 first:border-0">
                          <Link to={`${tasksBasePath}?taskId=${item.taskId}&assignmentId=${item.assignmentId}`} className="min-w-0 truncate text-xs">
                            <span className="font-semibold">{item.taskNumber}</span> <span className="text-muted-foreground">· {item.title}</span>
                          </Link>
                          <div className="flex flex-shrink-0 items-center gap-2.5">
                            <span className="text-[11px] text-muted-foreground">{formatWhen(item.occurredAt)}</span>
                            <span className={`rounded-full bg-accent px-2 py-1 text-[11px] font-bold ${badge.tone}`}>{badge.text} · {item.resultingPoints} pts</span>
                          </div>
                        </li>
                      )
                    })}
                  </ul>
                )}
              </Card>

              <Card title="Individual Tasks" subtitle="Every task in this window, with its due-date history and points">
                {data.taskDetails.length === 0 ? (
                  <p className="text-xs text-muted-foreground">No tasks in this window.</p>
                ) : (
                  <table className="w-full text-xs">
                    <thead>
                      <tr className="text-left text-[11px] uppercase tracking-wide text-muted-foreground">
                        <th className="pb-2 pr-2 font-semibold">Task</th>
                        <th className="pb-2 pr-2 font-semibold">Priority</th>
                        <th className="pb-2 pr-2 font-semibold">Assigned</th>
                        <th className="pb-2 pr-2 font-semibold">Status</th>
                        <th className="pb-2 pr-2 font-semibold">Due dates</th>
                        <th className="pb-2 pr-2 text-right font-semibold">Possible</th>
                        <th className="pb-2 text-right font-semibold">Deducted</th>
                      </tr>
                    </thead>
                    <tbody>
                      {data.taskDetails.map((item, i) => (
                        <tr key={`${item.taskId}-${item.assignedAt}-${i}`} className="border-t border-border align-top">
                          <td className="py-2 pr-2">
                            <Link to={`${tasksBasePath}?taskId=${item.taskId}`} className="block min-w-0">
                              <div className="font-medium">{item.title}</div>
                              <div className="text-muted-foreground">{item.taskNumber}</div>
                            </Link>
                          </td>
                          <td className="py-2 pr-2"><span className={`rounded-full px-2 py-1 font-semibold ${badgeClass(item.priority)}`}>{label(item.priority)}</span></td>
                          <td className="py-2 pr-2 text-muted-foreground">{item.assignedAt.slice(0, 10)}</td>
                          <td className="py-2 pr-2"><span className={`rounded-full px-2 py-1 font-semibold ${badgeClass(item.status)}`}>{label(item.status)}</span></td>
                          <td className="py-2 pr-2">
                            {item.dueDates.length === 0 ? (
                              <span className="text-muted-foreground">—</span>
                            ) : (
                              <>
                                <div>{item.dueDates[0].dueDate}</div>
                                {item.dueDates.slice(1).map((ext, j) => (
                                  <div key={j} className="text-muted-foreground">
                                    → {ext.dueDate}{!ext.countedTowardStrikes && " (waived)"}
                                  </div>
                                ))}
                              </>
                            )}
                          </td>
                          <td className="py-2 pr-2 text-right font-semibold">{item.possiblePoints}</td>
                          <td className={`py-2 text-right font-semibold ${item.deductedPoints > 0 ? "text-destructive" : "text-muted-foreground"}`}>
                            {item.deductedPoints > 0 ? item.deductedPoints : "—"}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                )}
              </Card>
            </div>
          )}

          <div className="grid grid-cols-1 gap-5 lg:grid-cols-3">
            <Card title="Tasks by Status" subtitle={`Created ${from} to ${to}`}>
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
            <Card title="Tasks by Priority" subtitle={`Created ${from} to ${to}`}>
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
            <Card title="Tasks by Category" subtitle={`Created ${from} to ${to}`}>
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

          <div className="grid grid-cols-1 gap-5 lg:grid-cols-[1.4fr_1fr]">
            <Card title="Tasks Assigned vs. Completed" subtitle={`Created ${from} to ${to}`}>
              <AssignedVsCompletedChart rows={[{ label: "This period", assigned: data.summary.assigned, completed: data.summary.completed }]} />
            </Card>
            <Card title="Status Breakdown" subtitle={`Created ${from} to ${to}, plus the live overdue count`}>
              <StatusBreakdownTable statusDistribution={data.statusDistribution} overdue={data.summary.overdue} />
            </Card>
          </div>

          <Card title="Monthly Completion Trend" subtitle="Last 6 months">
            <div className="flex items-end gap-3">
              {data.completionTrend.map((m) => {
                const max = Math.max(...data.completionTrend.map((x) => x.count), 1)
                return (
                  <div key={m.monthStart} className="flex flex-1 flex-col items-center gap-1.5">
                    <span className="text-xs font-bold">{m.count}</span>
                    <div className="w-full rounded bg-primary" style={{ height: `${Math.max((m.count / max) * 80, m.count > 0 ? 6 : 2)}px` }} />
                    <span className="text-[10px] text-muted-foreground">{m.monthLabel}</span>
                  </div>
                )
              })}
            </div>
          </Card>

          <Card title="Current Workload" subtitle="Live - not date-bound">
            {data.currentWorkload.length === 0 ? (
              <p className="text-xs text-muted-foreground">Nothing currently assigned.</p>
            ) : (
              <table className="w-full text-xs">
                <thead>
                  <tr className="text-left text-[11px] uppercase tracking-wide text-muted-foreground">
                    <th className="pb-2 font-semibold">Task</th>
                    <th className="pb-2 font-semibold">Priority</th>
                    <th className="pb-2 font-semibold">Status</th>
                    <th className="pb-2 font-semibold">Progress</th>
                    <th className="pb-2 font-semibold">Due</th>
                  </tr>
                </thead>
                <tbody>
                  {data.currentWorkload.map((w) => (
                    <tr key={w.taskId} className="border-t border-border">
                      <td className="py-2 pr-2">
                        <div className="font-medium">{w.title}</div>
                        <div className="text-muted-foreground">{w.taskNumber}</div>
                      </td>
                      <td className="py-2 pr-2"><span className={`rounded-full px-2 py-1 font-semibold ${badgeClass(w.priority)}`}>{label(w.priority)}</span></td>
                      <td className="py-2 pr-2"><span className={`rounded-full px-2 py-1 font-semibold ${badgeClass(w.status)}`}>{label(w.status)}</span></td>
                      <td className="py-2 pr-2">{w.progress}%</td>
                      <td className={`py-2 ${isWorkloadItemOverdue(w.dueDate) ? "text-destructive" : "text-muted-foreground"}`}>
                        {w.dueDate ?? "—"}
                        {isWorkloadItemOverdue(w.dueDate) && <span className={`ml-1.5 ${OVERDUE_BADGE_CLASS}`}>Overdue</span>}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </Card>

          <div className="grid grid-cols-1 gap-5 lg:grid-cols-2">
            <Card title="Reassigned Away" subtitle={`${from} to ${to}`}>
              {data.reassignedTasks.length === 0 ? (
                <p className="text-xs text-muted-foreground">No reassignments in this window.</p>
              ) : (
                <ul className="flex flex-col gap-2 text-xs">
                  {data.reassignedTasks.map((item) => (
                    <li key={`${item.taskId}-${item.reassignedAt}`} className="border-t border-border pt-2 first:border-0 first:pt-0">
                      <div className="font-medium">{item.taskNumber} · {item.title}</div>
                      <div className="text-muted-foreground">To {item.toUserName} · {item.reason} · {formatWhen(item.reassignedAt)}</div>
                    </li>
                  ))}
                </ul>
              )}
            </Card>
            <Card title="Recent Activity" subtitle="Last 10 events">
              {data.recentActivity.length === 0 ? (
                <p className="text-xs text-muted-foreground">No activity yet.</p>
              ) : (
                <ul className="flex flex-col gap-2 text-xs">
                  {data.recentActivity.map((item, i) => (
                    <li key={i} className="border-t border-border pt-2 first:border-0 first:pt-0">
                      <div className="font-medium">{item.taskNumber} · {item.title}</div>
                      <div className="text-muted-foreground">
                        {item.oldStatus ? `${label(item.oldStatus)} → ` : ""}{label(item.newStatus)} · {formatWhen(item.occurredAt)}
                        {item.comment ? ` · ${item.comment}` : ""}
                      </div>
                    </li>
                  ))}
                </ul>
              )}
            </Card>
          </div>
        </>
      )}
    </div>
  )
}
