import type { PagedResponse, Task } from "@/types/tasks"
import { badgeClass, isTaskOverdue, label, OVERDUE_BADGE_CLASS } from "./taskDisplay"

interface TaskTableProps {
  result: PagedResponse<Task> | undefined
  employee: boolean
  loading: boolean
  selectedId: number | null
  cancelling: boolean
  onSelect: (task: Task) => void
  onCancel: (task: Task) => void
  onPage: (page: number) => void
}

export function TaskTable({ result, employee, loading, selectedId, cancelling, onSelect, onCancel, onPage }: TaskTableProps) {
  const tasks = result?.content ?? []
  const columns = employee ? 5 : 7

  return (
    <div className="overflow-hidden rounded-lg border border-border bg-card">
      <table className="w-full border-collapse text-sm">
        <thead>
          <tr className="border-b border-border text-left text-[11px] font-semibold uppercase tracking-wide text-muted-foreground">
            <th className="px-4 py-3">Task</th>
            <th className="px-4 py-3">Priority</th>
            {!employee && <th className="px-4 py-3">Assigned to</th>}
            <th className="px-4 py-3">Status</th>
            <th className="px-4 py-3">Progress</th>
            <th className="px-4 py-3">Due</th>
            {!employee && <th className="px-4 py-3">Action</th>}
          </tr>
        </thead>
        <tbody>
          {loading && (
            <tr>
              <td colSpan={columns} className="px-4 py-8 text-center text-muted-foreground">Loading tasks...</td>
            </tr>
          )}
          {!loading && tasks.length === 0 && (
            <tr>
              <td colSpan={columns} className="px-4 py-8 text-center text-muted-foreground">No tasks match these filters.</td>
            </tr>
          )}
          {tasks.map((task) => (
            <tr
              key={task.id}
              onClick={() => onSelect(task)}
              className={`cursor-pointer border-b border-border last:border-0 hover:bg-accent/50 ${task.id === selectedId ? "bg-accent/40" : ""}`}
            >
              <td className="px-4 py-3">
                <div className="font-medium">{task.title}</div>
                <div className="text-xs text-muted-foreground">{task.taskNumber} · {task.category.name}</div>
              </td>
              <td className="px-4 py-3">
                <span className={`rounded-full px-2 py-1 text-[11px] font-semibold ${badgeClass(task.priority)}`}>{label(task.priority)}</span>
              </td>
              {!employee && (
                <td className="max-w-40 px-4 py-3 text-xs text-muted-foreground">
                  {task.assignments.length ? (
                    <span title={task.assignments.map((assignment) => assignment.user.name).join(", ")}>
                      {task.assignments.map((assignment) => assignment.user.name).join(", ")}
                    </span>
                  ) : (
                    "Unassigned"
                  )}
                </td>
              )}
              <td className="px-4 py-3">
                <span className={`rounded-full px-2 py-1 text-[11px] font-semibold ${badgeClass(task.status)}`}>{label(task.status)}</span>
              </td>
              <td className="px-4 py-3">
                <div className="flex items-center gap-2">
                  <div className="h-1.5 w-20 rounded-full bg-muted">
                    <div className="h-1.5 rounded-full bg-primary" style={{ width: `${task.overallProgress}%` }} />
                  </div>
                  <span className="text-xs text-muted-foreground">{task.overallProgress}%</span>
                </div>
              </td>
              <td className="px-4 py-3 text-xs">
                {task.dueDate ? (
                  <div className={isTaskOverdue(task) ? "text-destructive" : "text-muted-foreground"}>
                    <div className="font-semibold">{task.dueDate}</div>
                    {isTaskOverdue(task) && <span className={`mt-1 inline-block ${OVERDUE_BADGE_CLASS}`}>Overdue</span>}
                  </div>
                ) : (
                  <span className="text-muted-foreground">-</span>
                )}
              </td>
              {!employee && (
                <td className="px-4 py-3">
                  {task.status !== "CANCELLED" && (
                    <button
                      onClick={(event) => {
                        event.stopPropagation()
                        onCancel(task)
                      }}
                      disabled={cancelling}
                      className="text-xs font-semibold text-destructive disabled:opacity-60"
                    >
                      Cancel
                    </button>
                  )}
                </td>
              )}
            </tr>
          ))}
        </tbody>
      </table>
      {result && result.totalElements > 0 && <Pagination result={result} onPage={onPage} />}
    </div>
  )
}

function Pagination({ result, onPage }: { result: PagedResponse<Task>; onPage: (page: number) => void }) {
  const first = result.page * result.size + 1
  const last = Math.min(first + result.content.length - 1, result.totalElements)
  return (
    <div className="flex items-center justify-between border-t border-border px-4 py-2.5 text-xs text-muted-foreground">
      <span>
        {first}-{last} of {result.totalElements}
      </span>
      <div className="flex items-center gap-2">
        <button onClick={() => onPage(result.page - 1)} disabled={result.page === 0} className="rounded-md border border-border px-2.5 py-1 font-semibold disabled:opacity-40">
          Previous
        </button>
        <span>Page {result.page + 1} of {Math.max(result.totalPages, 1)}</span>
        <button onClick={() => onPage(result.page + 1)} disabled={result.page + 1 >= result.totalPages} className="rounded-md border border-border px-2.5 py-1 font-semibold disabled:opacity-40">
          Next
        </button>
      </div>
    </div>
  )
}
