import { AppLayout } from "@/components/layout/AppLayout"
import { IndividualReportView } from "@/components/reports/IndividualReportView"
import { useAuth } from "@/auth/AuthContext"

export function MyReportPage() {
  const { user } = useAuth()

  return (
    <AppLayout title="My Report" subtitle="Your own performance, with PDF/Excel export">
      {user ? <IndividualReportView employeeId={user.id} /> : <p className="text-sm text-muted-foreground">Loading…</p>}
    </AppLayout>
  )
}
