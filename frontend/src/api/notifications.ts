import { apiClient } from "./client"
import type { PagedResponse } from "@/types/tasks"
import type { Notification } from "@/types/notifications"

export async function listNotifications(page = 0, size = 20): Promise<PagedResponse<Notification>> {
  const response = await apiClient.get<PagedResponse<Notification>>("/notifications", { params: { page, size } })
  return response.data
}

export async function unreadNotificationCount(): Promise<number> {
  const response = await apiClient.get<{ count: number }>("/notifications/unread-count")
  return response.data.count
}

export async function markNotificationRead(id: number): Promise<void> {
  await apiClient.put(`/notifications/${id}/read`)
}

export async function markAllNotificationsRead(): Promise<void> {
  await apiClient.put("/notifications/read-all")
}
