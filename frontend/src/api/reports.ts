import { apiClient } from "./client"
import type { AnalyticsFilters } from "@/types/analytics"
import type { EmployeeReport } from "@/types/reports"

export async function getEmployeeReport(employeeId: number, filters: Omit<AnalyticsFilters, "employeeId">): Promise<EmployeeReport> {
  const response = await apiClient.get<EmployeeReport>(`/analytics/employee/${employeeId}`, { params: filters })
  return response.data
}

export type ExportFormat = "pdf" | "xlsx"

async function downloadFile(url: string, params: object, fallbackName: string): Promise<void> {
  const response = await apiClient.get<Blob>(url, { params, responseType: "blob" })
  const disposition = response.headers["content-disposition"] as string | undefined
  const match = disposition?.match(/filename="?([^"]+)"?/)
  const filename = match?.[1] ?? fallbackName

  const blobUrl = window.URL.createObjectURL(response.data)
  const link = document.createElement("a")
  link.href = blobUrl
  link.download = filename
  document.body.appendChild(link)
  link.click()
  link.remove()
  window.URL.revokeObjectURL(blobUrl)
}

export function exportDepartmentReport(format: ExportFormat, filters: AnalyticsFilters): Promise<void> {
  return downloadFile(`/analytics/dashboard/export.${format}`, filters, `department-report.${format}`)
}

export function exportEmployeeReport(employeeId: number, format: ExportFormat, filters: Omit<AnalyticsFilters, "employeeId">): Promise<void> {
  return downloadFile(`/analytics/employee/${employeeId}/export.${format}`, filters, `employee-report.${format}`)
}
