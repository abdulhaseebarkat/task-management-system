import { useState, type FormEvent } from "react"
import type { AssignmentUpdate, Assignee, DueDateChange, ReassignmentRequest, Task, TaskAssignment } from "@/types/tasks"
import { AdminActions } from "./AdminActions"
import { AssignmentForm } from "./AssignmentForm"
import { badgeClass, isTaskOverdue, label, OVERDUE_BADGE_CLASS } from "./taskDisplay"
import { TaskComments } from "./TaskComments"
import { TaskHistoryPanels } from "./TaskHistoryPanels"
import { TaskPointsPanel } from "./TaskPointsPanel"

interface TaskDetailProps {
  task: Task | null
  employee: boolean
  userId?: number
  /** Deep-linked from an Individual Report's Point Events list - auto-expands that one assignment's points panel. */
  openAssignmentId?: number
  error: string | null
  isSaving: boolean
  onEdit: (task: Task) => void
  onCancel: (task: Task) => void
  onReopen: (task: Task, keepProgress: boolean) => void
  onReinstate: (task: Task) => void
  onAssignmentUpdate: (taskId: number, update: AssignmentUpdate) => void
  onAdminAdjust: (taskId: number, assignmentId: number, update: AssignmentUpdate) => void
  onFailAssignment: (taskId: number, assignmentId: number, reason: string) => void
  teamMembers: Assignee[]
  onDueDateChange: (taskId: number, change: DueDateChange) => void
  onReassign: (taskId: number, request: ReassignmentRequest) => void
}

/** Identifies an assignment's saved values; the Admin's adjust panel hides itself once these change. */
function snapshotOf(assignment: TaskAssignment): string {
  return `${assignment.status}:${assignment.progress}:${assignment.reason ?? ""}`
}

