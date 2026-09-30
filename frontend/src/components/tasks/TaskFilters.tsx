import type { FormEvent } from "react"
import type { TaskCategory } from "@/types/tasks"
import type { FilterValues } from "./filterState"
import { label } from "./taskDisplay"

// CANCELLED lives only in the Archive tab now, never the main list. DRAFT is Admin-only (a team
// member never has one to filter to), so it's excluded from their dropdown too.
const ADMIN_STATUSES = ["DRAFT", "ASSIGNED", "IN_PROGRESS", "ON_HOLD", "BLOCKED", "FAILED", "COMPLETED", "REOPENED"]
const EMPLOYEE_STATUSES = ["ASSIGNED", "IN_PROGRESS", "ON_HOLD", "BLOCKED", "FAILED", "COMPLETED", "REOPENED"]
const PRIORITIES = ["LOW", "MEDIUM", "HIGH", "CRITICAL"]

const SORT_OPTIONS = [
  { value: "updated:desc", label: "Recently updated" },
  { value: "created:desc", label: "Newest first" },
  { value: "due:asc", label: "Due date (soonest)" },
  { value: "priority:desc", label: "Priority (highest first)" },
  { value: "progress:desc", label: "Progress (highest)" },
  { value: "title:asc", label: "Title (A-Z)" },
]

interface TaskFiltersProps {
  values: FilterValues
  employee: boolean
  /** Archive view: every result is already CANCELLED, so the status filter has nothing useful to do. */
  hideStatusFilter?: boolean
  categories: TaskCategory[]
  teamMembers: { id: number; name: string }[]
  onChange: (patch: Partial<FilterValues>) => void
  onSearch: () => void
  onReset: () => void
}

const selectClass = "rounded-md border border-input bg-card px-2.5 py-2 text-xs"

export function TaskFilters({ values, employee, hideStatusFilter, categories, teamMembers, onChange, onSearch, onReset }: TaskFiltersProps) {
  function submit(event: FormEvent) {
    event.preventDefault()
    onSearch()
  }

  return (
    <div className="flex flex-wrap items-center gap-2">
      <form onSubmit={submit} className="flex gap-1">
        <input
          type="search"
          aria-label="Search tasks"
          placeholder="Search title, number or description"
          value={values.searchInput}
          onChange={(event) => onChange({ searchInput: event.target.value })}
          className="w-64 rounded-md border border-input bg-card px-2.5 py-2 text-xs"
        />
        <button type="submit" className="rounded-md border border-border px-3 py-2 text-xs font-semibold">Search</button>
      </form>

      {!hideStatusFilter && (
        <select aria-label="Filter by status" value={values.status} onChange={(event) => onChange({ status: event.target.value })} className={selectClass}>
          <option value="">All statuses</option>
          {(employee ? EMPLOYEE_STATUSES : ADMIN_STATUSES).map((status) => <option key={status} value={status}>{label(status)}</option>)}
        </select>
      )}

      {!employee && (
        <>
          <select aria-label="Filter by priority" value={values.priority} onChange={(event) => onChange({ priority: event.target.value })} className={selectClass}>
            <option value="">All priorities</option>
            {PRIORITIES.map((priority) => <option key={priority} value={priority}>{label(priority)}</option>)}
          </select>
          <select aria-label="Filter by category" value={values.categoryId} onChange={(event) => onChange({ categoryId: event.target.value })} className={selectClass}>
            <option value="">All categories</option>
            {categories.map((category) => <option key={category.id} value={category.id}>{category.name}</option>)}
          </select>
          <select aria-label="Filter by employee" value={values.assigneeId} onChange={(event) => onChange({ assigneeId: event.target.value })} className={selectClass}>
            <option value="">All employees</option>
            {teamMembers.map((member) => <option key={member.id} value={member.id}>{member.name}</option>)}
          </select>
        </>
      )}

      <select aria-label="Sort tasks" value={values.sort} onChange={(event) => onChange({ sort: event.target.value })} className={selectClass}>
        {SORT_OPTIONS.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
      </select>

      <button type="button" onClick={onReset} className="text-xs font-semibold text-primary">Clear</button>
    </div>
  )
}
