# API

Base path: `/api`. All responses are JSON. Errors follow a uniform shape (see below) — never a raw stack trace.

## Implemented

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/api/health` | none | Liveness check for the frontend/Docker to confirm the backend is reachable |
| POST | `/api/auth/login` | none | Returns a JWT + the authenticated user |
| GET | `/api/auth/me` | any authenticated user | Current user, resolved from the token |
| PUT | `/api/auth/password` | any authenticated user | Change your own password (Admin or team member) - body `{ "currentPassword", "newPassword" }` |
| GET | `/api/users` | ADMIN | List all accounts |
| GET | `/api/users/{id}` | ADMIN | Get one account |
| POST | `/api/users` | ADMIN | Create an account; returns a one-time temporary password |
| PUT | `/api/users/{id}` | ADMIN | Update name/department |
| PUT | `/api/users/{id}/status` | ADMIN | Activate/deactivate an account |
| GET | `/api/task-categories` | any authenticated user | Active categories only - for task-creation/filter dropdowns |
| GET | `/api/task-categories/manage` | ADMIN | Every category, including inactive ones |
| POST | `/api/task-categories` | ADMIN | Create a category (`{ "name" }`, unique case-insensitively) |
| PUT | `/api/task-categories/{id}` | ADMIN | Rename a category |
| PUT | `/api/task-categories/{id}/status` | ADMIN | Activate/deactivate a category |

### Password change

`PUT /api/auth/password` requires the caller's **current** password (not just a valid session) before accepting a new one - a left-open browser can't be used to silently lock the real account owner out. `newPassword` must be at least 8 characters and different from the current one. Never invalidates other sessions - JWTs already issued stay valid until they naturally expire (this app is stateless-JWT, no server-side session store to revoke from).

### Category management

Categories are never hard-deleted (a task's foreign key would forbid it anyway) - **deactivate** is the only removal path, identical in spirit to deactivating a user: `GET /api/task-categories` (used for every task-creation/filter dropdown) immediately stops offering it, but every task that already has it keeps it untouched. Editing an unrelated field on such a task still works - the category is only re-validated as "must be active" when it's actually being *changed* to something else, not when it's staying the same.

## Task management

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/api/tasks` | ADMIN / TEAM_MEMBER | Paged, searchable, filterable, sortable list (see below). Admin: all non-cancelled, non-archived tasks; employee: only their own non-DRAFT tasks. `archived=true` switches to the Archive tab (cancelled tasks only, Admin-only in the UI) |
| GET | `/api/tasks/{id}` | ADMIN / assigned TEAM_MEMBER | Task detail. Anyone not on the task gets **404**, identical to a task that does not exist |
| POST | `/api/tasks` | ADMIN | Create a task and assign zero or more team members. Status is derived, never sent |
| PUT | `/api/tasks/{id}` | ADMIN | Edit task fields and current assignees (409 if the task is cancelled) |
| DELETE | `/api/tasks/{id}` | ADMIN | Cancel a task (204; idempotent). Not a hard delete |
| POST | `/api/tasks/{id}/reopen` | ADMIN | Reopen a COMPLETED task. Body `{ "keepProgress": true \| false }` (omitted = reset) |
| POST | `/api/tasks/{id}/reinstate` | ADMIN | Bring a CANCELLED task back instead of re-creating it |
| PUT | `/api/tasks/{id}/assignment` | assigned TEAM_MEMBER | Update own status / progress / reason (rules below) |
| PUT | `/api/tasks/{id}/assignments/{assignmentId}` | ADMIN | Adjust one person's status / progress / reason (same rules) |
| POST | `/api/tasks/{id}/assignments/{assignmentId}/fail` | ADMIN | Mark one still-current assignment as FAILED directly (reason required) |
| PUT | `/api/tasks/{id}/due-date` | ADMIN | Change the due date (reason **required**); recorded in `task_due_date_history` |
| GET | `/api/tasks/{id}/due-date-history` | ADMIN / assigned TEAM_MEMBER | Full due-date change history for the task |
| POST | `/api/tasks/{id}/reassign` | ADMIN | Move one person's unfinished work to someone else, with reason + history |
| GET | `/api/tasks/{id}/reassignment-history` | ADMIN | Full reassignment chain for the task |
| GET | `/api/tasks/{id}/activity` | ADMIN / assigned TEAM_MEMBER | Human-readable per-task timeline, filtered per viewer (see below) |
| GET | `/api/task-categories` | authenticated | List active task categories |
| GET | `/api/audit-logs` | ADMIN | Global, paged, filterable (action/actor/task/date range) audit trail |
| GET | `/api/tasks/{id}/comments` | ADMIN / assigned TEAM_MEMBER | One shared thread per task - every current assignee and the Admin see the same messages |
| POST | `/api/tasks/{id}/comments` | ADMIN / assigned TEAM_MEMBER | Post a comment (`{ "comment" }`, non-blank, max 4000). Notifies the other side (Admin↔assignees) and pushes a live refresh (see Real-time) |
| GET | `/api/notifications` | any authenticated user | Own notifications, paged, newest first |
| GET | `/api/notifications/unread-count` | any authenticated user | `{ "count" }` |
| PUT | `/api/notifications/{id}/read` | any authenticated user | Mark one notification read (404 if it isn't yours) |
| PUT | `/api/notifications/read-all` | any authenticated user | Mark every notification read |
| GET | `/api/analytics/dashboard` | ADMIN | Admin dashboard KPIs and chart data (see below) |
| GET | `/api/analytics/employee/{id}` | ADMIN / own TEAM_MEMBER | Individual report (see Reports below) |
| GET | `/api/analytics/dashboard/export.pdf` \| `.xlsx` | ADMIN | Department report as a downloadable file |
| GET | `/api/analytics/employee/{id}/export.pdf` \| `.xlsx` | ADMIN / own TEAM_MEMBER | Individual report as a downloadable file |
| GET | `/api/analytics/tasks/{taskId}/points/{assignmentId}` | ADMIN / own assignment's TEAM_MEMBER | One assignment's full points ledger for a task (see Points & performance below) |

### Listing tasks

`GET /api/tasks` returns `{ content, page, size, totalElements, totalPages }`. All parameters are optional:

| Param | Meaning |
|---|---|
| `page` (0-based), `size` | Paging. Default 20, capped at 100 |
| `sort` + `direction` | `sort` is one of `updated` (default), `created`, `due`, `priority` (by severity, not alphabetically), `title`, `number`, `progress`; `direction` is `asc` / `desc` (default). Anything else is a 400 |
| `search` | Case-insensitive match on title, task number or description. `%` and `_` are literal |
| `status`, `priority`, `categoryId` | Exact filters |
| `assigneeId` | Tasks where this person is a *current* assignee. Admin only - ignored for employees, who are always limited to their own tasks |
| `archived` | Default `false`. `true` shows the Archive tab: cancelled tasks only, ignoring any `status` filter. `false` (the normal list) never includes a cancelled task, and additionally never includes a `DRAFT` task for a non-Admin caller |

### What an employee receives

Employees get the task plus, for each person on it, only `{ id, name }`, their status and progress. There is no creator, no email / employee code / role, and another person's blocked / on-hold reason is withheld (you only see your own).

### Task lifecycle rules

**Task status is derived, never set by the client.** It is computed from the task's current assignments (first match wins): all COMPLETED → `COMPLETED`; any BLOCKED → `BLOCKED`; any IN_PROGRESS → `IN_PROGRESS`; every unfinished assignment ON_HOLD → `ON_HOLD`; some COMPLETED → `IN_PROGRESS`; otherwise `ASSIGNED` (or `REOPENED` after a reset-reopen, until work resumes). No assignees, OR no due date yet → `DRAFT` - a task is incomplete until both are filled in, and stays invisible to team members the whole time (404 on `GET .../{id}`, absent from `GET /api/tasks`) even if it happens to already have them as a current assignee. `CANCELLED` is set only by `DELETE`. Overall progress is the average of current assignments' progress (100 when completed).

**Assignment update** (employee on their own work, or an Admin adjusting anyone's) body: `{ "status", "progress" (0-100), "reason" }`.
- `status` may only be `IN_PROGRESS`, `ON_HOLD`, `BLOCKED`, `COMPLETED` (else 400).
- `COMPLETED` requires progress 100; progress 100 requires `COMPLETED`.
- `BLOCKED` / `ON_HOLD` require a non-blank `reason` (max 255).
- 409 if the task is cancelled. An employee also gets 409 once their own part is COMPLETED (only an Admin can reopen or adjust it).
- Admin adjustments are recorded as such (`adjustedByAdmin` in the audit log; "Adjusted by Admin" in the status history).

**Admin edit** - people already assigned stay assigned even if deactivated; newly added people must be active team members (400). Removing someone marks their assignment `REMOVED` (or leaves it `COMPLETED` if they had finished). `REASSIGNED` is reserved for the Phase 4 reassignment flow. `dueDate` is normally rejected here (400, "use the dedicated due-date action") if it differs from the task's current one - **except** when the task never had one yet (`null` → any value), which is a plain field edit completing a DRAFT, not a deadline change, so it needs no reason and writes no due-date history. Changing or clearing an *existing* due date still always requires the dedicated endpoint.

**Cancel** - unfinished assignments become `CANCELLED` (progress kept); already-COMPLETED assignments are untouched. A cancelled task leaves the normal `GET /api/tasks` list entirely - it only shows up with `archived=true` (the Archive tab, Admin-only in the UI).

**Reinstate** - only for CANCELLED tasks (else 409). People whose work was cancelled resume where they stopped: `IN_PROGRESS` if they had progress, otherwise `ASSIGNED`. Blocked / on-hold reasons are not restored. The task keeps its number and history.

**Reopen** - only for COMPLETED tasks (else 409).
- `keepProgress: false` (default): everyone restarts at `ASSIGNED` / 0%, start and completion times cleared; task becomes `REOPENED`.
- `keepProgress: true`: everyone resumes `IN_PROGRESS` at the progress they had *just before* marking their part complete (99% if that was never recorded); start times kept, completion times cleared.

**Duplicate guards** - two *open* tasks (any status except CANCELLED / COMPLETED) cannot share a title (case- and space-insensitive) within a category, and a person can be a current assignee of a task only once. Both are enforced by the database and return 409 with a readable message. This also means a cancelled or completed task cannot be reinstated / reopened while a same-title open task exists.

Every change above also writes `task_status_history` and `audit_logs` rows (see `DATABASE.md`).

### Fail

`POST /api/tasks/{id}/assignments/{assignmentId}/fail` body: `{ "reason" }` (required, max 255) - a third, direct way into the terminal `FAILED` points outcome, alongside the due-date-extension 3-strike path and a reassignment with the points-loss checkbox. Only a *current* assignment can be failed (404 otherwise): 409 if the task is `CANCELLED` or `COMPLETED`, or if this assignment is itself already `COMPLETED`. Seals the assignee's cycle at permanent 0 points (counted, same as a 3rd missed deadline - see Points & performance below), then re-derives the task's own status to `FAILED` since the assignment stays current. The assignment's own `status`/`progress` are left untouched - status and scoring are tracked separately by design, same as the strike path. Idempotent: calling it again on an already-failed cycle is a no-op on points but still succeeds.

### Due date

`PUT /api/tasks/{id}/due-date` body: `{ "dueDate": "2026-09-23", "reason": "...", "deductPoints"? }`. **`reason` is required** (non-blank, max 255). 409 if the task is cancelled ("reinstate it first") or completed ("reopen it first"). Setting the same date again, or clearing an existing date, is a 400. The response distinguishes `SET` (no previous date), `EXTENDED` (later) and `BROUGHT_FORWARD` (earlier) - `GET .../due-date-history` returns `{ originalDueDate, currentDueDate, extensionCount, changes[] }` where `extensionCount` only counts `EXTENDED` entries (regardless of `deductPoints`). An employee sees the same history for their own task but `changedBy` is always `null` for them; the Admin sees `{ id, name }`.

`deductPoints` (Phase 9, Admin-only in effect since only Admins can call this endpoint) - null/omitted defaults to `true`. When `true` (the default), this extension is eligible to cost the assignee(s) points same as before. When explicitly `false`, the due date still moves and still counts toward `extensionCount` / the advisory 3-extension cap, but this specific extension is excluded from the points strike calculation entirely - useful when the delay wasn't the assignee's fault. Each `changes[]` entry exposes its own `countsTowardStrikes` so the history shows which extensions were waived.

### Reassignment

`POST /api/tasks/{id}/reassign` body: `{ "fromAssignmentId", "toUserId", "reason", "classification"?, "keepProgress"?, "deductPoints"? }`. `reason` is required (max 255); `classification` is one of `NEUTRAL_ADMINISTRATIVE | PERFORMANCE_RELATED | OPERATIONAL | OTHER`, optional, Admin-only-visible, and never fed into any automatic calculation. The old assignment becomes `REASSIGNED` (distinct from `REMOVED`, which is a plain unassign via task edit); a new assignment row is created for the target - the task keeps its id/number, nothing is deleted. `keepProgress: true` starts the new assignee `IN_PROGRESS` at the old progress (or `ASSIGNED`/0% if that was 0); default starts them at `ASSIGNED`/0%. Only a *current, unfinished* assignment can be the source (404 if not current, 409 if already `COMPLETED`); the target must be an active team member not already on the task. The recipient's own activity just says "Task assigned to you" - never who they replaced or why. `GET .../reassignment-history` (Admin only) returns the full chain in order, including deactivated participants by name.

`deductPoints` (Phase 9) - null/omitted defaults to `false`, the opposite polarity from the due-date-extension checkbox: reassignment is neutral by default (excluded from scoring entirely), and the Admin has to explicitly opt in to penalize. When `true`, the *original* assignee's cycle is sealed as a permanent `FAILED` (0 points, counted in their totals) - same terminal outcome as a 3rd missed deadline - instead of the default neutral close. Either way the new assignee always starts a completely fresh cycle at full points, and the task's own live status is unaffected (only a *current* assignment's failure can flip that, and the original assignment is no longer current by the time this is sealed).

### Activity timeline

`GET /api/tasks/{id}/activity` merges `task_status_history`, `task_due_date_history` and (Admin only) `task_reassignment_history` into one chronological, human-readable list (`{ id, occurredAt, type, message, actorName }`). An employee only sees: task-level events (no actor name), their *own* assignment-level events, and due-date changes - never another person's status/progress events, reassignment details, or any actor name.

## Analytics (Admin dashboard)

`GET /api/analytics/dashboard` - all query params optional: `from`, `to` (ISO dates; default the last 30 days), `employeeId`, `status`, `priority`, `categoryId`. Returns one payload covering every dashboard widget. **Not every widget shares the same window** - each follows the approved mockup's own subtitle; see the Javadoc on `AnalyticsService` for the full per-widget rationale. In short:

- `summary.totalTasks` / `completed` / `reassigned` / `completionRate` - tasks **created** within `[from, to]`.
- `summary.active` / `overdue` / `blocked` / `onHold` - a **live snapshot** (not date-bound), so an old task that's still blocked today always counts regardless of the date filter.
- `statusDistribution`, `categoryDistribution` - tasks created within `[from, to]`.
- `teamWorkload`, `priorityDistribution` - live snapshot of currently-open tasks.
- `employeeCompletion` - per employee, **their own** assignment's `completedAt` within `[from, to]` - not the whole task's, so finishing your part of a still-open shared task counts. (`completionTrend`, below, is the task-level equivalent: organizational throughput rather than individual credit.)
- `employeeAssigned` - per employee, tasks **created** within `[from, to]` where they're (or ever were) an assignee - one count per task, not per assignment row. Pairs with `employeeCompletion` for the "Tasks Assigned vs. Completed, by Person" chart on the Department Report.
- `completionTrend` - completions per ISO week, a fixed last-8-weeks window, independent of the date-range filter.
- `overdueTrend` - the **current** overdue backlog, bucketed by the week its due date fell in (a snapshot of today's backlog by origin week, not a reconstructed historical trend).

Every widget still respects the explicit `employeeId` / `status` / `priority` / `categoryId` filters, if set. Every active team member is listed in `teamWorkload` / `employeeCompletion` / `employeeAssigned` even at zero, so a quiet employee doesn't just vanish from the chart - the Admin (Farrukh) is never in any of these three, only real team members.

The Department Report and each Individual Report both also show a "Tasks Assigned vs. Completed" bar chart (department: one bar-pair per employee, from `employeeAssigned` + `employeeCompletion`; individual: a single bar-pair from that employee's own `summary.assigned` / `summary.completed`) alongside a "Status Breakdown" table (the existing `statusDistribution`, plus a live `summary.overdue` row appended - overdue isn't a `TaskStatus` value, so it's never otherwise part of that list). Both PDF/Excel exports include the same two as an extra sheet/section each.

## Reports (Phase 8)

`GET /api/analytics/employee/{id}` - one person's own objective metrics (spec sections 21 / 38): `from`, `to`, `status`, `priority`, `categoryId` all optional (same defaulting as the dashboard). **Admin**: any employee. **Team member**: only their own id - someone else's report is a `404`, identical to every other visibility rule in this app, never a `403`. Two independent "completed" numbers exist on purpose, mirroring the dashboard's own createdAt-vs-completedAt split:

- `summary.assigned` / `completed` / `cancelled` / `completionRate` - **created**At-bound, so the rate reads "of what was assigned in this window, how much did they finish".
- `summary.active` / `overdue` / `blocked` / `onHold` - a **live** snapshot of their own current assignments (their own status, not the task's derived overall one - correct when a co-assignee's state differs from theirs).
- `summary.reassignedAway` - reassignment events **away from them**, createdAt-bound.
- `summary.avgCompletionDays` - completedAt-bound (unbounded by when the task was created) - "how long have their recent completions taken"; `null` when they haven't completed anything in the window.
- `statusDistribution` / `priorityDistribution` / `categoryDistribution` - their own createdAt-bound tasks.
- `completionTrend` - their own completions per calendar month, a fixed last-6-months window.
- `currentWorkload` - their current open assignments (live, not date-bound), each with their own status/progress.
- `reassignedTasks` - the reassignment events above, resolved to task/recipient names.
- `recentActivity` - their last 10 status-history events across every task, newest first.

Every number above is directly countable - no synthesized score - per the original spec's instruction not to invent one *unless a formal scoring methodology is defined*. Phase 9 defines one (points), documented below; it is deliberately kept separate from these objective counts.

`GET /api/analytics/dashboard/export.pdf` / `.xlsx` (Admin only) and `GET /api/analytics/employee/{id}/export.pdf` / `.xlsx` (same visibility rule as the report itself) return a downloadable file (`Content-Disposition: attachment`), but **the PDF and Excel versions are no longer the same content** - the Admin asked for the PDF specifically to carry only the charts (plus the per-task detail table for the individual report), so the two formats now diverge on purpose:
- **PDF** - charts only, rendered as real images (`ChartImageRenderer`, plain Java2D, no charting library dependency): "Tasks Assigned vs. Completed" (department: by person; individual: "This period"), "Team/Strike"/"Team Efficiency Trend" for the department (bar chart + donut), and for the individual report "Points Retained vs Lost" (diverging bar) plus "Monthly Points Trend" (bar chart) - immediately followed by the full "Individual Tasks" table (see `taskDetails` above). No summary tiles, no distribution tables, no workload/activity lists - nothing else.
- **Excel** - unchanged: every data table the on-screen report shows (summary, distributions, workload, reassignments, activity, points breakdown, etc.) across multiple sheets, plus the same chart PNGs embedded into a dedicated "Charts" sheet and the "Individual tasks" sheet.

This intentionally departs from the original spec's "data-only, no charts" instruction - a later, more specific request from the Admin for a lean, chart-first PDF takes precedence.

## Points & performance scoring (Phase 9)

Every task carries a base point value from its priority: LOW=2, MEDIUM=3, HIGH=5, CRITICAL=8. Per assignee, per scoring "cycle" (started by an internal `ASSIGNED` ledger event on initial assignment, becoming a reassignment's new assignee, or a reopen/reinstate): **points are never deducted just because a task sits overdue.** The only thing that costs points is an Admin explicitly granting a due-date extension with "deduct points" left checked (see `deductPoints` above) - the 1st such extension drops resulting points to 50% of base, the 2nd to 25%, and the 3rd marks the task `FAILED` at 0 points, permanently, for that one assignment, even if the task is later completed by someone else (status and scoring are tracked separately by design). A task can sit arbitrarily overdue, untouched, forever, and still hold full points until an Admin acts. Completing before a 3rd strike locks in whatever the strike level left. Cancelling or being reassigned away before a 3rd strike is neutral and excluded from scoring entirely. **The 3-strike cap is advisory, not enforced** - an Admin can still extend the due date or reassign past it.

**`pointsPossible` is reflected live, the moment a task is assigned - `pointsEarned` and `pointsLost` only once something has actually happened.** Every points total (individual report, department leaderboard, efficiency trend) is now three separate numbers, each with a precise meaning:
- `pointsPossible` - the live ceiling. A still-open, untouched task counts its full base value here the instant it's assigned - this is the one number that shows up immediately, per the Admin's request. A cycle closed out neutrally (`CANCELLED`, or a `REASSIGNED` without the points-loss checkbox) drops out of this entirely the moment it's closed.
- `pointsEarned` - completed work only. An open task - however long it's sat at full points - never contributes here. Only a `COMPLETED` event's `resultingPoints` counts, whatever strike level it locked in at.
- `pointsLost` - what's already been genuinely forfeited, open or resolved. An untouched open task contributes 0 here (nothing lost yet); a strike that's already landed on a still-open task counts immediately (it doesn't wait for the task to finish); a `FAILED` outcome (from a 3rd strike, a penalized reassignment, or the direct Fail action) counts its full base value.

This is deliberately continuous: an open cycle's live `pointsLost` contribution and what it locks in at `COMPLETED` are computed from the exact same numbers, so finishing a task never causes a jump - only a genuine strike, a failure, or the Fail action changes anything. `efficiencyRate` is `pointsEarned ÷ pointsPossible` - of every point ever at stake, including still-open work at its live value, what fraction has actually been banked so far. By the Admin's explicit choice, a pile of ordinary, still-open work **does** lower this until it's actually completed (an earlier version computed it as `earned ÷ (earned + lost)` instead, specifically to avoid that; this was deliberately reverted).

**Points are not split on a shared task.** Each current assignee has their own independent scoring cycle at the task's full base value - a HIGH task shared by two people means each of them is separately eligible for the full 5 points, not 2.5 each. A due-date extension is a task-level event, though, so it's evaluated against every current assignee's cycle at once: extending a shared task's deadline can strike every co-assignee simultaneously, unless waived (see `deductPoints` above) for that specific extension.

**Per-extension points waiver.** Each due-date extension can independently be marked as not counting toward strikes (`deductPoints: false` on the due-date-change request - see "Due date" above). This only affects the points math; the extension still happened and still shows in history and the extension-count/cap.

**Three ways into a permanent FAILED outcome**, all sharing the same terminal effect (0 points, counted): (1) a 3rd strike from due-date extensions, above; (2) a reassignment with `deductPoints: true` (see "Reassignment" below); (3) the Admin directly failing a still-current assignment via `POST .../assignments/{assignmentId}/fail` (see "Fail" above) - the only one of the three that flips the task's own live status to `FAILED` immediately, since the other two involve an assignment that's no longer current by the time it's sealed.

**Visibility: a team member sees their own points, never anyone else's.** `GET /api/analytics/employee/{id}` includes real `pointsSummary` / `pointsBreakdown` / `monthlyPointsTrend` / `pointEvents` for both an Admin viewing any employee AND a team member viewing themselves (same visibility rule as the rest of the report - see above). `GET /api/analytics/tasks/{taskId}/points/{assignmentId}` follows the identical rule at the assignment level: an Admin can view any assignment, a team member only one that's theirs - a co-assignee's points on the very same shared task are invisible to them (404, never 403). There is no department-wide comparison available to a team member; `GET /api/analytics/dashboard` and its exports (leaderboard, strike distribution, at-risk list, and every other employee's points) remain Admin-only outright.

`GET /api/analytics/dashboard` additionally returns, Admin-only:
- `pointsLeaderboard` - every active team member's `pointsEarned` / `pointsPossible` / `pointsLost` / `efficiencyRate` (earned ÷ possible) for `[from, to]`, sorted by efficiency descending. Efficiency, not raw points, is the fair comparison metric - task-priority mix and volume differ per person.
- `teamEfficiencyTrend` - department-average efficiency per month, fixed last-6-months window.
- `strikeDistribution` - every task in `[from, to]` that currently counts (open or resolved - see "live points" above) bucketed by its current outcome level (`0`/`1`/`2` point-deducting deadline extensions, or `FAILED`). Shown on screen as "Points Impact by Deadline Extensions" - the field/level names (`STRIKE_1`, `STRIKE_2`) are unchanged internally.
- `atRiskTasks` - a **live** snapshot of currently-open assignments sitting at 1 or 2 point-deducting deadline extensions right now, not date-bound.
- `employeeDueDateExtensions` - per employee, distinct tasks created in `[from, to]` that have had their due date extended by the Admin at least once, **whether or not** that extension deducted points - broader than `strikeDistribution`, which only counts point-deducting extensions. A task extended more than once still only counts once.
- `employeeReassignedAway` - per employee, how many times a task has been reassigned away from them (the "from" side) in `[from, to]` - the department-wide version of `EmployeeReportResponse.Summary#reassignedAway`.

`GET /api/analytics/employee/{id}` additionally returns (Admin viewing any employee, or the employee viewing themselves):
- `pointsSummary` - `pointsEarned` / `pointsPossible` / `pointsLost` / `efficiencyRate` / `tasksFailed` for `[from, to]` (see "live points" above for exactly what each counts).
- `pointsBreakdown` - every task in `[from, to]` that currently counts (open or resolved) bucketed by its current outcome (`FULL`/`STRIKE_1`/`STRIKE_2`/`FAILED`).
- `monthlyPointsTrend` - their own efficiency per month, fixed last-6-months window.
- `pointEvents` - their most recent point-affecting events (excludes the internal `ASSIGNED` event), newest first, each linking back to its task.
- `taskDetails` - one row per (task, this employee's assignment) they were on in `[from, to]`: `taskNumber`, `title`, `priority`, `assignedAt`, `status` (the task's own derived status), `dueDates` (the original due date plus every later change, each flagged `countedTowardStrikes`), `possiblePoints` (the task's base value), `deductedPoints` (what this employee has actually forfeited on it so far - 0 for an untouched open task). Backs the "Individual Tasks" table in both the on-screen report and its PDF/Excel exports.

`GET /api/analytics/tasks/{taskId}/points/{assignmentId}` returns one assignment's full chronological ledger for a task - `basePoints`, `resultingPoints`, `outcome` (`OPEN`/`COMPLETED`/`FAILED`/`CANCELLED`/`REASSIGNED`), and every `events[]` entry (`occurredAt`, `eventType`, `pointsDelta`, `resultingPoints`, `reason`). Backs the "Points — This Task" panel in the task detail view.

## Real-time (WebSocket)

STOMP over `/ws`. The endpoint itself is permitted anonymously (a browser's native WebSocket constructor can't set an `Authorization` header on the handshake); real authentication happens on the STOMP `CONNECT` frame instead, which carries `Authorization: Bearer <token>` as a native STOMP header. No valid `CONNECT` means the connection is closed before it can subscribe to anything.

| Destination | Who | When |
|---|---|---|
| `/user/queue/notifications` | the user themself | Any time a notification is created for them (assigned/reassigned/due-date changed/reopened/cancelled/completed/blocked/on-hold/commented - the same trigger points as `GET /api/notifications`) |
| `/topic/admin-dashboard` | any authenticated user | An assignment reaches `COMPLETED` or `BLOCKED` (scoped narrowly - not every edit) |
| `/topic/tasks/{taskId}` | any authenticated user | That task is mutated: assignment update, due-date change, reassignment, comment, cancel, reopen, reinstate |

Every payload is `{ "type": "..." }` (plus `taskId` on the task topic) - a lightweight "something changed" signal, never a state sync. The frontend reacts by invalidating the relevant TanStack Query cache and refetching over the normal REST endpoints above; if the socket is ever disconnected, the existing 60s notification poll and on-focus refetches keep working as the fallback.

## Planned (not yet built)

```
POST   /api/tasks/{id}/attachments
```

## Authentication

`Authorization: Bearer <token>` on every request except `/api/auth/login`, `/api/health`, and the `/ws` handshake itself (see Real-time above - the WebSocket connection is authenticated per-connection on the STOMP CONNECT frame instead). Tokens are HS384 JWTs (`app.jwt.secret` / `app.jwt.expiration-minutes` in `application.yml`), carrying the user's id, email and role. A request with no/invalid token gets a `401` in the standard error shape; an authenticated request lacking the required role (`@PreAuthorize`) gets a `403`.

## Error shape

```json
{
  "timestamp": "2026-09-18T10:00:00Z",
  "status": 404,
  "error": "Not Found",
  "message": "Task not found.",
  "path": "/api/tasks/9999",
  "fieldErrors": null
}
```

`fieldErrors` is populated only for validation failures (400), as a list of `"field: message"` strings.
