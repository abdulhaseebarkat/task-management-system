export type Role = "ADMIN" | "TEAM_MEMBER"

export interface AuthUser {
  id: number
  employeeCode: string
  name: string
  email: string
  role: Role
  department: string | null
  active: boolean
}
