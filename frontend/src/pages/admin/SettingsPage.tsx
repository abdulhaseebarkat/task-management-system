import { useState, type FormEvent } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { AppLayout } from "@/components/layout/AppLayout"
import { apiErrorMessage } from "@/lib/apiError"
import { createCategory, listCategoriesForManagement, renameCategory, setCategoryActive, type CategoryManagement } from "@/api/categories"

const inputClass = "rounded-md border border-input bg-background px-2.5 py-1.5 text-sm outline-none focus:border-primary"

export function SettingsPage() {
  const queryClient = useQueryClient()
  const { data: categories, isLoading } = useQuery({ queryKey: ["categories", "manage"], queryFn: listCategoriesForManagement })

  const [showForm, setShowForm] = useState(false)
  const [name, setName] = useState("")
  const [formError, setFormError] = useState<string | null>(null)
  const [editingId, setEditingId] = useState<number | null>(null)
  const [editingName, setEditingName] = useState("")
  const [editError, setEditError] = useState<string | null>(null)

  function refresh() {
    queryClient.invalidateQueries({ queryKey: ["categories"] })
  }

  const createMutation = useMutation({
    mutationFn: createCategory,
    onSuccess: () => {
      refresh()
      setName("")
      setShowForm(false)
      setFormError(null)
    },
    onError: (error) => setFormError(apiErrorMessage(error, "Could not create this category.")),
  })

  const renameMutation = useMutation({
    mutationFn: ({ id, name }: { id: number; name: string }) => renameCategory(id, name),
    onSuccess: () => {
      refresh()
      setEditingId(null)
      setEditError(null)
    },
    onError: (error) => setEditError(apiErrorMessage(error, "Could not rename this category.")),
  })

  const statusMutation = useMutation({
    mutationFn: ({ id, active }: { id: number; active: boolean }) => setCategoryActive(id, active),
    onSuccess: refresh,
  })

  function handleCreate(event: FormEvent) {
    event.preventDefault()
    createMutation.mutate(name.trim())
  }

  function startEdit(category: CategoryManagement) {
    setEditingId(category.id)
    setEditingName(category.name)
    setEditError(null)
  }

  function submitEdit(event: FormEvent) {
    event.preventDefault()
    if (editingId !== null) renameMutation.mutate({ id: editingId, name: editingName.trim() })
  }

  return (
    <AppLayout title="Settings" subtitle="Manage task categories">
      <div className="flex flex-col gap-5">
        <div className="flex items-center justify-between">
          <p className="text-sm text-muted-foreground">{categories?.length ?? 0} categories</p>
          <button
            onClick={() => setShowForm((v) => !v)}
            className="rounded-md bg-primary px-3.5 py-2 text-xs font-semibold text-primary-foreground"
          >
            {showForm ? "Cancel" : "+ Add category"}
          </button>
        </div>

        {showForm && (
          <form onSubmit={handleCreate} className="flex flex-wrap items-end gap-3 rounded-lg border border-border bg-card p-4">
            <div className="flex flex-col gap-1">
              <label className="text-xs font-medium text-secondary-foreground">Name</label>
              <input required maxLength={100} value={name} onChange={(event) => setName(event.target.value)} className={inputClass} />
            </div>
            <button
              type="submit"
              disabled={createMutation.isPending || !name.trim()}
              className="rounded-md bg-primary px-3.5 py-2 text-xs font-semibold text-primary-foreground disabled:opacity-60"
            >
              {createMutation.isPending ? "Creating..." : "Create category"}
            </button>
            {formError && <p className="w-full text-xs font-medium text-destructive">{formError}</p>}
          </form>
        )}

        <div className="overflow-hidden rounded-lg border border-border bg-card">
          <table className="w-full border-collapse text-sm">
            <thead>
              <tr className="border-b border-border text-left text-[11px] font-semibold uppercase tracking-wide text-muted-foreground">
                <th className="px-4 py-3">Name</th>
                <th className="px-4 py-3">Status</th>
                <th className="px-4 py-3">Action</th>
              </tr>
            </thead>
            <tbody>
              {isLoading && (
                <tr>
                  <td colSpan={3} className="px-4 py-6 text-center text-muted-foreground">Loading...</td>
                </tr>
              )}
              {categories?.length === 0 && !isLoading && (
                <tr>
                  <td colSpan={3} className="px-4 py-6 text-center text-muted-foreground">No categories yet.</td>
                </tr>
              )}
              {categories?.map((category) => (
                <tr key={category.id} className="border-b border-border last:border-0">
                  <td className="px-4 py-3">
                    {editingId === category.id ? (
                      <form onSubmit={submitEdit} className="flex items-center gap-2">
                        <input
                          required
                          autoFocus
                          maxLength={100}
                          value={editingName}
                          onChange={(event) => setEditingName(event.target.value)}
                          className={inputClass}
                        />
                        <button type="submit" disabled={renameMutation.isPending} className="text-xs font-medium text-primary disabled:opacity-60">Save</button>
                        <button type="button" onClick={() => setEditingId(null)} className="text-xs font-medium text-muted-foreground">Cancel</button>
                        {editError && <p className="text-xs font-medium text-destructive">{editError}</p>}
                      </form>
                    ) : (
                      <span className="font-medium text-foreground">{category.name}</span>
                    )}
                  </td>
                  <td className="px-4 py-3">
                    <span className={`inline-flex items-center gap-1.5 text-xs font-semibold ${category.active ? "text-success" : "text-muted-foreground"}`}>
                      <span className={`h-1.5 w-1.5 rounded-full ${category.active ? "bg-success" : "bg-muted-foreground"}`} />
                      {category.active ? "Active" : "Deactivated"}
                    </span>
                  </td>
                  <td className="px-4 py-3">
                    {editingId !== category.id && (
                      <div className="flex gap-3">
                        <button onClick={() => startEdit(category)} className="text-xs font-medium text-primary">Rename</button>
                        <button
                          onClick={() => statusMutation.mutate({ id: category.id, active: !category.active })}
                          disabled={statusMutation.isPending}
                          className="text-xs font-medium text-primary disabled:opacity-60"
                        >
                          {category.active ? "Deactivate" : "Activate"}
                        </button>
                      </div>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <p className="text-xs text-muted-foreground">
          Deactivating a category removes it from the create/edit dropdown for new selections - tasks that already have it keep it untouched.
        </p>
      </div>
    </AppLayout>
  )
}
