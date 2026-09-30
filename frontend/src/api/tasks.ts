import { apiClient } from "./client"
import type { AssignmentUpdate, DueDateChange, DueDateHistory, PagedResponse, ReassignmentHistoryItem, ReassignmentRequest, Task, TaskActivity, TaskCategory, TaskComment, TaskListParams, TaskRequest } from "@/types/tasks"

export async function listTasks(params: TaskListParams): Promise<PagedResponse<Task>> {
  const response = await apiClient.get<PagedResponse<Task>>("/tasks", { params })
  return response.data
}

export async function getTask(id: number): Promise<Task> {
  const response = await apiClient.get<Task>(`/tasks/${id}`)
  return response.data
}

export async function listTaskCategories(): Promise<TaskCategory[]> {
  const response = await apiClient.get<TaskCategory[]>("/task-categories")
  return response.data
}

// An empty date input's value is "", which the backend's LocalDate field can't parse (fails at
// JSON deserialization, before validation even runs) - null means "not set yet" (DRAFT).
function withNormalizedDueDate(request: TaskRequest) {
  return { ...request, dueDate: request.dueDate || null }
}

export async function createTask(request: TaskRequest): Promise<Task> {
  const response = await apiClient.post<Task>("/tasks", withNormalizedDueDate(request))
  return response.data
}

export async function updateTask(id: number, request: TaskRequest): Promise<Task> {
  const response = await apiClient.put<Task>(`/tasks/${id}`, withNormalizedDueDate(request))
  return response.data
}

export async function cancelTask(id: number): Promise<void> {
  await apiClient.delete(`/tasks/${id}`)
}

export async function reopenTask(id: number, keepProgress: boolean): Promise<Task> {
  const response = await apiClient.post<Task>(`/tasks/${id}/reopen`, { keepProgress })
  return response.data
}

export async function reinstateTask(id: number): Promise<Task> {
  const response = await apiClient.post<Task>(`/tasks/${id}/reinstate`)
  return response.data
}

export async function updateMyAssignment(id: number, update: AssignmentUpdate): Promise<Task> {
  const response = await apiClient.put<Task>(`/tasks/${id}/assignment`, update)
  return response.data
}

export async function adjustAssignment(taskId: number, assignmentId: number, update: AssignmentUpdate): Promise<Task> {
  const response = await apiClient.put<Task>(`/tasks/${taskId}/assignments/${assignmentId}`, update)
  return response.data
}

export async function failAssignment(taskId: number, assignmentId: number, reason: string): Promise<Task> {
  const response = await apiClient.post<Task>(`/tasks/${taskId}/assignments/${assignmentId}/fail`, { reason })
  return response.data
}

export async function changeDueDate(taskId: number, change: DueDateChange): Promise<Task> {
  const response = await apiClient.put<Task>(`/tasks/${taskId}/due-date`, change)
  return response.data
}

export async function getDueDateHistory(taskId: number): Promise<DueDateHistory> {
  const response = await apiClient.get<DueDateHistory>(`/tasks/${taskId}/due-date-history`)
  return response.data
}

export async function reassignTask(taskId: number, request: ReassignmentRequest): Promise<Task> {
  const response = await apiClient.post<Task>(`/tasks/${taskId}/reassign`, request)
  return response.data
}

export async function getReassignmentHistory(taskId: number): Promise<ReassignmentHistoryItem[]> {
  const response = await apiClient.get<ReassignmentHistoryItem[]>(`/tasks/${taskId}/reassignment-history`)
  return response.data
}

export async function getTaskActivity(taskId: number): Promise<TaskActivity[]> {
  const response = await apiClient.get<TaskActivity[]>(`/tasks/${taskId}/activity`)
  return response.data
}

export async function getTaskComments(taskId: number): Promise<TaskComment[]> {
  const response = await apiClient.get<TaskComment[]>(`/tasks/${taskId}/comments`)
  return response.data
}

export async function addTaskComment(taskId: number, comment: string): Promise<TaskComment> {
  const response = await apiClient.post<TaskComment>(`/tasks/${taskId}/comments`, { comment })
  return response.data
}
