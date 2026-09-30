import { apiClient } from "./client"
import type { AuthUser, Role } from "@/types/auth"

export interface CreateUserRequest {
  name: string
  email: string
  role: Role
  department: string
}

export interface CreateUserResponse {
  user: AuthUser
  temporaryPassword: string
}

export async function listUsers(): Promise<AuthUser[]> {
  const response = await apiClient.get<AuthUser[]>("/users")
  return response.data
}

export async function createUser(request: CreateUserRequest): Promise<CreateUserResponse> {
  const response = await apiClient.post<CreateUserResponse>("/users", request)
  return response.data
}

export async function setUserStatus(id: number, active: boolean): Promise<AuthUser> {
  const response = await apiClient.put<AuthUser>(`/users/${id}/status`, { active })
  return response.data
}
