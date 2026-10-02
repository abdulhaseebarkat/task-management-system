import { useEffect, useState, type FormEvent } from "react"
import { useSearchParams } from "react-router-dom"
import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { AppLayout } from "@/components/layout/AppLayout"
import { TaskDetail } from "@/components/tasks/TaskDetail"
import { DEFAULT_FILTERS, type FilterValues } from "@/components/tasks/filterState"
import { TaskFilters } from "@/components/tasks/TaskFilters"
import { TaskForm } from "@/components/tasks/TaskForm"
import { TaskTable } from "@/components/tasks/TaskTable"
import { useAuth } from "@/auth/AuthContext"
import { useRealtimeSubscription } from "@/hooks/useRealtimeSubscription"
import { listUsers } from "@/api/users"
import {
  adjustAssignment, cancelTask, createTask, failAssignment, getTask, listTaskCategories, listTasks,
  changeDueDate, reassignTask, reinstateTask, reopenTask, updateMyAssignment, updateTask,
} from "@/api/tasks"
import { apiErrorMessage } from "@/lib/apiError"
import type { AssignmentUpdate, DueDateChange, ReassignmentRequest, Task, TaskListParams, TaskPriority, TaskRequest, TaskStatus } from "@/types/tasks"

const PAGE_SIZE = 20

function emptyForm(categoryId: number): TaskRequest {
  return { title: "", description: "", priority: "MEDIUM", categoryId, assigneeIds: [], dueDate: "" }
}

