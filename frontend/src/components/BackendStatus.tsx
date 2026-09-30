import { useQuery } from "@tanstack/react-query"
import { fetchHealth } from "@/api/health"

export function BackendStatus() {
  const { data, isLoading, isError } = useQuery({
    queryKey: ["health"],
    queryFn: fetchHealth,
    retry: 1,
  })

  const label = isLoading
    ? "Checking backend..."
    : isError
      ? "Backend unreachable"
      : `Backend connected (${data?.service})`

  const dotColor = isLoading ? "bg-muted-foreground" : isError ? "bg-destructive" : "bg-success"

  return (
    <div className="inline-flex items-center gap-2 rounded-full border border-border bg-card px-3 py-1.5 text-xs text-muted-foreground">
      <span className={`h-2 w-2 rounded-full ${dotColor}`} />
      {label}
    </div>
  )
}
