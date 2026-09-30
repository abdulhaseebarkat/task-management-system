import { useState, type FormEvent } from "react"
import { useMutation } from "@tanstack/react-query"
import { AppLayout } from "@/components/layout/AppLayout"
import { useAuth } from "@/auth/AuthContext"
import { changePassword } from "@/api/auth"
import { apiErrorMessage } from "@/lib/apiError"

const inputClass = "rounded-md border border-input bg-background px-2.5 py-1.5 text-sm outline-none focus:border-primary"

export function ProfilePage() {
  const { user } = useAuth()
  const [currentPassword, setCurrentPassword] = useState("")
  const [newPassword, setNewPassword] = useState("")
  const [confirmPassword, setConfirmPassword] = useState("")
  const [error, setError] = useState<string | null>(null)
  const [success, setSuccess] = useState(false)

  const mutation = useMutation({
    mutationFn: changePassword,
    onSuccess: () => {
      setSuccess(true)
      setError(null)
      setCurrentPassword("")
      setNewPassword("")
      setConfirmPassword("")
    },
    onError: (err) => {
      setSuccess(false)
      setError(apiErrorMessage(err, "Could not change your password."))
    },
  })

  function submit(event: FormEvent) {
    event.preventDefault()
    setSuccess(false)
    if (newPassword !== confirmPassword) {
      setError("New password and confirmation don't match.")
      return
    }
    setError(null)
    mutation.mutate({ currentPassword, newPassword })
  }

  return (
    <AppLayout title="Profile" subtitle="Your account details">
      <div className="flex max-w-md flex-col gap-5">
        <div className="rounded-lg border border-border bg-card p-6">
          <dl className="flex flex-col gap-4 text-sm">
            <div>
              <dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground">Name</dt>
              <dd className="mt-0.5 text-foreground">{user?.name}</dd>
            </div>
            <div>
              <dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground">Employee Code</dt>
              <dd className="mt-0.5 text-foreground">{user?.employeeCode}</dd>
            </div>
            <div>
              <dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground">Email</dt>
              <dd className="mt-0.5 text-foreground">{user?.email}</dd>
            </div>
            <div>
              <dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground">Role</dt>
              <dd className="mt-0.5 text-foreground">{user?.role === "ADMIN" ? "IT Department Head" : "IT Team Member"}</dd>
            </div>
            <div>
              <dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground">Department</dt>
              <dd className="mt-0.5 text-foreground">{user?.department}</dd>
            </div>
          </dl>
        </div>

        <form onSubmit={submit} className="flex flex-col gap-3 rounded-lg border border-border bg-card p-6">
          <h2 className="text-sm font-bold">Change password</h2>
          <div className="flex flex-col gap-1">
            <label className="text-xs font-medium text-secondary-foreground">Current password</label>
            <input required type="password" autoComplete="current-password" value={currentPassword} onChange={(event) => setCurrentPassword(event.target.value)} className={inputClass} />
          </div>
          <div className="flex flex-col gap-1">
            <label className="text-xs font-medium text-secondary-foreground">New password</label>
            <input required minLength={8} type="password" autoComplete="new-password" value={newPassword} onChange={(event) => setNewPassword(event.target.value)} className={inputClass} />
          </div>
          <div className="flex flex-col gap-1">
            <label className="text-xs font-medium text-secondary-foreground">Confirm new password</label>
            <input required minLength={8} type="password" autoComplete="new-password" value={confirmPassword} onChange={(event) => setConfirmPassword(event.target.value)} className={inputClass} />
          </div>
          <p className="text-xs text-muted-foreground">At least 8 characters.</p>
          {error && <p className="text-xs font-medium text-destructive">{error}</p>}
          {success && <p className="text-xs font-medium text-success">Password changed.</p>}
          <button
            type="submit"
            disabled={mutation.isPending}
            className="w-fit rounded-md bg-primary px-3.5 py-2 text-xs font-semibold text-primary-foreground disabled:opacity-60"
          >
            {mutation.isPending ? "Saving..." : "Change password"}
          </button>
        </form>
      </div>
    </AppLayout>
  )
}
