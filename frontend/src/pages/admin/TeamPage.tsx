import { useState, type FormEvent } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { AppLayout } from "@/components/layout/AppLayout"
import { createUser, listUsers, setUserStatus, type CreateUserResponse } from "@/api/users"
import type { Role } from "@/types/auth"

export function TeamPage() {
  const queryClient = useQueryClient()
  const { data: users, isLoading } = useQuery({ queryKey: ["users"], queryFn: listUsers })

  const [showForm, setShowForm] = useState(false)
  const [name, setName] = useState("")
  const [email, setEmail] = useState("")
  const [role, setRole] = useState<Role>("TEAM_MEMBER")
  const [department, setDepartment] = useState("IT")
  const [createdResult, setCreatedResult] = useState<CreateUserResponse | null>(null)
  const [formError, setFormError] = useState<string | null>(null)

  const createMutation = useMutation({
    mutationFn: createUser,
    onSuccess: (result) => {
      queryClient.invalidateQueries({ queryKey: ["users"] })
      setCreatedResult(result)
      setName("")
      setEmail("")
      setDepartment("IT")
      setRole("TEAM_MEMBER")
      setFormError(null)
    },
    onError: () => setFormError("Could not create this account. The email may already be in use."),
  })

  const statusMutation = useMutation({
    mutationFn: ({ id, active }: { id: number; active: boolean }) => setUserStatus(id, active),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["users"] }),
  })

  function handleCreate(e: FormEvent) {
    e.preventDefault()
    createMutation.mutate({ name, email, role, department })
  }

  return (
    <AppLayout title="Team" subtitle="Manage IT department accounts">
      <div className="flex flex-col gap-5">
        {createdResult && (
          <div className="rounded-lg border border-primary/30 bg-accent px-4 py-3 text-sm">
            <p className="font-semibold text-accent-foreground">
              {createdResult.user.name} was created ({createdResult.user.employeeCode}).
            </p>
            <p className="mt-1 text-muted-foreground">
              Temporary password (shown once — share it with them privately):{" "}
              <code className="rounded bg-card px-1.5 py-0.5 font-mono text-foreground">
                {createdResult.temporaryPassword}
              </code>
            </p>
            <button
              className="mt-2 text-xs font-medium text-primary"
              onClick={() => setCreatedResult(null)}
            >
              Dismiss
            </button>
          </div>
        )}

        <div className="flex items-center justify-between">
          <p className="text-sm text-muted-foreground">{users?.length ?? 0} accounts</p>
          <button
            onClick={() => setShowForm((v) => !v)}
            className="rounded-md bg-primary px-3.5 py-2 text-xs font-semibold text-primary-foreground"
          >
            {showForm ? "Cancel" : "+ Add employee"}
          </button>
        </div>

        {showForm && (
          <form onSubmit={handleCreate} className="flex flex-wrap items-end gap-3 rounded-lg border border-border bg-card p-4">
            <div className="flex flex-col gap-1">
              <label className="text-xs font-medium text-secondary-foreground">Name</label>
              <input
                required
                value={name}
                onChange={(e) => setName(e.target.value)}
                className="rounded-md border border-input bg-background px-2.5 py-1.5 text-sm outline-none focus:border-primary"
              />
            </div>
            <div className="flex flex-col gap-1">
              <label className="text-xs font-medium text-secondary-foreground">Email</label>
              <input
                required
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                className="rounded-md border border-input bg-background px-2.5 py-1.5 text-sm outline-none focus:border-primary"
              />
            </div>
            <div className="flex flex-col gap-1">
              <label className="text-xs font-medium text-secondary-foreground">Role</label>
              <select
                value={role}
                onChange={(e) => setRole(e.target.value as Role)}
                className="rounded-md border border-input bg-background px-2.5 py-1.5 text-sm outline-none focus:border-primary"
              >
                <option value="TEAM_MEMBER">Team Member</option>
                <option value="ADMIN">Admin</option>
              </select>
            </div>
            <div className="flex flex-col gap-1">
              <label className="text-xs font-medium text-secondary-foreground">Department</label>
              <input
                value={department}
                onChange={(e) => setDepartment(e.target.value)}
                className="rounded-md border border-input bg-background px-2.5 py-1.5 text-sm outline-none focus:border-primary"
              />
            </div>
            <button
              type="submit"
              disabled={createMutation.isPending}
              className="rounded-md bg-primary px-3.5 py-2 text-xs font-semibold text-primary-foreground disabled:opacity-60"
            >
              {createMutation.isPending ? "Creating..." : "Create account"}
            </button>
            {formError && <p className="w-full text-xs font-medium text-destructive">{formError}</p>}
          </form>
        )}

        <div className="overflow-hidden rounded-lg border border-border bg-card">
          <table className="w-full border-collapse text-sm">
            <thead>
              <tr className="border-b border-border text-left text-[11px] font-semibold uppercase tracking-wide text-muted-foreground">
                <th className="px-4 py-3">Employee</th>
                <th className="px-4 py-3">Code</th>
                <th className="px-4 py-3">Role</th>
                <th className="px-4 py-3">Department</th>
                <th className="px-4 py-3">Status</th>
                <th className="px-4 py-3">Action</th>
              </tr>
            </thead>
            <tbody>
              {isLoading && (
                <tr>
                  <td colSpan={6} className="px-4 py-6 text-center text-muted-foreground">
                    Loading...
                  </td>
                </tr>
              )}
              {users?.map((u) => (
                <tr key={u.id} className="border-b border-border last:border-0">
                  <td className="px-4 py-3">
                    <div className="font-medium text-foreground">{u.name}</div>
                    <div className="text-xs text-muted-foreground">{u.email}</div>
                  </td>
                  <td className="px-4 py-3 text-muted-foreground">{u.employeeCode}</td>
                  <td className="px-4 py-3">
                    <span className="rounded-full bg-accent px-2 py-0.5 text-xs font-semibold text-accent-foreground">
                      {u.role === "ADMIN" ? "Admin" : "Team Member"}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-muted-foreground">{u.department}</td>
                  <td className="px-4 py-3">
                    <span
                      className={`inline-flex items-center gap-1.5 text-xs font-semibold ${u.active ? "text-success" : "text-muted-foreground"}`}
                    >
                      <span className={`h-1.5 w-1.5 rounded-full ${u.active ? "bg-success" : "bg-muted-foreground"}`} />
                      {u.active ? "Active" : "Deactivated"}
                    </span>
                  </td>
                  <td className="px-4 py-3">
                    <button
                      onClick={() => statusMutation.mutate({ id: u.id, active: !u.active })}
                      disabled={statusMutation.isPending}
                      className="text-xs font-medium text-primary disabled:opacity-60"
                    >
                      {u.active ? "Deactivate" : "Activate"}
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </AppLayout>
  )
}
