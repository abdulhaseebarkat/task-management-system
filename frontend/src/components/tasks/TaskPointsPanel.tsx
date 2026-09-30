import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { getTaskPoints } from "@/api/analytics"
import { DivergingPointsBar, pointEventLabel } from "@/components/reports/PointsCharts"

function formatWhen(iso: string): string {
  return new Date(iso).toLocaleString(undefined, { month: "short", day: "numeric", hour: "2-digit", minute: "2-digit" })
}

const OUTCOME_LABEL: Record<string, string> = {
  OPEN: "still open",
  COMPLETED: "completed",
  FAILED: "failed",
  CANCELLED: "cancelled",
  REASSIGNED: "reassigned away",
}

/** One assignment's points ledger for a task - "Points — This Task" from the approved mockup, embedded in the task detail panel next to Comments and History. The backend enforces who may see which assignment: an Admin can view any, a team member only their own. */
export function TaskPointsPanel({ taskId, assignmentId, autoOpen = false }: { taskId: number; assignmentId: number; autoOpen?: boolean }) {
  const [open, setOpen] = useState(autoOpen)
  const query = useQuery({ queryKey: ["tasks", taskId, "points", assignmentId], queryFn: () => getTaskPoints(taskId, assignmentId), enabled: open })
  const data = query.data

  return (
    <div className="flex flex-col gap-2">
      <button onClick={() => setOpen((v) => !v)} className="w-fit text-[11px] font-semibold text-primary">
        {open ? "Hide points" : "View points"}
      </button>
      {open && (
        <div className="rounded-md border border-border p-3">
          {query.isLoading && <p className="text-xs text-muted-foreground">Loading…</p>}
          {data && (
            <>
              <p className="text-center text-xs text-muted-foreground">{data.basePoints} points possible ({data.priority.toLowerCase()})</p>
              <div className="mt-2">
                <DivergingPointsBar retained={data.resultingPoints} lost={data.basePoints - data.resultingPoints} height={70} />
              </div>
              <div className="mt-2 border-t border-border pt-2.5 text-center text-xs text-muted-foreground">
                Final: <strong className="text-foreground">{data.resultingPoints} / {data.basePoints}</strong> · {OUTCOME_LABEL[data.outcome] ?? data.outcome.toLowerCase()}
              </div>
              <ul className="mt-3 flex flex-col border-t border-border">
                {data.events.map((event, i) => {
                  const badge = pointEventLabel(event.eventType)
                  return (
                    <li key={i} className="flex items-center justify-between gap-2 border-t border-border py-2 first:border-0">
                      <div>
                        <div className={`text-xs font-medium ${badge.tone}`}>{badge.text}</div>
                        {event.reason && <div className="text-[11px] text-muted-foreground">{event.reason}</div>}
                      </div>
                      <div className="flex-shrink-0 text-right">
                        <div className="text-xs font-bold">{event.resultingPoints} pts</div>
                        <div className="text-[10px] text-muted-foreground">{formatWhen(event.occurredAt)}</div>
                      </div>
                    </li>
                  )
                })}
              </ul>
            </>
          )}
        </div>
      )}
    </div>
  )
}
