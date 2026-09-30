import { useState } from "react"
import type { Task } from "@/types/tasks"

interface AdminActionsProps {
  task: Task
  onEdit: (task: Task) => void
  onCancel: (task: Task) => void
  onReopen: (task: Task, keepProgress: boolean) => void
  onReinstate: (task: Task) => void
}

type Panel = "reopen" | "reinstate" | null

/** Edit / Reopen / Reinstate / Cancel. Reopen and Reinstate confirm inline so Reopen can offer its progress choice. */
export function AdminActions({ task, onEdit, onCancel, onReopen, onReinstate }: AdminActionsProps) {
  const [panel, setPanel] = useState<Panel>(null)
  const [keepProgress, setKeepProgress] = useState(false)
  const cancelled = task.status === "CANCELLED"
  const completed = task.status === "COMPLETED"

  return (
    <div className="flex flex-col gap-3 border-t border-border pt-4">
      <div className="flex flex-wrap gap-2">
        {!cancelled && (
          <button onClick={() => onEdit(task)} className="rounded-md bg-primary px-3 py-2 text-xs font-semibold text-primary-foreground">
            Edit task
          </button>
        )}
        {completed && (
          <button onClick={() => setPanel(panel === "reopen" ? null : "reopen")} className="rounded-md border border-border px-3 py-2 text-xs font-semibold">
            Reopen task
          </button>
        )}
        {cancelled && (
          <button onClick={() => setPanel(panel === "reinstate" ? null : "reinstate")} className="rounded-md bg-primary px-3 py-2 text-xs font-semibold text-primary-foreground">
            Reinstate task
          </button>
        )}
        {!cancelled && (
          <button onClick={() => onCancel(task)} className="rounded-md border border-destructive px-3 py-2 text-xs font-semibold text-destructive">
            Cancel task
          </button>
        )}
      </div>

      {panel === "reopen" && completed && (
        <fieldset className="rounded-md border border-border p-3 text-xs">
          <legend className="px-1 font-semibold">Reopen — what happens to progress?</legend>
          <label className="mb-2 flex items-start gap-2">
            <input type="radio" name="reopen-mode" checked={!keepProgress} onChange={() => setKeepProgress(false)} className="mt-0.5" />
            <span><strong>Reset</strong> — everyone restarts at Assigned, 0%.</span>
          </label>
          <label className="mb-3 flex items-start gap-2">
            <input type="radio" name="reopen-mode" checked={keepProgress} onChange={() => setKeepProgress(true)} className="mt-0.5" />
            <span><strong>Keep</strong> — everyone resumes In Progress at the progress they had before finishing.</span>
          </label>
          <div className="flex gap-2">
            <button
              onClick={() => {
                onReopen(task, keepProgress)
                setPanel(null)
              }}
              className="rounded-md bg-primary px-3 py-2 font-semibold text-primary-foreground"
            >
              Confirm reopen
            </button>
            <button onClick={() => setPanel(null)} className="rounded-md border border-border px-3 py-2 font-semibold">Back</button>
          </div>
        </fieldset>
      )}

      {panel === "reinstate" && cancelled && (
        <div className="rounded-md border border-border p-3 text-xs">
          <p className="mb-3">Bring this task back? Unfinished work resumes where it stopped (progress is kept).</p>
          <div className="flex gap-2">
            <button
              onClick={() => {
                onReinstate(task)
                setPanel(null)
              }}
              className="rounded-md bg-primary px-3 py-2 font-semibold text-primary-foreground"
            >
              Confirm reinstate
            </button>
            <button onClick={() => setPanel(null)} className="rounded-md border border-border px-3 py-2 font-semibold">Back</button>
          </div>
        </div>
      )}
    </div>
  )
}
