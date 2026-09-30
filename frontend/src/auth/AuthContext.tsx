import { createContext, useContext, useEffect, useState, type ReactNode } from "react"
import { fetchCurrentUser, login as loginRequest } from "@/api/auth"
import { clearStoredToken, getStoredToken, setStoredToken } from "@/api/client"
import { disconnectRealtime } from "@/lib/realtime"
import type { AuthUser } from "@/types/auth"

interface AuthContextValue {
  user: AuthUser | null
  isLoading: boolean
  login: (email: string, password: string) => Promise<AuthUser>
  logout: () => void
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(null)
  const [isLoading, setIsLoading] = useState(true)

  useEffect(() => {
    const token = getStoredToken()
    if (!token) {
      setIsLoading(false)
      return
    }
    fetchCurrentUser()
      .then(setUser)
      .catch(() => clearStoredToken())
      .finally(() => setIsLoading(false))
  }, [])

  async function login(email: string, password: string): Promise<AuthUser> {
    const response = await loginRequest({ email, password })
    setStoredToken(response.token)
    setUser(response.user)
    return response.user
  }

  function logout() {
    disconnectRealtime()
    clearStoredToken()
    setUser(null)
  }

  return (
    <AuthContext.Provider value={{ user, isLoading, login, logout }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext)
  if (!ctx) {
    throw new Error("useAuth must be used within an AuthProvider")
  }
  return ctx
}
