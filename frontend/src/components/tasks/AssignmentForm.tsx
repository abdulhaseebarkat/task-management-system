import { useState } from "react"
import type { AssignmentStatus, AssignmentUpdate, TaskAssignment } from "@/types/tasks"
import { label } from "./taskDisplay"

const SETTABLE_STATUSES: AssignmentStatus[] = ["IN_PROGRESS", "ON_HOLD", "BLOCKED", "COMPLETED"]

const REASON_SUGGESTIONS: Record<"BLOCKED" | "ON_HOLD", string[]> = {
  BLOCKED: ["Waiting for hardware", "Waiting for vendor", "Waiting for approval", "Network dependency", "Access unavailable"],
  ON_HOLD: ["Waiting for management decision", "Project temporarily paused", "Vendor dependency", "Operational priority changed"],
}

interface AssignmentFormProps {
  assignment: TaskAssignment
  heading: string
  isSaving: boolean
  onSubmit: (update: AssignmentUpdate) => void
  /** Present when the form can be dismissed (the Admin's "adjust" panel). */
  onClose?: () => void
}

/**
 * Status / progress / reason for one assignment. The rules mirror the server's:
 * Completed is fixed at 100%, everything else tops out at 99%, and Blocked / On Hold need a reason.
 * Remount it (via `key`) when the saved values change so it always starts from the latest server state.
 */
export function AssignmentForm({ assignment, heading, isSaving, onSubmit, onClose }: AssignmentFormProps) {
  const [status, setStatus] = useState<AssignmentStatus>(assignment.status === "ASSIGNED" ? "IN_PROGRESS" : assignment.status)
  const [progress, setProgress] = useState(assignment.progress)
  const [reason, setReason] = useState(assignment.reason ?? "")

  const completed = status === "COMPLETED"
  const needsReason = status === "BLOCKED" || status === "ON_HOLD"
  const shownProgress = completed ? 100 : Math.min(progress, 99)
  const canSave = !isSaving && (!needsReason || reason.trim().length > 0)
  const suffix = `${assignment.id}`

  return (
    <form
      className="border-t border-border pt-4"
      onSubmit={(event) => {
        event.preventDefault()
        onSubmit({ status, progress: shownProgress, reason: needsReason ? reason.trim() : undefined })
      }}
    >
      <p className="mb-2 text-xs font-semibold">{heading}</p>

      <label className="mb-1 block text-xs text-muted-foreground" htmlFor={`status-${suffix}`}>Status</label>
      <select
        id={`status-${suffix}`}
        value={status}
        onChange={(event) => setStatus(event.target.value as AssignmentStatus)}
        className="mb-3 w-full rounded-md border border-input bg-background px-2.5 py-2 text-sm"
      >
        {SETTABLE_STATUSES.map((item) => (
          <option key={item} value={item}>{label(item)}</option>
        ))}
      </select>

      <label className="mb-1 flex justify-between text-xs text-muted-foreground" htmlFor={`progress-${suffix}`}>
        <span>Progress</span>
        <span>{shownProgress}%{completed ? " (fixed when completed)" : ""}</span>
      </label>
      <input
        id={`progress-${suffix}`}
        type="range"
        min="0"
        max={completed ? 100 : 99}
        value={shownProgress}
        disabled={completed}
        onChange={(event) => setProgress(Number(event.target.value))}
        className="mb-3 w-full"
      />

      {needsReason && (
        <>
          <label className="mb-1 block text-xs text-muted-foreground" htmlFor={`reason-${suffix}`}>Reason (required)</label>
          <input
            id={`reason-${suffix}`}
            list={`reasons-${suffix}`}
            maxLength={255}
            value={reason}
            onChange={(event) => setReason(event.target.value)}
            placeholder={status === "BLOCKED" ? "What is blocking the work?" : "Why is this on hold?"}
            className="mb-3 w-full rounded-md border border-input bg-background px-2.5 py-2 text-sm"
          />
          <datalist id={`reasons-${suffix}`}>
            {REASON_SUGGESTIONS[status as "BLOCKED" | "ON_HOLD"].map((suggestion) => (
              <option key={suggestion} value={suggestion} />
            ))}
          </datalist>
        </>
      )}

      <div className="flex gap-2">
        <button type="submit" disabled={!canSave} className="flex-1 rounded-md bg-primary px-3 py-2 text-xs font-semibold text-primary-foreground disabled:opacity-60">
          {isSaving ? "Saving..." : "Save update"}
        </button>
        {onClose && (
          <button type="button" onClick={onClose} className="rounded-md border border-border px-3 py-2 text-xs font-semibold">
            Close
          </button>
        )}
      </div>
    </form>
  )
}
