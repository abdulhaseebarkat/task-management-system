import { useQuery } from "@tanstack/react-query"
import { Link } from "react-router-dom"
import { AppLayout } from "@/components/layout/AppLayout"
import { badgeClass, isTaskOverdue, label, OPEN_TASK_STATUSES, OVERDUE_BADGE_CLASS } from "@/components/tasks/taskDisplay"
import { listTasks } from "@/api/tasks"
import { getEmployeeSummary } from "@/api/dashboard"
import { useAuth } from "@/auth/AuthContext"
import type { AssignmentStatus } from "@/types/tasks"

const ACTIVE_STATUSES = new Set(["ASSIGNED", "IN_PROGRESS", "REOPENED"])

const DONUT_COLORS: Record<string, string> = {
  COMPLETED: "#16a34a",
  IN_PROGRESS: "#d97706",
  BLOCKED: "#dc2626",
  ON_HOLD: "#64748b",
  ASSIGNED: "#2563eb",
}
const DONUT_ORDER: AssignmentStatus[] = ["COMPLETED", "IN_PROGRESS", "BLOCKED", "ON_HOLD", "ASSIGNED"]

function Tile({ label: text, value, tone }: { label: string; value: number; tone?: string }) {
  return (
    <div className="rounded-lg border border-border bg-card px-4 py-3.5">
      <div className="text-[11px] font-semibold uppercase tracking-wide text-muted-foreground">{text}</div>
      <div className={`mt-1.5 text-2xl font-bold ${tone ?? ""}`}>{value}</div>
    </div>
  )
}

function StatusDonut({ counts }: { counts: Record<string, number> }) {
  const total = Object.values(counts).reduce((a, b) => a + b, 0)
  const radius = 40
  const circumference = 2 * Math.PI * radius
  let offset = 0
  const segments = DONUT_ORDER.filter((status) => counts[status] > 0)

  return (
    <div className="flex items-center gap-4">
      <svg width="104" height="104" viewBox="0 0 104 104" className="-rotate-90">
        <circle cx="52" cy="52" r={radius} fill="none" stroke="var(--border)" strokeWidth="14" />
        {total > 0 &&
          segments.map((status) => {
            const length = (counts[status] / total) * circumference
            const el = (
              <circle
                key={status}
                cx="52"
                cy="52"
                r={radius}
                fill="none"
                stroke={DONUT_COLORS[status]}
                strokeWidth="14"
                strokeDasharray={`${length} ${circumference - length}`}
                strokeDashoffset={-offset}
              />
            )
            offset += length
            return el
          })}
      </svg>
      <ul className="flex flex-col gap-1.5 text-xs">
        {total === 0 && <li className="text-muted-foreground">No active assignments yet.</li>}
        {segments.map((status) => (
          <li key={status} className="flex items-center gap-1.5">
            <span className="h-2 w-2 rounded-full" style={{ backgroundColor: DONUT_COLORS[status] }} />
            <span>{label(status)} · {counts[status]}</span>
          </li>
        ))}
      </ul>
    </div>
  )
}

