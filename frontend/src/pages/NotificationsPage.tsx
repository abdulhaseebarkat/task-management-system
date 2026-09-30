import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { AppLayout } from "@/components/layout/AppLayout"
import { listNotifications, markAllNotificationsRead, markNotificationRead } from "@/api/notifications"

const PAGE_SIZE = 20

function formatWhen(iso: string): string {
  return new Date(iso).toLocaleString(undefined, { month: "short", day: "numeric", hour: "2-digit", minute: "2-digit" })
}

export function NotificationsPage() {
  const [page, setPage] = useState(0)
  const queryClient = useQueryClient()
  const query = useQuery({ queryKey: ["notifications", page], queryFn: () => listNotifications(page, PAGE_SIZE) })

  function refresh() {
    queryClient.invalidateQueries({ queryKey: ["notifications"] })
    queryClient.invalidateQueries({ queryKey: ["notifications-unread-count"] })
  }

  const readMutation = useMutation({ mutationFn: markNotificationRead, onSuccess: refresh })
  const readAllMutation = useMutation({ mutationFn: markAllNotificationsRead, onSuccess: refresh })

  const data = query.data

  return (
    <AppLayout title="Notifications" subtitle="Updates about your tasks">
      <div className="flex flex-col gap-4">
        <div className="flex justify-end">
          <button
            onClick={() => readAllMutation.mutate()}
            disabled={readAllMutation.isPending || !data?.content.some((n) => !n.read)}
            className="text-xs font-semibold text-primary disabled:opacity-40"
          >
            Mark all as read
          </button>
        </div>

        <div className="overflow-hidden rounded-lg border border-border bg-card">
          {query.isLoading && <p className="p-6 text-center text-sm text-muted-foreground">Loading…</p>}
          {data && data.content.length === 0 && <p className="p-6 text-center text-sm text-muted-foreground">No notifications yet.</p>}
          <ul>
            {data?.content.map((item) => (
              <li key={item.id} className={`flex items-start justify-between gap-3 border-b border-border p-4 text-sm last:border-0 ${item.read ? "" : "bg-accent/40"}`}>
                <div className="flex items-start gap-2">
                  {!item.read && <span className="mt-1.5 h-1.5 w-1.5 flex-shrink-0 rounded-full bg-primary" />}
                  <div>
                    <p className="font-semibold">{item.title}</p>
                    {item.message && <p className="mt-0.5 text-xs text-muted-foreground">{item.message}</p>}
                    <p className="mt-1 text-[11px] text-muted-foreground">{formatWhen(item.createdAt)}</p>
                  </div>
                </div>
                {!item.read && (
                  <button onClick={() => readMutation.mutate(item.id)} className="flex-shrink-0 text-xs font-semibold text-primary">
                    Mark read
                  </button>
                )}
              </li>
            ))}
          </ul>
        </div>

        {data && data.totalElements > 0 && (
          <div className="flex items-center justify-between text-xs text-muted-foreground">
            <span>{data.totalElements} total</span>
            <div className="flex gap-2">
              <button disabled={data.page === 0} onClick={() => setPage((p) => p - 1)} className="rounded-md border border-border px-2.5 py-1 font-semibold disabled:opacity-40">Previous</button>
              <span>Page {data.page + 1} of {Math.max(data.totalPages, 1)}</span>
              <button disabled={data.page + 1 >= data.totalPages} onClick={() => setPage((p) => p + 1)} className="rounded-md border border-border px-2.5 py-1 font-semibold disabled:opacity-40">Next</button>
            </div>
          </div>
        )}
      </div>
    </AppLayout>
  )
}