export function TaskDetail({ task, employee, userId, openAssignmentId, error, isSaving, onEdit, onCancel, onReopen, onReinstate, onAssignmentUpdate, onAdminAdjust, onFailAssignment, teamMembers, onDueDateChange, onReassign }: TaskDetailProps) {
  const [adjusting, setAdjusting] = useState<{ id: number; snapshot: string } | null>(null)
  const [failingId, setFailingId] = useState<number | null>(null)
  const [failReason, setFailReason] = useState("")

  if (!task) {
    return <aside className="rounded-lg border border-dashed border-border p-6 text-sm text-muted-foreground">Select a task to view its details.</aside>
  }

  const own = task.assignments.find((assignment) => assignment.user.id === userId)
  const cancelled = task.status === "CANCELLED"

  return (
    <aside className="flex flex-col gap-4 rounded-lg border border-border bg-card p-5">
      <div>
        <div className="text-xs font-semibold text-muted-foreground">{task.taskNumber}</div>
        <h2 className="mt-1 text-lg font-bold">{task.title}</h2>
        <span className={`mt-2 inline-block rounded-full px-2 py-1 text-[11px] font-semibold ${badgeClass(task.status)}`}>{label(task.status)}</span>
      </div>

      <p className="whitespace-pre-wrap text-sm text-muted-foreground">{task.description || "No description provided."}</p>

      <dl className="grid grid-cols-2 gap-3 text-xs">
        <div>
          <dt className="text-muted-foreground">Priority</dt>
          <dd className={`mt-1 inline-block rounded-full px-2 py-1 font-semibold ${badgeClass(task.priority)}`}>{label(task.priority)}</dd>
        </div>
        <div>
          <dt className="text-muted-foreground">Category</dt>
          <dd className="mt-1 font-semibold">{task.category.name}</dd>
        </div>
        <div>
          <dt className="text-muted-foreground">Due date</dt>
          <dd className={`mt-1 flex items-center gap-1.5 font-semibold ${isTaskOverdue(task) ? "text-destructive" : ""}`}>
            {task.dueDate ?? "-"}
            {isTaskOverdue(task) && <span className={OVERDUE_BADGE_CLASS}>Overdue</span>}
          </dd>
        </div>
        <div>
          <dt className="text-muted-foreground">Overall progress</dt>
          <dd className="mt-1 font-semibold">{task.overallProgress}%</dd>
        </div>
      </dl>

      <div>
        <p className="mb-2 text-xs font-semibold text-secondary-foreground">Assignments</p>
        {task.assignments.length ? (
          <ul className="flex flex-col gap-3">
            {task.assignments.map((assignment) => {
              const showAdjust = adjusting?.id === assignment.id && adjusting.snapshot === snapshotOf(assignment)
              const showFail = failingId === assignment.id
              const canFail = !employee && task.status !== "CANCELLED" && task.status !== "COMPLETED" && assignment.status !== "COMPLETED"
              const submitFail = (event: FormEvent) => {
                event.preventDefault()
                onFailAssignment(task.id, assignment.id, failReason)
                setFailingId(null)
                setFailReason("")
              }
              return (
                <li key={assignment.id} className="text-xs">
                  <div className="flex items-center justify-between gap-2">
                    <span>{assignment.user.name}</span>
                    <span className="flex items-center gap-2">
                      <span className={`rounded-full px-2 py-1 font-semibold ${badgeClass(assignment.status)}`}>
                        {assignment.progress}% · {label(assignment.status)}
                      </span>
                      {!employee && !cancelled && (
                        <button
                          onClick={() => setAdjusting(showAdjust ? null : { id: assignment.id, snapshot: snapshotOf(assignment) })}
                          className="font-semibold text-primary"
                        >
                          Adjust
                        </button>
                      )}
                      {canFail && (
                        <button
                          onClick={() => { setFailingId(showFail ? null : assignment.id); setFailReason("") }}
                          className="font-semibold text-destructive"
                        >
                          Fail
                        </button>
                      )}
                    </span>
                  </div>
                  {assignment.reason && <p className="mt-1 text-muted-foreground">Reason: {assignment.reason}</p>}
                  {(!employee || assignment.user.id === userId) && (
                    <div className="mt-1.5">
                      <TaskPointsPanel taskId={task.id} assignmentId={assignment.id} autoOpen={openAssignmentId === assignment.id} />
                    </div>
                  )}
                  {showAdjust && (
                    <div className="mt-3">
                      <AssignmentForm
                        key={snapshotOf(assignment)}
                        assignment={assignment}
                        heading={`Adjust ${assignment.user.name}'s work`}
                        isSaving={isSaving}
                        onSubmit={(update) => onAdminAdjust(task.id, assignment.id, update)}
                        onClose={() => setAdjusting(null)}
                      />
                    </div>
                  )}
                  {showFail && (
                    <form onSubmit={submitFail} className="mt-3 grid gap-2 rounded-md border border-destructive/40 p-3">
                      <p className="font-semibold text-destructive">Mark {assignment.user.name}'s work as failed</p>
                      <p className="text-muted-foreground">This permanently zeroes out their points for this task, same as a 3rd missed deadline. Their status/progress are not otherwise changed.</p>
                      <label className="grid gap-1">Reason<input required maxLength={255} value={failReason} onChange={(event) => setFailReason(event.target.value)} className="rounded border border-input bg-background p-2" /></label>
                      <div className="flex gap-2">
                        <button disabled={isSaving || !failReason.trim()} className="rounded bg-destructive px-3 py-2 font-semibold text-destructive-foreground disabled:opacity-60">Confirm fail</button>
                        <button type="button" onClick={() => setFailingId(null)} className="rounded border border-border px-3 py-2 font-semibold">Cancel</button>
                      </div>
                    </form>
                  )}
                </li>
              )
            })}
          </ul>
        ) : (
          <p className="text-xs text-muted-foreground">No team members assigned.</p>
        )}
      </div>

      {employee && own && <EmployeeSection task={task} own={own} isSaving={isSaving} onSubmit={(update) => onAssignmentUpdate(task.id, update)} />}

      {error && <p className="text-xs text-destructive">{error}</p>}

      {!employee && <AdminActions key={`${task.id}:${task.status}`} task={task} onEdit={onEdit} onCancel={onCancel} onReopen={onReopen} onReinstate={onReinstate} />}
      <TaskHistoryPanels
        key={`${task.id}:${task.status}:${task.dueDate}`}
        task={task}
        employee={employee}
        teamMembers={teamMembers}
        busy={isSaving}
        onDueDateChange={(change) => onDueDateChange(task.id, change)}
        onReassign={(request) => onReassign(task.id, request)}
      />
      <TaskComments key={task.id} taskId={task.id} />
    </aside>
  )
}

function EmployeeSection({ task, own, isSaving, onSubmit }: { task: Task; own: TaskAssignment; isSaving: boolean; onSubmit: (update: AssignmentUpdate) => void }) {
  if (task.status === "CANCELLED") {
    return <p className="border-t border-border pt-4 text-xs text-muted-foreground">This task was cancelled, so it can no longer be updated.</p>
  }
  if (own.status === "COMPLETED") {
    return <p className="border-t border-border pt-4 text-xs text-muted-foreground">You have completed your part. Ask an Admin to reopen the task if it needs more work.</p>
  }
  return <AssignmentForm key={snapshotOf(own)} assignment={own} heading="Update your assignment" isSaving={isSaving} onSubmit={onSubmit} />
}
