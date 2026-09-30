import { useState, type FormEvent } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { addTaskComment, getTaskComments } from "@/api/tasks"
import { apiErrorMessage } from "@/lib/apiError"
import { useRealtimeSubscription } from "@/hooks/useRealtimeSubscription"

function formatWhen(iso: string): string {
  return new Date(iso).toLocaleString(undefined, { month: "short", day: "numeric", hour: "2-digit", minute: "2-digit" })
}

/** One shared thread per task - every current assignee and the Admin see the same messages. */
export function TaskComments({ taskId }: { taskId: number }) {
  const [open, setOpen] = useState(false)
  const [text, setText] = useState("")
  const [error, setError] = useState<string | null>(null)
  const queryClient = useQueryClient()

  const commentsQuery = useQuery({ queryKey: ["tasks", taskId, "comments"], queryFn: () => getTaskComments(taskId), enabled: open })

  useRealtimeSubscription(open ? `/topic/tasks/${taskId}` : null, () => {
    queryClient.invalidateQueries({ queryKey: ["tasks", taskId, "comments"] })
  })

  const mutation = useMutation({
    mutationFn: (comment: string) => addTaskComment(taskId, comment),
    onSuccess: () => {
      setText("")
      setError(null)
      queryClient.invalidateQueries({ queryKey: ["tasks", taskId, "comments"] })
    },
    onError: (err) => setError(apiErrorMessage(err, "Could not post the comment.")),
  })

  function submit(event: FormEvent) {
    event.preventDefault()
    if (!text.trim()) return
    mutation.mutate(text.trim())
  }

  return (
    <section className="flex flex-col gap-3 border-t border-border pt-4">
      <button onClick={() => setOpen((v) => !v)} className="w-fit rounded-md border border-border px-3 py-2 text-xs font-semibold">
        {open ? "Hide comments" : "Comments"}
      </button>

      {open && (
        <div className="flex flex-col gap-3 rounded-md border border-border p-3">
          <div className="flex flex-col gap-2">
            {commentsQuery.isLoading && <p className="text-xs text-muted-foreground">Loading…</p>}
            {commentsQuery.data?.length === 0 && <p className="text-xs text-muted-foreground">No comments yet.</p>}
            {commentsQuery.data?.map((item) => (
              <div key={item.id} className="text-xs">
                <div className="flex items-baseline justify-between gap-2">
                  <span className="font-semibold">{item.author.name}</span>
                  <span className="text-muted-foreground">{formatWhen(item.createdAt)}</span>
                </div>
                <p className="mt-0.5 whitespace-pre-wrap text-secondary-foreground">{item.comment}</p>
              </div>
            ))}
          </div>

          <form onSubmit={submit} className="flex flex-col gap-2">
            <textarea
              value={text}
              onChange={(event) => setText(event.target.value)}
              maxLength={4000}
              placeholder="Post a progress update…"
              className="min-h-16 rounded-md border border-input bg-background p-2 text-xs"
            />
            {error && <p className="text-xs text-destructive">{error}</p>}
            <button
              type="submit"
              disabled={mutation.isPending || !text.trim()}
              className="w-fit rounded-md bg-primary px-3 py-1.5 text-xs font-semibold text-primary-foreground disabled:opacity-60"
            >
              {mutation.isPending ? "Posting..." : "Post comment"}
            </button>
          </form>
        </div>
      )}
    </section>
  )
}
