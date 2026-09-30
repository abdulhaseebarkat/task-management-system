import { apiClient } from "./client"
import type { AuthUser } from "@/types/auth"

export interface LoginRequest {
  email: string
  password: string
}

export interface LoginResponse {
  token: string
  user: AuthUser
}

export async function login(request: LoginRequest): Promise<LoginResponse> {
  const response = await apiClient.post<LoginResponse>("/auth/login", request)
  return response.data
}

export async function fetchCurrentUser(): Promise<AuthUser> {
  const response = await apiClient.get<AuthUser>("/auth/me")
  return response.data
}

export interface ChangePasswordRequest {
  currentPassword: string
  newPassword: string
}

/** Any authenticated user changing their own password (Admin or team member). */
export async function changePassword(request: ChangePasswordRequest): Promise<void> {
  await apiClient.put("/auth/password", request)
}
