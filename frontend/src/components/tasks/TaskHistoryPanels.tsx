import { useState, type FormEvent } from "react"
import { useQuery } from "@tanstack/react-query"
import { getDueDateHistory, getReassignmentHistory, getTaskActivity } from "@/api/tasks"
import type { Assignee, DueDateChange, ReassignmentRequest, Task } from "@/types/tasks"

const REASSIGNMENT_REASONS = [
  "Employee unavailable",
  "Employee unable to complete",
  "Workload issue",
  "Skill/resource issue",
  "Priority changed",
  "Management decision",
  "Operational requirement",
  "Other",
]

const CLASSIFICATIONS: { value: ReassignmentRequest["classification"] | ""; label: string }[] = [
  { value: "", label: "No classification" },
  { value: "NEUTRAL_ADMINISTRATIVE", label: "Neutral / Administrative" },
  { value: "PERFORMANCE_RELATED", label: "Performance related" },
  { value: "OPERATIONAL", label: "Operational" },
  { value: "OTHER", label: "Other" },
]

interface Props {
  task: Task
  employee: boolean
  teamMembers: Assignee[]
  busy: boolean
  onDueDateChange: (change: DueDateChange) => void
  onReassign: (request: ReassignmentRequest) => void
}

export function TaskHistoryPanels({ task, employee, teamMembers, busy, onDueDateChange, onReassign }: Props) {
  const [panel, setPanel] = useState<"due" | "reassign" | "history" | "activity" | null>(null)
  const [dueDate, setDueDate] = useState(task.dueDate ?? "")
  const [dueReason, setDueReason] = useState("")
  const [deductPoints, setDeductPoints] = useState(true)
  const reassignable = task.assignments.filter((assignment) => assignment.status !== "COMPLETED")
  const [fromAssignmentId, setFromAssignmentId] = useState(reassignable[0]?.id ?? 0)
  const [toUserId, setToUserId] = useState(0)
  const [reason, setReason] = useState("")
  const [classification, setClassification] = useState<ReassignmentRequest["classification"] | "">("")
  const [keepProgress, setKeepProgress] = useState(false)
  const [deductPointsOnReassign, setDeductPointsOnReassign] = useState(false)

  const dueHistory = useQuery({ queryKey: ["tasks", task.id, "due-date-history"], queryFn: () => getDueDateHistory(task.id), enabled: panel === "history" })
  const reassignmentHistory = useQuery({ queryKey: ["tasks", task.id, "reassignment-history"], queryFn: () => getReassignmentHistory(task.id), enabled: panel === "history" && !employee })
  const activity = useQuery({ queryKey: ["tasks", task.id, "activity"], queryFn: () => getTaskActivity(task.id), enabled: panel === "activity" })
  const candidates = teamMembers.filter((person) => !task.assignments.some((assignment) => assignment.user.id === person.id))

  function submitDueDate(event: FormEvent) {
    event.preventDefault()
    onDueDateChange({ dueDate, reason: dueReason, deductPoints })
    setPanel(null)
    setDeductPoints(true)
  }
  function submitReassignment(event: FormEvent) {
    event.preventDefault()
    onReassign({ fromAssignmentId, toUserId, reason, classification: classification || undefined, keepProgress, deductPoints: deductPointsOnReassign })
    setPanel(null)
    setDeductPointsOnReassign(false)
  }

  return (
    <section className="flex flex-col gap-3 border-t border-border pt-4">
      <div className="flex flex-wrap gap-2">
        {!employee && task.status !== "CANCELLED" && task.status !== "COMPLETED" && <button onClick={() => setPanel(panel === "due" ? null : "due")} className="rounded-md border border-border px-3 py-2 text-xs font-semibold">Change due date</button>}
        {!employee && task.status !== "CANCELLED" && task.status !== "COMPLETED" && <button onClick={() => setPanel(panel === "reassign" ? null : "reassign")} className="rounded-md border border-border px-3 py-2 text-xs font-semibold">Reassign</button>}
        <button onClick={() => setPanel(panel === "history" ? null : "history")} className="rounded-md border border-border px-3 py-2 text-xs font-semibold">History</button>
        <button onClick={() => setPanel(panel === "activity" ? null : "activity")} className="rounded-md border border-border px-3 py-2 text-xs font-semibold">Activity</button>
      </div>

      {panel === "due" && <form onSubmit={submitDueDate} className="grid gap-2 rounded-md border border-border p-3 text-xs">
        <label className="grid gap-1">New due date<input required type="date" value={dueDate} onChange={(event) => setDueDate(event.target.value)} className="rounded border border-input bg-background p-2" /></label>
        <label className="grid gap-1">Reason<input required maxLength={255} value={dueReason} onChange={(event) => setDueReason(event.target.value)} className="rounded border border-input bg-background p-2" /></label>
        <label className="flex items-center gap-2">
          <input type="checkbox" checked={deductPoints} onChange={(event) => setDeductPoints(event.target.checked)} />
          Deduct points for this extension
        </label>
        <p className="text-muted-foreground">
          {deductPoints
            ? "This will count as a strike against the assignee(s)' points if applicable."
            : "No points will be deducted for this extension - it still counts toward the visible extension history."}
        </p>
        <button disabled={busy || !dueDate || !dueReason.trim()} className="rounded bg-primary px-3 py-2 font-semibold text-primary-foreground disabled:opacity-60">Save due date</button>
      </form>}

      {panel === "reassign" && <form onSubmit={submitReassignment} className="grid gap-2 rounded-md border border-border p-3 text-xs">
        <label className="grid gap-1">Move work from<select value={fromAssignmentId} onChange={(event) => setFromAssignmentId(Number(event.target.value))} className="rounded border border-input bg-background p-2">{reassignable.map((assignment) => <option key={assignment.id} value={assignment.id}>{assignment.user.name} · {assignment.progress}%</option>)}</select></label>
        <label className="grid gap-1">Assign to<select required value={toUserId} onChange={(event) => setToUserId(Number(event.target.value))} className="rounded border border-input bg-background p-2"><option value={0}>Choose a team member</option>{candidates.map((person) => <option key={person.id} value={person.id}>{person.name}</option>)}</select></label>
        <label className="grid gap-1">Reason<input required maxLength={255} list="reassignment-reasons" value={reason} onChange={(event) => setReason(event.target.value)} className="rounded border border-input bg-background p-2" /></label>
        <datalist id="reassignment-reasons">{REASSIGNMENT_REASONS.map((item) => <option key={item} value={item} />)}</datalist>
        <label className="grid gap-1">Classification (optional, Admin note only)
          <select value={classification} onChange={(event) => setClassification(event.target.value as ReassignmentRequest["classification"] | "")} className="rounded border border-input bg-background p-2">
            {CLASSIFICATIONS.map((item) => <option key={item.label} value={item.value}>{item.label}</option>)}
          </select>
        </label>
        <label className="flex gap-2"><input type="checkbox" checked={keepProgress} onChange={(event) => setKeepProgress(event.target.checked)} />Carry over progress</label>
        <label className="flex items-center gap-2">
          <input type="checkbox" checked={deductPointsOnReassign} onChange={(event) => setDeductPointsOnReassign(event.target.checked)} />
          Original assignee loses points for this task
        </label>
        <p className="text-muted-foreground">
          {deductPointsOnReassign
            ? "The original assignee's points for this task will be permanently zeroed out, counted against them - same as a 3rd missed deadline."
            : "The original assignee's points for this task are unaffected - reassignment is neutral by default."}
        </p>
        <button disabled={busy || !fromAssignmentId || !toUserId || !reason.trim()} className="rounded bg-primary px-3 py-2 font-semibold text-primary-foreground disabled:opacity-60">Confirm reassignment</button>
      </form>}

      {panel === "history" && <div className="grid gap-3 rounded-md border border-border p-3 text-xs">
        <p className="font-semibold">Due-date history</p>
        {dueHistory.isLoading ? <p>Loading…</p> : dueHistory.data?.changes.length ? dueHistory.data.changes.map((item) => (
          <p key={item.id}>
            {item.kind.replace("_", " ")}: {item.previousDueDate ?? "—"} → {item.newDueDate}{item.reason ? ` · ${item.reason}` : ""}{item.changedBy ? ` · by ${item.changedBy.name}` : ""}
            {!employee && item.kind === "EXTENDED" && !item.countsTowardStrikes && <span className="ml-1 font-semibold text-muted-foreground">(points waived)</span>}
          </p>
        )) : <p className="text-muted-foreground">No due-date changes yet.</p>}
        {!employee && <><p className="font-semibold">Reassignment history</p>{reassignmentHistory.isLoading ? <p>Loading…</p> : reassignmentHistory.data?.length ? reassignmentHistory.data.map((item) => <p key={item.id}>{item.fromUser.name} → {item.toUser.name} · {item.reason}</p>) : <p className="text-muted-foreground">No reassignments yet.</p>}</>}
      </div>}

      {panel === "activity" && <div className="grid gap-2 rounded-md border border-border p-3 text-xs">{activity.isLoading ? <p>Loading…</p> : activity.data?.length ? activity.data.map((item) => <p key={`${item.type}-${item.id}`}><span className="font-semibold">{item.message}</span>{item.actorName ? ` · ${item.actorName}` : ""}</p>) : <p className="text-muted-foreground">No activity yet.</p>}</div>}
    </section>
  )
}
