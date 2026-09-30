import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { AppLayout } from "@/components/layout/AppLayout"
import { DepartmentReportView } from "@/components/reports/DepartmentReportView"
import { IndividualReportView } from "@/components/reports/IndividualReportView"
import { listUsers } from "@/api/users"

type Tab = "department" | "individual"

const tabClass = (active: boolean) =>
  `rounded-md px-3.5 py-2 text-xs font-semibold ${active ? "bg-primary text-primary-foreground" : "border border-border text-secondary-foreground"}`

export function ReportsPage() {
  const [tab, setTab] = useState<Tab>("department")
  const usersQuery = useQuery({ queryKey: ["users"], queryFn: listUsers })
  const teamMembers = (usersQuery.data ?? []).filter((u) => u.role === "TEAM_MEMBER")
  const [employeeId, setEmployeeId] = useState<number | null>(null)

  const selected = employeeId ?? teamMembers[0]?.id ?? null

  return (
    <AppLayout title="Reports" subtitle="Department and individual performance, with PDF/Excel export">
      <div className="flex flex-col gap-5">
        <div className="flex flex-wrap items-center gap-2">
          <button onClick={() => setTab("department")} className={tabClass(tab === "department")}>Department Report</button>
          <button onClick={() => setTab("individual")} className={tabClass(tab === "individual")}>Individual Report</button>
          {tab === "individual" && (
            <select
              aria-label="Employee"
              value={selected ?? ""}
              onChange={(event) => setEmployeeId(Number(event.target.value))}
              className="rounded-md border border-input bg-card px-2.5 py-2 text-xs"
            >
              {teamMembers.map((member) => <option key={member.id} value={member.id}>{member.name}</option>)}
            </select>
          )}
        </div>

        {tab === "department" && <DepartmentReportView />}
        {tab === "individual" && (selected ? <IndividualReportView key={selected} employeeId={selected} /> : <p className="text-sm text-muted-foreground">No team members yet.</p>)}
      </div>
    </AppLayout>
  )
}