export function EmployeeDashboardPage() {
  const { user } = useAuth()
  const { data, isLoading } = useQuery({
    queryKey: ["tasks", "list", "dashboard"],
    queryFn: () => listTasks({ page: 0, size: 100, sort: "due", direction: "asc" }),
  })
  const { data: summary } = useQuery({
    queryKey: ["dashboard", "employee-summary"],
    queryFn: getEmployeeSummary,
  })

  const tasks = data?.content ?? []
  const overdue = tasks.filter(isTaskOverdue)
  const upcoming = tasks
    .filter((t) => t.dueDate && OPEN_TASK_STATUSES.has(t.status) && !isTaskOverdue(t))
    .sort((a, b) => (a.dueDate ?? "").localeCompare(b.dueDate ?? ""))
    .slice(0, 5)
  const myTasksPreview = tasks.slice(0, 5)

  const counts = {
    total: tasks.length,
    active: tasks.filter((t) => ACTIVE_STATUSES.has(t.status)).length,
    completed: tasks.filter((t) => t.status === "COMPLETED").length,
    blocked: tasks.filter((t) => t.status === "BLOCKED").length,
    onHold: tasks.filter((t) => t.status === "ON_HOLD").length,
    overdue: overdue.length,
    reassignedAway: summary?.reassignedAway ?? 0,
  }

  const myStatusCounts: Record<string, number> = {}
  for (const task of tasks) {
    const mine = task.assignments.find((a) => a.user.id === user?.id)
    if (!mine) continue
    myStatusCounts[mine.status] = (myStatusCounts[mine.status] ?? 0) + 1
  }

  return (
    <AppLayout title="My Dashboard" subtitle={`Welcome back, ${user?.name.split(" ")[0] ?? ""} — here's what's on your plate`}>
      {isLoading ? (
        <p className="text-sm text-muted-foreground">Loading…</p>
      ) : (
        <div className="flex flex-col gap-6">
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-4 lg:grid-cols-7">
            <Tile label="Total Assigned" value={counts.total} />
            <Tile label="Active" value={counts.active} tone="text-primary" />
            <Tile label="Completed" value={counts.completed} tone="text-success" />
            <Tile label="Overdue" value={counts.overdue} tone={counts.overdue > 0 ? "text-destructive" : undefined} />
            <Tile label="Blocked" value={counts.blocked} tone={counts.blocked > 0 ? "text-serious" : undefined} />
            <Tile label="On Hold" value={counts.onHold} tone={counts.onHold > 0 ? "text-warning" : undefined} />
            <Tile label="Reassigned" value={counts.reassignedAway} />
          </div>

          <div className="grid grid-cols-1 gap-6 lg:grid-cols-[1.6fr_1fr]">
            <div className="rounded-lg border border-border bg-card p-4">
              <div className="mb-3 flex items-center justify-between">
                <p className="text-sm font-bold">My Tasks</p>
                <Link to="/employee/tasks" className="text-xs font-semibold text-primary">View all →</Link>
              </div>
              {myTasksPreview.length === 0 ? (
                <p className="text-xs text-muted-foreground">No tasks assigned yet.</p>
              ) : (
                <table className="w-full text-xs">
                  <thead>
                    <tr className="text-left text-[11px] uppercase tracking-wide text-muted-foreground">
                      <th className="pb-2 font-semibold">Task</th>
                      <th className="pb-2 font-semibold">Priority</th>
                      <th className="pb-2 font-semibold">Progress</th>
                      <th className="pb-2 font-semibold">Status</th>
                      <th className="pb-2 font-semibold">Due</th>
                    </tr>
                  </thead>
                  <tbody>
                    {myTasksPreview.map((task) => (
                      <tr key={task.id} className="border-t border-border">
                        <td className="py-2 pr-2">
                          <div className="font-medium">{task.title}</div>
                          <div className="text-muted-foreground">
                            {task.taskNumber}
                            {task.assignments.length > 1 &&
                              ` · Shared with ${task.assignments.length - 1} other${task.assignments.length > 2 ? "s" : ""}`}
                          </div>
                        </td>
                        <td className="py-2 pr-2">{label(task.priority)}</td>
                        <td className="py-2 pr-2">{task.overallProgress}%</td>
                        <td className="py-2 pr-2">
                          <span className={`rounded-full px-2 py-1 font-semibold ${badgeClass(task.status)}`}>{label(task.status)}</span>
                        </td>
                        <td className={`py-2 ${isTaskOverdue(task) ? "text-destructive" : "text-muted-foreground"}`}>
                          {task.dueDate ?? "—"}
                          {isTaskOverdue(task) && <span className={`ml-1.5 ${OVERDUE_BADGE_CLASS}`}>Overdue</span>}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </div>

            <div className="flex flex-col gap-6">
              <div className="rounded-lg border border-border bg-card p-4">
                <p className="mb-3 text-sm font-bold">Upcoming Deadlines</p>
                {overdue.length === 0 && upcoming.length === 0 ? (
                  <p className="text-xs text-muted-foreground">Nothing due soon.</p>
                ) : (
                  <ul className="flex flex-col gap-2">
                    {overdue.map((task) => (
                      <li key={task.id} className="flex items-center justify-between text-xs">
                        <span>{task.title} <span className="text-muted-foreground">· {task.taskNumber}</span></span>
                        <span className="rounded-full bg-destructive/10 px-2 py-1 font-semibold text-destructive">Overdue · {task.dueDate}</span>
                      </li>
                    ))}
                    {upcoming.map((task) => (
                      <li key={task.id} className="flex items-center justify-between text-xs">
                        <span>{task.title} <span className="text-muted-foreground">· {task.taskNumber}</span></span>
                        <span className={`rounded-full px-2 py-1 font-semibold ${badgeClass(task.status)}`}>{label(task.status)} · {task.dueDate}</span>
                      </li>
                    ))}
                  </ul>
                )}
              </div>

              <div className="rounded-lg border border-border bg-card p-4">
                <p className="mb-3 text-sm font-bold">My Status Breakdown</p>
                <StatusDonut counts={myStatusCounts} />
              </div>
            </div>
          </div>
        </div>
      )}
    </AppLayout>
  )
}
