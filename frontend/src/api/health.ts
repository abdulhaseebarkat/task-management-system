import { apiClient } from "./client"

export interface HealthStatus {
  status: string
  service: string
  timestamp: string
}

export async function fetchHealth(): Promise<HealthStatus> {
  const response = await apiClient.get<HealthStatus>("/health")
  return response.data
}
