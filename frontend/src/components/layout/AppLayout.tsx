import type { ReactNode } from "react"
import { Link, useLocation, useNavigate } from "react-router-dom"
import { useQuery, useQueryClient } from "@tanstack/react-query"
import { useAuth } from "@/auth/AuthContext"
import { unreadNotificationCount } from "@/api/notifications"
import { useRealtimeSubscription } from "@/hooks/useRealtimeSubscription"
import { cn } from "@/lib/utils"

interface NavItem {
  label: string
  to: string
}

const ADMIN_NAV: NavItem[] = [
  { label: "Dashboard", to: "/admin/dashboard" },
  { label: "Tasks", to: "/admin/tasks" },
  { label: "Team", to: "/admin/team" },
  { label: "Reports", to: "/admin/reports" },
  { label: "Notifications", to: "/admin/notifications" },
  { label: "Audit Logs", to: "/admin/audit-logs" },
  { label: "Settings", to: "/admin/settings" },
  { label: "Profile", to: "/admin/profile" },
]

const EMPLOYEE_NAV: NavItem[] = [
  { label: "Dashboard", to: "/employee/dashboard" },
  { label: "My Tasks", to: "/employee/tasks" },
  { label: "My Report", to: "/employee/report" },
  { label: "Notifications", to: "/employee/notifications" },
  { label: "Profile", to: "/employee/profile" },
]

export function AppLayout({ title, subtitle, children }: { title: string; subtitle?: string; children: ReactNode }) {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const queryClient = useQueryClient()

  const navItems = user?.role === "ADMIN" ? ADMIN_NAV : EMPLOYEE_NAV
  const initials = user?.name.split(" ").map((p) => p[0]).slice(0, 2).join("").toUpperCase() ?? ""

  // The 60s poll is the fallback; a WebSocket push (Phase 7) triggers an immediate refetch on top
  // of it whenever the backend creates a notification for this user, so the badge feels live
  // without dropping the safety net for a client whose socket happens to be disconnected.
  const unreadQuery = useQuery({
    queryKey: ["notifications-unread-count"],
    queryFn: unreadNotificationCount,
    enabled: !!user,
    refetchInterval: 60_000,
  })
  const unread = unreadQuery.data ?? 0

  useRealtimeSubscription(user ? "/user/queue/notifications" : null, () => {
    queryClient.invalidateQueries({ queryKey: ["notifications-unread-count"] })
    queryClient.invalidateQueries({ queryKey: ["notifications"] })
  })

  function handleLogout() {
    logout()
    navigate("/login", { replace: true })
  }

  return (
    <div className="flex min-h-screen w-full items-start bg-background">
      <aside className="sticky top-0 flex h-screen w-56 flex-shrink-0 flex-col justify-between bg-[#171c26] px-3.5 py-5">
        <div className="flex flex-col gap-6">
          <div className="px-2">
            <div className="text-[15px] font-bold tracking-wide text-white">SLM TIRES</div>
            <div className="mt-0.5 text-[11px] text-[#7c8494]">IT Task Management</div>
          </div>
          <nav className="flex flex-col gap-0.5">
            {navItems.map((item) => {
              const active = location.pathname === item.to
              const isNotifications = item.label === "Notifications"
              return (
                <Link
                  key={item.to}
                  to={item.to}
                  className={cn(
                    "flex items-center justify-between rounded-lg px-3.5 py-2.5 text-[13px] font-medium text-[#c7cdd6]",
                    active && "bg-[#232a38] text-white shadow-[inset_3px_0_0_var(--primary)]"
                  )}
                >
                  <span>{item.label}</span>
                  {isNotifications && unread > 0 && (
                    <span className="rounded-full bg-primary px-1.5 py-0.5 text-[10px] font-bold text-primary-foreground">
                      {unread > 99 ? "99+" : unread}
                    </span>
                  )}
                </Link>
              )
            })}
          </nav>
        </div>
        <div className="flex items-center gap-2.5 border-t border-white/10 px-2 pt-2.5">
          <div className="flex h-8 w-8 items-center justify-center rounded-full bg-primary text-xs font-bold text-white">
            {initials}
          </div>
          <div className="flex min-w-0 flex-col">
            <span className="truncate text-xs font-semibold text-white">{user?.name}</span>
            <span className="truncate text-[11px] text-[#7c8494]">
              {user?.role === "ADMIN" ? "IT Department Head" : "IT Team Member"}
            </span>
          </div>
          <button
            onClick={handleLogout}
            className="ml-auto text-[11px] font-medium text-[#7c8494] hover:text-white"
            title="Log out"
          >
            Log out
          </button>
        </div>
      </aside>

      <main className="min-w-0 flex-grow px-9 py-7">
        <div className="mb-5">
          <h1 className="text-xl font-bold text-foreground">{title}</h1>
          {subtitle && <p className="mt-1 text-sm text-muted-foreground">{subtitle}</p>}
        </div>
        {children}
      </main>
    </div>
  )
}
