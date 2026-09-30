import { apiClient } from "./client"

export async function getEmployeeSummary(): Promise<{ reassignedAway: number }> {
  const response = await apiClient.get<{ reassignedAway: number }>("/dashboard/employee-summary")
  return response.data
}
