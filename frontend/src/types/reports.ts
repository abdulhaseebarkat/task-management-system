import type { CategoryCount, MonthlyRate, PointEventItem, PriorityCount, StatusSlice } from "./analytics"

export interface MonthCount { monthLabel: string; monthStart: string; count: number }

export interface WorkloadItem {
  taskId: number
  taskNumber: string
  title: string
  assignmentId: number
  priority: string
  status: string
  progress: number
  dueDate: string | null
}

export interface ReassignedItem {
  taskId: number
  taskNumber: string | null
  title: string
  assignmentId: number
  reassignedAt: string
  toUserName: string
  reason: string
}

export interface ActivityItem {
  occurredAt: string
  taskNumber: string | null
  title: string
  oldStatus: string | null
  newStatus: string
  comment: string | null
}

export interface EmployeeReportSummary {
  assigned: number
  completed: number
  active: number
  overdue: number
  blocked: number
  onHold: number
  cancelled: number
  reassignedAway: number
  completionRate: number
  avgCompletionDays: number | null
}

/**
 * pointsEarned counts ONLY completed work; pointsPossible is live (open tasks included at full
 * value, the moment they're assigned); pointsLost is what's already been genuinely forfeited (open
 * or resolved). efficiencyRate = earned / possible - still-open work lowers this until it's done.
 */
export interface PointsSummary { pointsEarned: number; pointsPossible: number; pointsLost: number; efficiencyRate: number; tasksFailed: number }
/** level is "FULL", "STRIKE_1", "STRIKE_2" or "FAILED". */
export interface PointsBreakdownRow { level: string; count: number; percent: number }

export interface DueDateEntry { dueDate: string; first: boolean; countedTowardStrikes: boolean }

/** One row per (task, this employee's assignment) - backs the "Individual Tasks" table in the exported reports. */
export interface TaskDetailRow {
  taskId: number
  taskNumber: string
  title: string
  priority: string
  assignedAt: string
  status: string
  dueDates: DueDateEntry[]
  possiblePoints: number
  deductedPoints: number
}

export interface EmployeeReport {
  employeeId: number
  employeeName: string
  from: string
  to: string
  summary: EmployeeReportSummary
  statusDistribution: StatusSlice[]
  priorityDistribution: PriorityCount[]
  categoryDistribution: CategoryCount[]
  completionTrend: MonthCount[]
  currentWorkload: WorkloadItem[]
  reassignedTasks: ReassignedItem[]
  recentActivity: ActivityItem[]
  pointsSummary: PointsSummary
  pointsBreakdown: PointsBreakdownRow[]
  monthlyPointsTrend: MonthlyRate[]
  pointEvents: PointEventItem[]
  taskDetails: TaskDetailRow[]
}
