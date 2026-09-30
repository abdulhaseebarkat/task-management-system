import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { AppLayout } from "@/components/layout/AppLayout"
import { listAuditLogs } from "@/api/auditLogs"

const PAGE_SIZE = 20

export function AuditLogsPage() {
  const [page, setPage] = useState(0)
  const [action, setAction] = useState("")
  const query = useQuery({ queryKey: ["audit-logs", page, action], queryFn: () => listAuditLogs({ page, size: PAGE_SIZE, action: action || undefined }) })
  const data = query.data

  return <AppLayout title="Audit Logs" subtitle="Append-only record of administrative task activity">
    <div className="flex flex-col gap-4">
      <div className="flex flex-wrap items-end gap-2 rounded-lg border border-border bg-card p-3">
        <label className="grid gap-1 text-xs font-semibold">Action
          <select value={action} onChange={(event) => { setAction(event.target.value); setPage(0) }} className="rounded border border-input bg-background p-2 text-sm">
            <option value="">All actions</option>
            <option value="CREATE_TASK">Create task</option>
            <option value="UPDATE_TASK">Edit task</option>
            <option value="CHANGE_DUE_DATE">Change due date</option>
            <option value="REASSIGN_TASK">Reassign task</option>
            <option value="CANCEL_TASK">Cancel task</option>
            <option value="REINSTATE_TASK">Reinstate task</option>
            <option value="REOPEN_TASK">Reopen task</option>
          </select>
        </label>
        <button onClick={() => { setAction(""); setPage(0) }} className="rounded border border-border px-3 py-2 text-xs font-semibold">Clear</button>
      </div>
      <div className="overflow-x-auto rounded-lg border border-border bg-card">
        <table className="w-full text-left text-sm"><thead className="border-b border-border text-xs text-muted-foreground"><tr><th className="p-3">When</th><th className="p-3">Actor</th><th className="p-3">Task</th><th className="p-3">Activity</th></tr></thead>
          <tbody>{query.isLoading ? <tr><td colSpan={4} className="p-6 text-center text-muted-foreground">Loading…</td></tr> : data?.content.length ? data.content.map((item) => <tr key={item.id} className="border-b border-border last:border-0"><td className="p-3 text-xs text-muted-foreground">{new Date(item.at).toLocaleString()}</td><td className="p-3">{item.actor?.name ?? "System"}</td><td className="p-3 font-mono text-xs">{item.taskNumber ?? "—"}</td><td className="p-3">{item.summary}</td></tr>) : <tr><td colSpan={4} className="p-6 text-center text-muted-foreground">No audit events match these filters.</td></tr>}</tbody>
        </table>
      </div>
      {data && <div className="flex items-center justify-between text-xs text-muted-foreground"><span>{data.totalElements} events · Page {data.page + 1} of {data.totalPages}</span><div className="flex gap-2"><button disabled={data.page === 0} onClick={() => setPage((current) => current - 1)} className="rounded border border-border px-3 py-2 font-semibold disabled:opacity-40">Previous</button><button disabled={data.page + 1 >= data.totalPages} onClick={() => setPage((current) => current + 1)} className="rounded border border-border px-3 py-2 font-semibold disabled:opacity-40">Next</button></div></div>}
    </div>
  </AppLayout>
}
