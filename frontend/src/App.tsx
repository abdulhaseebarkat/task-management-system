import { Navigate, Route, Routes } from "react-router-dom"
import { LoginPage } from "@/pages/LoginPage"
import { AdminDashboardPage } from "@/pages/admin/AdminDashboardPage"
import { TeamPage } from "@/pages/admin/TeamPage"
import { AuditLogsPage } from "@/pages/admin/AuditLogsPage"
import { ReportsPage } from "@/pages/admin/ReportsPage"
import { SettingsPage } from "@/pages/admin/SettingsPage"
import { NotificationsPage } from "@/pages/NotificationsPage"
import { EmployeeDashboardPage } from "@/pages/employee/EmployeeDashboardPage"
import { MyReportPage } from "@/pages/employee/MyReportPage"
import { AdminTasksPage } from "@/pages/admin/TasksPage"
import { EmployeeTasksPage } from "@/pages/employee/TasksPage"
import { ProfilePage } from "@/pages/ProfilePage"
import { NotFoundPage } from "@/pages/NotFoundPage"
import { ProtectedRoute } from "@/routes/ProtectedRoute"

function App() {
  return (
    <Routes>
      <Route path="/" element={<Navigate to="/login" replace />} />
      <Route path="/login" element={<LoginPage />} />

      <Route
        path="/admin/dashboard"
        element={
          <ProtectedRoute allowedRoles={["ADMIN"]}>
            <AdminDashboardPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/admin/team"
        element={
          <ProtectedRoute allowedRoles={["ADMIN"]}>
            <TeamPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/admin/tasks"
        element={
          <ProtectedRoute allowedRoles={["ADMIN"]}>
            <AdminTasksPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/admin/audit-logs"
        element={<ProtectedRoute allowedRoles={["ADMIN"]}><AuditLogsPage /></ProtectedRoute>}
      />
      <Route
        path="/admin/notifications"
        element={<ProtectedRoute allowedRoles={["ADMIN"]}><NotificationsPage /></ProtectedRoute>}
      />
      <Route
        path="/admin/reports"
        element={<ProtectedRoute allowedRoles={["ADMIN"]}><ReportsPage /></ProtectedRoute>}
      />
      <Route
        path="/admin/settings"
        element={<ProtectedRoute allowedRoles={["ADMIN"]}><SettingsPage /></ProtectedRoute>}
      />
      <Route
        path="/admin/profile"
        element={<ProtectedRoute allowedRoles={["ADMIN"]}><ProfilePage /></ProtectedRoute>}
      />

      <Route
        path="/employee/dashboard"
        element={
          <ProtectedRoute allowedRoles={["TEAM_MEMBER"]}>
            <EmployeeDashboardPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/employee/profile"
        element={
          <ProtectedRoute allowedRoles={["TEAM_MEMBER"]}>
            <ProfilePage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/employee/tasks"
        element={
          <ProtectedRoute allowedRoles={["TEAM_MEMBER"]}>
            <EmployeeTasksPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/employee/notifications"
        element={<ProtectedRoute allowedRoles={["TEAM_MEMBER"]}><NotificationsPage /></ProtectedRoute>}
      />
      <Route
        path="/employee/report"
        element={<ProtectedRoute allowedRoles={["TEAM_MEMBER"]}><MyReportPage /></ProtectedRoute>}
      />

      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  )
}

export default App
