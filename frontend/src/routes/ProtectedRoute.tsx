import type { ReactNode } from "react"
import { Navigate } from "react-router-dom"
import { useAuth } from "@/auth/AuthContext"
import type { Role } from "@/types/auth"

interface ProtectedRouteProps {
  allowedRoles?: Role[]
  children: ReactNode
}

export function ProtectedRoute({ allowedRoles, children }: ProtectedRouteProps) {
  const { user, isLoading } = useAuth()

  if (isLoading) {
    return null
  }

  if (!user) {
    return <Navigate to="/login" replace />
  }

  if (allowedRoles && !allowedRoles.includes(user.role)) {
    const fallback = user.role === "ADMIN" ? "/admin/dashboard" : "/employee/dashboard"
    return <Navigate to={fallback} replace />
  }

  return <>{children}</>
}
