import type { AuthUser } from "./auth"

export type TaskPriority = "LOW" | "MEDIUM" | "HIGH" | "CRITICAL"
export type TaskStatus = "DRAFT" | "ASSIGNED" | "IN_PROGRESS" | "ON_HOLD" | "BLOCKED" | "COMPLETED" | "CANCELLED" | "REOPENED" | "FAILED"
export type AssignmentStatus = "ASSIGNED" | "IN_PROGRESS" | "ON_HOLD" | "BLOCKED" | "COMPLETED" | "REASSIGNED" | "CANCELLED" | "REMOVED"

export interface TaskCategory {
  id: number
  name: string
}

/** Deliberately just id + name: the API never sends emails or codes for the people on a task. */
export interface Assignee {
  id: number
  name: string
}

export interface TaskAssignment {
  id: number
  user: Assignee
  status: AssignmentStatus
  progress: number
  reason: string | null
}

export interface Task {
  id: number
  taskNumber: string
  title: string
  description: string | null
  priority: TaskPriority
  category: TaskCategory
  status: TaskStatus
  overallProgress: number
  dueDate: string | null
  originalDueDate?: string | null
  dueDateExtensions?: number
  reassignmentCount?: number
  /** Only sent to Admins. */
  createdBy: AuthUser | null
  assignments: TaskAssignment[]
}

/** Task status is not part of the request: it is derived from assignments, or changed via cancel/reopen/reinstate. */
export interface TaskRequest {
  title: string
  description: string
  priority: TaskPriority
  categoryId: number
  assigneeIds: number[]
  dueDate: string
}

export interface AssignmentUpdate {
  status: AssignmentStatus
  progress: number
  reason?: string
}

export interface DueDateChange { dueDate: string; reason: string; deductPoints: boolean }
export interface ReassignmentRequest {
  fromAssignmentId: number
  toUserId: number
  reason: string
  classification?: "NEUTRAL_ADMINISTRATIVE" | "PERFORMANCE_RELATED" | "OPERATIONAL" | "OTHER"
  keepProgress: boolean
  /** Admin's choice: should the original assignee lose points for this reassignment? Defaults to false (neutral) when omitted. */
  deductPoints: boolean
}
export interface DueDateHistoryItem { id: number; previousDueDate: string | null; newDueDate: string; kind: string; reason: string | null; changedBy: Assignee | null; changedAt: string; countsTowardStrikes: boolean }
export interface DueDateHistory { originalDueDate: string | null; currentDueDate: string | null; extensionCount: number; changes: DueDateHistoryItem[] }
export interface ReassignmentHistoryItem { id: number; createdAt: string; fromUser: Assignee; toUser: Assignee; reassignedBy: Assignee; reason: string; classification: string | null; fromStatus: AssignmentStatus; fromProgress: number; toStatus: AssignmentStatus; toProgress: number }
export interface TaskActivity { id: number; occurredAt: string; type: string; message: string; actorName: string | null }

export interface PagedResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface TaskListParams {
  page: number
  size: number
  sort: string
  direction: "asc" | "desc"
  search?: string
  status?: TaskStatus
  priority?: TaskPriority
  categoryId?: number
  assigneeId?: number
  /** Archive tab (Admin-only): cancelled tasks only. Default false - the normal list never includes a cancelled task. */
  archived?: boolean
}

export interface TaskComment {
  id: number
  author: Assignee
  comment: string
  createdAt: string
}