export function TasksPage({ employee = false }: { employee?: boolean }) {
  const { user } = useAuth()
  const queryClient = useQueryClient()
  const [searchParams, setSearchParams] = useSearchParams()

  // A dashboard chart (or any other deep link) can land here with ?status=/priority=/categoryId=/
  // assigneeId= to pre-apply a filter - captured once from the initial URL, same as taskId below.
  const [filters, setFilters] = useState<FilterValues>(() => ({
    ...DEFAULT_FILTERS,
    status: searchParams.get("status") ?? DEFAULT_FILTERS.status,
    priority: searchParams.get("priority") ?? DEFAULT_FILTERS.priority,
    categoryId: searchParams.get("categoryId") ?? DEFAULT_FILTERS.categoryId,
    assigneeId: searchParams.get("assigneeId") ?? DEFAULT_FILTERS.assigneeId,
  }))
  const [appliedSearch, setAppliedSearch] = useState("")
  const [page, setPage] = useState(0)
  const [view, setView] = useState<"active" | "archive">("active")
  const deepLinkedTaskId = searchParams.get("taskId")
  const [selectedId, setSelectedId] = useState<number | null>(deepLinkedTaskId ? Number(deepLinkedTaskId) : null)
  // Captured once from the initial URL (not re-read from searchParams, which gets cleared right below).
  const [openAssignmentId] = useState<number | null>(() => {
    const raw = searchParams.get("assignmentId")
    return raw ? Number(raw) : null
  })

  // A Point Events link (or any other deep link) landed here with ?taskId=... - select it once, then
  // drop the params so they don't fight with normal in-page selection afterward. The filter params
  // above are read once at mount (in useState initializers), so they're cleared here too rather than
  // left dangling in the address bar once applied.
  useEffect(() => {
    if (deepLinkedTaskId) {
      setSelectedId(Number(deepLinkedTaskId))
    }
    if (deepLinkedTaskId || searchParams.has("status") || searchParams.has("priority") || searchParams.has("categoryId") || searchParams.has("assigneeId")) {
      setSearchParams((params) => {
        params.delete("taskId"); params.delete("assignmentId")
        params.delete("status"); params.delete("priority"); params.delete("categoryId"); params.delete("assigneeId")
        return params
      }, { replace: true })
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [deepLinkedTaskId])
  const [editing, setEditing] = useState<Task | null>(null)
  const [form, setForm] = useState<TaskRequest>(emptyForm(0))
  const [showForm, setShowForm] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)

  const [sortKey, sortDirection] = filters.sort.split(":")
  const params: TaskListParams = {
    page,
    size: PAGE_SIZE,
    sort: sortKey,
    direction: sortDirection as "asc" | "desc",
    search: appliedSearch || undefined,
    status: (filters.status || undefined) as TaskStatus | undefined,
    priority: (filters.priority || undefined) as TaskPriority | undefined,
    categoryId: filters.categoryId ? Number(filters.categoryId) : undefined,
    assigneeId: filters.assigneeId ? Number(filters.assigneeId) : undefined,
    archived: !employee && view === "archive",
  }

  const tasksQuery = useQuery({ queryKey: ["tasks", "list", params], queryFn: () => listTasks(params), placeholderData: keepPreviousData })
  const detailQuery = useQuery({ queryKey: ["tasks", "detail", selectedId], queryFn: () => getTask(selectedId!), enabled: selectedId !== null })
  const categoriesQuery = useQuery({ queryKey: ["task-categories"], queryFn: listTaskCategories })
  const usersQuery = useQuery({ queryKey: ["users"], queryFn: listUsers, enabled: !employee })

  const categories = categoriesQuery.data ?? []
  const teamMembers = (usersQuery.data ?? []).filter((candidate) => candidate.role === "TEAM_MEMBER")

  // Current assignees stay listed (and can be unticked) even if they have since been deactivated.
  const currentAssigneeIds = new Set(editing?.assignments.map((assignment) => assignment.user.id) ?? [])
  const assignable = teamMembers.filter((member) => member.active || currentAssigneeIds.has(member.id))

  function refreshTasks() {
    queryClient.invalidateQueries({ queryKey: ["tasks"] })
  }

  // Someone else (a co-assignee, or the Admin) changed this task while it's open here - refetch
  // its detail instead of waiting for the next navigation/focus refetch.
  useRealtimeSubscription(selectedId !== null ? `/topic/tasks/${selectedId}` : null, () => {
    queryClient.invalidateQueries({ queryKey: ["tasks", "detail", selectedId] })
  })

  const saveMutation = useMutation({
    mutationFn: (request: TaskRequest) => (editing ? updateTask(editing.id, request) : createTask(request)),
    onSuccess: (task) => {
      refreshTasks()
      setSelectedId(task.id)
      setShowForm(false)
      setFormError(null)
    },
    onError: (error) => setFormError(apiErrorMessage(error, "Could not save the task. Check the required fields and assignments.")),
  })

  const cancelMutation = useMutation({
    mutationFn: cancelTask,
    onSuccess: refreshTasks,
    onError: (error) => setActionError(apiErrorMessage(error, "Could not cancel the task.")),
  })

  const reopenMutation = useMutation({
    mutationFn: ({ id, keepProgress }: { id: number; keepProgress: boolean }) => reopenTask(id, keepProgress),
    onSuccess: refreshTasks,
    onError: (error) => setActionError(apiErrorMessage(error, "Could not reopen the task.")),
  })

  const reinstateMutation = useMutation({
    mutationFn: reinstateTask,
    onSuccess: refreshTasks,
    onError: (error) => setActionError(apiErrorMessage(error, "Could not reinstate the task.")),
  })

  const assignmentMutation = useMutation({
    mutationFn: ({ taskId, assignmentId, update }: { taskId: number; assignmentId?: number; update: AssignmentUpdate }) =>
      assignmentId === undefined ? updateMyAssignment(taskId, update) : adjustAssignment(taskId, assignmentId, update),
    onSuccess: () => {
      refreshTasks()
      setActionError(null)
    },
    onError: (error) => setActionError(apiErrorMessage(error, "Could not update the assignment.")),
  })

  const failMutation = useMutation({
    mutationFn: ({ taskId, assignmentId, reason }: { taskId: number; assignmentId: number; reason: string }) =>
      failAssignment(taskId, assignmentId, reason),
    onSuccess: () => { refreshTasks(); setActionError(null) },
    onError: (error) => setActionError(apiErrorMessage(error, "Could not mark this assignment as failed.")),
  })

  const dueDateMutation = useMutation({
    mutationFn: ({ taskId, change }: { taskId: number; change: DueDateChange }) => changeDueDate(taskId, change),
    onSuccess: () => { refreshTasks(); setActionError(null) },
    onError: (error) => setActionError(apiErrorMessage(error, "Could not change the due date.")),
  })

  const reassignmentMutation = useMutation({
    mutationFn: ({ taskId, request }: { taskId: number; request: ReassignmentRequest }) => reassignTask(taskId, request),
    onSuccess: () => { refreshTasks(); setActionError(null) },
    onError: (error) => setActionError(apiErrorMessage(error, "Could not reassign the task.")),
  })

  function changeFilters(patch: Partial<FilterValues>) {
    setFilters((current) => ({ ...current, ...patch }))
    if (Object.keys(patch).some((key) => key !== "searchInput")) setPage(0)
  }

  function applySearch() {
    setAppliedSearch(filters.searchInput.trim())
    setPage(0)
  }

  function resetFilters() {
    setFilters(DEFAULT_FILTERS)
    setAppliedSearch("")
    setPage(0)
  }

  function changeView(next: "active" | "archive") {
    setView(next)
    setSelectedId(null)
    setPage(0)
  }

  function openCreate() {
    setEditing(null)
    setForm(emptyForm(categories[0]?.id ?? 0))
    setFormError(null)
    setShowForm(true)
  }

  function openEdit(task: Task) {
    setEditing(task)
    setForm({
      title: task.title,
      description: task.description ?? "",
      priority: task.priority,
      categoryId: task.category.id,
      assigneeIds: task.assignments.map((assignment) => assignment.user.id),
      dueDate: task.dueDate ?? "",
    })
    setFormError(null)
    setShowForm(true)
  }

  function submit(event: FormEvent) {
    event.preventDefault()
    saveMutation.mutate(form)
  }

  function confirmCancel(task: Task) {
    if (window.confirm(`Cancel task "${task.title}"? Unfinished work will be marked cancelled; completed work is kept. You can reinstate it later.`)) {
      setActionError(null)
      cancelMutation.mutate(task.id)
    }
  }

  return (
    <AppLayout
      title={employee ? "My Tasks" : view === "archive" ? "Archive" : "Tasks"}
      subtitle={employee ? "Work assigned to you" : view === "archive" ? "Cancelled tasks" : "Create, assign, and track department tasks"}
    >
      <div className="flex flex-col gap-5">
        {!employee && (
          <div className="flex gap-2">
            <button
              onClick={() => changeView("active")}
              className={`rounded-md px-3.5 py-2 text-xs font-semibold ${view === "active" ? "bg-primary text-primary-foreground" : "border border-border text-secondary-foreground"}`}
            >
              Tasks
            </button>
            <button
              onClick={() => changeView("archive")}
              className={`rounded-md px-3.5 py-2 text-xs font-semibold ${view === "archive" ? "bg-primary text-primary-foreground" : "border border-border text-secondary-foreground"}`}
            >
              Archive
            </button>
          </div>
        )}

        <div className="flex flex-wrap items-start justify-between gap-3">
          <TaskFilters
            values={filters}
            employee={employee}
            hideStatusFilter={view === "archive"}
            categories={categories}
            teamMembers={teamMembers}
            onChange={changeFilters}
            onSearch={applySearch}
            onReset={resetFilters}
          />
          {!employee && view === "active" && (
            <button onClick={openCreate} className="rounded-md bg-primary px-3.5 py-2 text-xs font-semibold text-primary-foreground">
              + New task
            </button>
          )}
        </div>

        {showForm && !employee && (
          <TaskForm
            heading={editing ? `Edit ${editing.taskNumber}` : "Create task"}
            form={form}
            categories={categories}
            assignable={assignable}
            error={formError}
            saving={saveMutation.isPending}
            onChange={setForm}
            onSubmit={submit}
            onClose={() => setShowForm(false)}
          />
        )}

        <div className="grid gap-3 lg:grid-cols-[minmax(0,1fr)_360px]">
          <TaskTable
            result={tasksQuery.data}
            employee={employee}
            loading={tasksQuery.isLoading}
            selectedId={selectedId}
            cancelling={cancelMutation.isPending}
            onSelect={(task) => {
              setSelectedId(task.id)
              setActionError(null)
            }}
            onCancel={confirmCancel}
            onPage={setPage}
          />

          <TaskDetail
            task={detailQuery.data ?? null}
            employee={employee}
            userId={user?.id}
            openAssignmentId={openAssignmentId ?? undefined}
            error={actionError}
            isSaving={assignmentMutation.isPending || reopenMutation.isPending || reinstateMutation.isPending || dueDateMutation.isPending || reassignmentMutation.isPending || failMutation.isPending}
            onEdit={openEdit}
            onCancel={confirmCancel}
            onReopen={(task, keepProgress) => {
              setActionError(null)
              reopenMutation.mutate({ id: task.id, keepProgress })
            }}
            onReinstate={(task) => {
              setActionError(null)
              reinstateMutation.mutate(task.id)
            }}
            onAssignmentUpdate={(taskId, update) => assignmentMutation.mutate({ taskId, update })}
            onAdminAdjust={(taskId, assignmentId, update) => assignmentMutation.mutate({ taskId, assignmentId, update })}
            onFailAssignment={(taskId, assignmentId, reason) => failMutation.mutate({ taskId, assignmentId, reason })}
            teamMembers={teamMembers.map((member) => ({ id: member.id, name: member.name }))}
            onDueDateChange={(taskId, change) => dueDateMutation.mutate({ taskId, change })}
            onReassign={(taskId, request) => reassignmentMutation.mutate({ taskId, request})}
          />
        </div>
      </div>
    </AppLayout>
  )
}
