import type { FormEvent, ReactNode } from "react"
import type { TaskCategory, TaskPriority, TaskRequest } from "@/types/tasks"
import { label } from "./taskDisplay"

const PRIORITIES: TaskPriority[] = ["LOW", "MEDIUM", "HIGH", "CRITICAL"]
const inputClass = "rounded-md border border-input bg-background px-2.5 py-2 text-sm"

export interface AssignableUser {
  id: number
  name: string
  active: boolean
}

interface TaskFormProps {
  heading: string
  form: TaskRequest
  categories: TaskCategory[]
  assignable: AssignableUser[]
  error: string | null
  saving: boolean
  onChange: (form: TaskRequest) => void
  onSubmit: (event: FormEvent) => void
  onClose: () => void
}

function Field({ label: text, className, children }: { label: string; className?: string; children: ReactNode }) {
  return (
    <label className={`flex flex-col gap-1 text-xs font-semibold text-secondary-foreground ${className ?? ""}`}>
      {text}
      {children}
    </label>
  )
}

export function TaskForm({ heading, form, categories, assignable, error, saving, onChange, onSubmit, onClose }: TaskFormProps) {
  function toggleAssignee(userId: number, checked: boolean) {
    onChange({
      ...form,
      assigneeIds: checked ? [...form.assigneeIds, userId] : form.assigneeIds.filter((id) => id !== userId),
    })
  }

  return (
    <form onSubmit={onSubmit} className="grid gap-3 rounded-lg border border-border bg-card p-4 md:grid-cols-2">
      <h2 className="text-sm font-bold md:col-span-2">{heading}</h2>

      <Field label="Title" className="md:col-span-2">
        <input required maxLength={200} value={form.title} onChange={(event) => onChange({ ...form, title: event.target.value })} className={inputClass} />
      </Field>
      <Field label="Description" className="md:col-span-2">
        <textarea value={form.description} onChange={(event) => onChange({ ...form, description: event.target.value })} className={`min-h-24 ${inputClass}`} />
      </Field>
      <Field label="Priority">
        <select value={form.priority} onChange={(event) => onChange({ ...form, priority: event.target.value as TaskPriority })} className={inputClass}>
          {PRIORITIES.map((priority) => (
            <option key={priority} value={priority}>{label(priority)}</option>
          ))}
        </select>
      </Field>
      <Field label="Category">
        <select required value={form.categoryId || ""} onChange={(event) => onChange({ ...form, categoryId: Number(event.target.value) })} className={inputClass}>
          <option value="">Choose a category</option>
          {categories.map((category) => (
            <option key={category.id} value={category.id}>{category.name}</option>
          ))}
        </select>
      </Field>
      <Field label="Due date">
        <input type="date" value={form.dueDate} onChange={(event) => onChange({ ...form, dueDate: event.target.value })} className={inputClass} />
      </Field>

      {(!form.dueDate || form.assigneeIds.length === 0) && (
        <p className="text-xs text-muted-foreground md:col-span-2">
          Leave the due date and/or assignees blank to save this as a Draft - visible only to you until both are filled in.
        </p>
      )}

      <fieldset className="md:col-span-2">
        <legend className="mb-2 text-xs font-semibold text-secondary-foreground">Assign team members</legend>
        <div className="grid gap-2 sm:grid-cols-2">
          {assignable.map((member) => (
            <label key={member.id} className="flex items-center gap-2 text-sm">
              <input type="checkbox" checked={form.assigneeIds.includes(member.id)} onChange={(event) => toggleAssignee(member.id, event.target.checked)} />
              {member.name}
              {!member.active && <span className="text-xs text-muted-foreground">(deactivated)</span>}
            </label>
          ))}
        </div>
      </fieldset>

      {error && <p className="text-xs text-destructive md:col-span-2">{error}</p>}
      <div className="flex gap-2 md:col-span-2">
        <button type="submit" disabled={saving} className="rounded-md bg-primary px-3.5 py-2 text-xs font-semibold text-primary-foreground disabled:opacity-60">
          {saving ? "Saving..." : "Save task"}
        </button>
        <button type="button" onClick={onClose} className="rounded-md border border-border px-3.5 py-2 text-xs font-semibold">
          Cancel
        </button>
      </div>
    </form>
  )
}
