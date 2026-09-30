import { apiClient } from "./client"
import type { AnalyticsFilters, DashboardAnalytics, TaskPoints } from "@/types/analytics"

export async function getDashboardAnalytics(filters: AnalyticsFilters): Promise<DashboardAnalytics> {
  const response = await apiClient.get<DashboardAnalytics>("/analytics/dashboard", { params: filters })
  return response.data
}

/** Admin-only. One assignment's full points ledger for a task. */
export async function getTaskPoints(taskId: number, assignmentId: number): Promise<TaskPoints> {
  const response = await apiClient.get<TaskPoints>(`/analytics/tasks/${taskId}/points/${assignmentId}`)
  return response.data
}

/** Admin-only. One-time, idempotent catch-up for assignments that predate the points system - never backdated, never touches anything already tracked. Safe to call more than once. */
export async function backfillPoints(): Promise<{ assignmentsSeeded: number }> {
  const response = await apiClient.post<{ assignmentsSeeded: number }>("/analytics/points/backfill")
  return response.data
}
