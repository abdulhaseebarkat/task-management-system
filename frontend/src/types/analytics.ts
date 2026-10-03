import type { TaskPriority, TaskStatus } from "./tasks"

export interface AnalyticsFilters {
  from?: string
  to?: string
  employeeId?: number
  status?: TaskStatus
  priority?: TaskPriority
  categoryId?: number
}

export interface DashboardSummary {
  totalTasks: number
  active: number
  completed: number
  overdue: number
  blocked: number
  onHold: number
  reassigned: number
  completionRate: number
}

export interface StatusSlice { status: string; count: number; percent: number }
export interface EmployeeCount { employeeId: number; employeeName: string; count: number }
export interface PriorityCount { priority: string; count: number }
export interface CategoryCount { categoryId: number; categoryName: string; count: number }
export interface WeekCount { weekLabel: string; weekStart: string; count: number }

/**
 * Sorted by efficiencyRate descending - the fair, comparable ranking (raw points shown alongside,
 * never used to rank). pointsEarned is completed work only; pointsPossible is live (open tasks
 * included at full value); pointsLost is already-forfeited points, open or resolved.
 */
export interface PointsLeaderboardEntry { employeeId: number; employeeName: string; pointsEarned: number; pointsPossible: number; pointsLost: number; efficiencyRate: number }
export interface MonthlyRate { monthLabel: string; monthStart: string; rate: number }
/** level is "0", "1", "2" or "FAILED". */
export interface StrikeDistributionSlice { level: string; count: number; percent: number }
export interface AtRiskTaskItem { taskId: number; taskNumber: string; title: string; assignmentId: number; employeeName: string; strikeLevel: number; dueDate: string | null }
export interface PointEventItem {
  occurredAt: string
  taskId: number
  taskNumber: string | null
  title: string
  assignmentId: number
  eventType: "ASSIGNED" | "STRIKE_1" | "STRIKE_2" | "FAILED" | "COMPLETED" | "CANCELLED" | "REASSIGNED" | "REBASED"
  pointsDelta: number
  resultingPoints: number
  reason: string | null
}

export interface DashboardAnalytics {
  from: string
  to: string
  summary: DashboardSummary
  statusDistribution: StatusSlice[]
  teamWorkload: EmployeeCount[]
  priorityDistribution: PriorityCount[]
  completionTrend: WeekCount[]
  overdueTrend: WeekCount[]
  employeeCompletion: EmployeeCount[]
  /** Tasks created in [from, to] where this employee is (or ever was) an assignee - pairs with employeeCompletion for the "Tasks Assigned vs. Completed, by Person" chart. Never includes the Admin. */
  employeeAssigned: EmployeeCount[]
  categoryDistribution: CategoryCount[]
  pointsLeaderboard: PointsLeaderboardEntry[]
  teamEfficiencyTrend: MonthlyRate[]
  strikeDistribution: StrikeDistributionSlice[]
  atRiskTasks: AtRiskTaskItem[]
  /** Per employee, distinct tasks created in [from, to] that have had their due date extended by the Admin at least once - whether or not that extension deducted points. */
  employeeDueDateExtensions: EmployeeCount[]
  /** Per employee, how many times a task has been reassigned away from them (the "from" side) in [from, to]. */
  employeeReassignedAway: EmployeeCount[]
}

/** Backs the per-task "Points — This Task" panel (Admin-only), scoped to one assignment. */
export interface TaskPoints {
  taskId: number
  taskNumber: string
  title: string
  priority: string
  assignmentId: number
  userId: number
  userName: string
  basePoints: number
  resultingPoints: number
  outcome: "OPEN" | "COMPLETED" | "FAILED" | "CANCELLED" | "REASSIGNED"
  events: { occurredAt: string; eventType: string; pointsDelta: number; resultingPoints: number; reason: string | null }[]
}
