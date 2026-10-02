import { label } from "@/components/tasks/taskDisplay"
import type { StatusSlice } from "@/types/analytics"

export interface AssignedVsCompletedRow {
  label: string
  assigned: number
  completed: number
}

/**
 * "Tasks Assigned vs. Completed, by Person" - paired vertical bars per row, matching the shape of
 * the department's old tracker spreadsheet chart. Works for one row (an individual report, "This
 * period") or many (the department report, one per employee).
 */
export function AssignedVsCompletedChart({ rows }: { rows: AssignedVsCompletedRow[] }) {
  const max = Math.max(...rows.map((r) => Math.max(r.assigned, r.completed)), 1)
  const chartHeight = 160
  const groupWidth = 90
  const barWidth = 26
  const gap = 6
  const width = Math.max(rows.length * groupWidth + 40, 200)

  function barHeight(value: number) {
    return value <= 0 ? 0 : Math.max((value / max) * chartHeight, 4)
  }

  return (
    <div>
      <svg width="100%" height={chartHeight + 60} viewBox={`0 0 ${width} ${chartHeight + 60}`}>
        <line x1="10" y1={chartHeight + 20} x2={width - 10} y2={chartHeight + 20} stroke="#c3c2b7" strokeWidth="1" />
        {rows.map((row, i) => {
          const groupX = 20 + i * groupWidth
          const assignedH = barHeight(row.assigned)
          const completedH = barHeight(row.completed)
          const centerX = groupX + (barWidth * 2 + gap) / 2
          return (
            <g key={row.label}>
              <rect x={groupX} y={chartHeight + 20 - assignedH} width={barWidth} height={assignedH} rx="2" fill="#2a78d6" />
              <text x={groupX + barWidth / 2} y={chartHeight + 20 - assignedH - 6} textAnchor="middle" fontSize="11" fontWeight="700" fill="#2a78d6">{row.assigned}</text>
              <rect x={groupX + barWidth + gap} y={chartHeight + 20 - completedH} width={barWidth} height={completedH} rx="2" fill="#d03b3b" />
              <text x={groupX + barWidth + gap + barWidth / 2} y={chartHeight + 20 - completedH - 6} textAnchor="middle" fontSize="11" fontWeight="700" fill="#d03b3b">{row.completed}</text>
              <text x={centerX} y={chartHeight + 38} textAnchor="middle" fontSize="11" fill="#52514e">{row.label}</text>
            </g>
          )
        })}
      </svg>
      <div className="mt-2 flex justify-center gap-5 text-xs">
        <span className="flex items-center gap-1.5"><span className="h-2.5 w-2.5 rounded-sm" style={{ backgroundColor: "#2a78d6" }} />Tasks Assigned</span>
        <span className="flex items-center gap-1.5"><span className="h-2.5 w-2.5 rounded-sm" style={{ backgroundColor: "#d03b3b" }} />Tasks Completed By Them</span>
      </div>
    </div>
  )
}

export interface NamedCountRow { employeeId: number; employeeName: string; count: number }

/**
 * Flat, single-color vertical bar chart - one bar per employee, for simple "how many of X per
 * employee" widgets (due-date extensions, reassignments away, etc). Plain HTML/CSS, not SVG, so
 * value labels sit in normal document flow and can never be clipped, however tall the leading bar
 * is - the same structural fix applied to the Employee Performance chart.
 */
export function EmployeeCountBarChart({ rows, color, emptyMessage, onSelect }: { rows: NamedCountRow[]; color: string; emptyMessage: string; onSelect?: (employeeId: number) => void }) {
  const max = Math.max(...rows.map((r) => r.count), 1)
  const chartHeight = 100

  if (rows.length === 0) {
    return <p className="mt-3 text-xs text-muted-foreground">{emptyMessage}</p>
  }

  return (
    <div className="flex items-end justify-center gap-4 overflow-x-auto px-2 pb-1 pt-6">
      {rows.map((row) => {
        const barHeight = row.count <= 0 ? 3 : Math.max((row.count / max) * chartHeight, 4)
        return (
          <div
            key={row.employeeId}
            className={`flex w-[88px] flex-shrink-0 flex-col items-center rounded-md p-1 ${onSelect ? "cursor-pointer transition-colors hover:bg-accent" : ""}`}
            onClick={onSelect ? () => onSelect(row.employeeId) : undefined}
            title={onSelect ? `View ${row.employeeName}'s tasks` : undefined}
          >
            <span className="text-sm font-bold leading-none">{row.count}</span>
            <div className="mt-1.5 flex w-full items-end justify-center" style={{ height: chartHeight }}>
              <div className="w-7 rounded-t-sm" style={{ height: barHeight, backgroundColor: color }} />
            </div>
            <span className="mt-2 w-full truncate text-center text-xs font-medium" title={row.employeeName}>{row.employeeName}</span>
          </div>
        )
      })}
    </div>
  )
}

/** "Employee | Count" table, shown alongside EmployeeCountBarChart for the same underlying data. */
export function EmployeeCountTable({ rows, countLabel, onSelect }: { rows: NamedCountRow[]; countLabel: string; onSelect?: (employeeId: number) => void }) {
  return (
    <table className="w-full text-xs">
      <thead>
        <tr className="text-left text-[11px] uppercase tracking-wide text-muted-foreground">
          <th className="pb-1.5 font-semibold">Employee</th>
          <th className="pb-1.5 text-right font-semibold">{countLabel}</th>
        </tr>
      </thead>
      <tbody>
        {rows.length === 0 && <tr><td colSpan={2} className="py-1.5 text-muted-foreground">None</td></tr>}
        {rows.map((row) => (
          <tr
            key={row.employeeId}
            className={`border-t border-border first:border-0 ${onSelect ? "cursor-pointer hover:bg-accent" : ""}`}
            onClick={onSelect ? () => onSelect(row.employeeId) : undefined}
          >
            <td className="py-1.5">{row.employeeName}</td>
            <td className="py-1.5 text-right font-semibold">{row.count}</td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}

/** The "Status | Count" breakdown paired with the chart above, plus a live Overdue count the status distribution itself doesn't carry (overdue isn't a TaskStatus value). */
export function StatusBreakdownTable({ statusDistribution, overdue }: { statusDistribution: StatusSlice[]; overdue: number }) {
  return (
    <table className="w-full text-xs">
      <thead>
        <tr className="text-left text-[11px] uppercase tracking-wide text-muted-foreground">
          <th className="pb-1.5 font-semibold">Status</th>
          <th className="pb-1.5 text-right font-semibold">Count</th>
        </tr>
      </thead>
      <tbody>
        {statusDistribution.map((s) => (
          <tr key={s.status} className="border-t border-border first:border-0">
            <td className="py-1.5">{label(s.status)}</td>
            <td className="py-1.5 text-right font-semibold">{s.count}</td>
          </tr>
        ))}
        <tr className="border-t border-border">
          <td className="py-1.5 font-semibold text-destructive">Overdue (live)</td>
          <td className="py-1.5 text-right font-semibold text-destructive">{overdue}</td>
        </tr>
      </tbody>
    </table>
  )
}
