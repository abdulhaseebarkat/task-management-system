import type { Task } from "@/types/tasks"

export function label(value: string): string {
  return value.replaceAll("_", " ")
}

/** A task can still be assigned/blocked/on-hold past its due date; only these count as "open". Mirrors the backend's own AnalyticsService.OPEN_STATUSES exactly, including FAILED (a real, advisory-only status that doesn't end the task's operational life). */
export const OPEN_TASK_STATUSES = new Set(["ASSIGNED", "IN_PROGRESS", "REOPENED", "BLOCKED", "ON_HOLD", "FAILED"])

/** A cancelled or completed task past its due date isn't "overdue" - it's just done. Mirrors the backend's own definition (AnalyticsService's OPEN_STATUSES). */
export function isTaskOverdue(task: Pick<Task, "dueDate" | "status">): boolean {
  if (!task.dueDate || !OPEN_TASK_STATUSES.has(task.status)) return false
  return task.dueDate < new Date().toISOString().slice(0, 10)
}

const BADGE_COLORS: Record<string, string> = {
  CRITICAL: "bg-red-100 text-red-800",
  HIGH: "bg-orange-100 text-orange-800",
  MEDIUM: "bg-blue-100 text-blue-800",
  LOW: "bg-slate-100 text-slate-700",
  COMPLETED: "bg-green-100 text-green-800",
  BLOCKED: "bg-red-100 text-red-800",
  IN_PROGRESS: "bg-amber-100 text-amber-800",
  ASSIGNED: "bg-blue-100 text-blue-800",
  REOPENED: "bg-purple-100 text-purple-800",
  ON_HOLD: "bg-slate-100 text-slate-700",
  CANCELLED: "bg-slate-200 text-slate-600",
  FAILED: "bg-red-600 text-white",
  DRAFT: "bg-slate-100 text-slate-500",
}

export function badgeClass(value: string): string {
  return BADGE_COLORS[value] ?? "bg-accent text-accent-foreground"
}

export const OVERDUE_BADGE_CLASS = "rounded-full bg-destructive/10 px-1.5 py-0.5 text-[10px] font-bold text-destructive"
