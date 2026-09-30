import { apiClient } from "./client"
import type { PagedResponse } from "@/types/tasks"

export interface AuditLogActor { id: number; employeeCode: string; name: string; email: string; role: "ADMIN" | "TEAM_MEMBER"; department: string | null; active: boolean }
export interface AuditLogItem {
  id: number
  at: string
  actor: AuditLogActor | null
  entityType: string
  entityId: number
  taskNumber: string | null
  action: string
  summary: string
  oldValue: Record<string, unknown> | null
  newValue: Record<string, unknown> | null
}

export async function listAuditLogs(params: {
  page?: number
  size?: number
  action?: string
  actorId?: number
  taskId?: number
  from?: string
  to?: string
}): Promise<PagedResponse<AuditLogItem>> {
  const response = await apiClient.get<PagedResponse<AuditLogItem>>("/audit-logs", { params })
  return response.data
}
