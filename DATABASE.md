# Database

PostgreSQL, schema-managed by Flyway (`backend/src/main/resources/db/migration`). `spring.jpa.hibernate.ddl-auto` is `validate` — Hibernate never generates schema; every change is a new versioned migration.

## Entity-relationship overview

```
users (1) ──< task_assignments >── (1) tasks
users (1) ──< tasks.created_by
users (1) ──< task_comments, task_attachments, notifications, audit_logs
users.manager_id ──> users.id            (self-reference, nullable — future org hierarchy)

task_categories (1) ──< tasks

tasks (1) ──< task_assignments
tasks (1) ──< task_due_date_history
tasks (1) ──< task_reassignment_history
tasks (1) ──< task_status_history
tasks (1) ──< task_comments
tasks (1) ──< task_attachments

task_assignments (1) ──< task_status_history      (nullable link — null = task-level event)
task_assignments (1) ──< task_comments             (nullable link)
task_assignments (1) ──< task_reassignment_history.from_assignment_id
task_assignments (1) ──< task_reassignment_history.to_assignment_id
```

## Why `task_assignments` is a separate table

A task is never `task -> one employee`. It is `task -> task_assignment(s) -> employee`, so:
- one task can have multiple current assignees, each with independent `status` and `progress`
- reassigning an employee off a task never deletes data — the old `task_assignments` row is marked `is_current = false`, `status = 'REASSIGNED'`, and a new row is inserted for the new assignee
- `tasks.overall_progress` is the average of `progress` across `task_assignments` where `is_current = true` (documented default — see Phase 0 discussion; revisit if weighted progress is ever needed)

## Task status vs. assignment status

- **`tasks.status`**: the whole-task lifecycle (`DRAFT, ASSIGNED, IN_PROGRESS, ON_HOLD, BLOCKED, COMPLETED, CANCELLED, REOPENED`). Derived from current assignment statuses in most cases; explicitly set by the Admin for `CANCELLED`/`REOPENED`.
- **`task_assignments.status`**: per-employee (`ASSIGNED, IN_PROGRESS, ON_HOLD, BLOCKED, COMPLETED, REASSIGNED, CANCELLED`). `REASSIGNED` is a neutral, non-punitive terminal state — never conflated with "failed" in any report.

## Constraints & invariants

- `overall_progress` and `task_assignments.progress` are `CHECK`ed to `0..100`.
- `task_assignments`: `CHECK (status <> 'COMPLETED' OR progress = 100)` — completed always means 100%.
- `tasks.version` / `task_assignments.version`: optimistic locking (JPA `@Version`) so a concurrent Admin edit and employee update don't silently overwrite each other.
- History tables (`task_due_date_history`, `task_reassignment_history`, `task_status_history`) are append-only by convention: the service layer never exposes update/delete operations on them. (Not enforced via a separate DB role in this deployment — a single small internal app doesn't warrant a second DB principal; the constraint lives in the API surface.)

## Enum strategy

Enums are `VARCHAR` + a DB `CHECK` constraint, not native Postgres `ENUM` types — adding a new value is a plain migration (`ALTER TABLE ... DROP CONSTRAINT ... ADD CONSTRAINT ...`) instead of the more invasive `ALTER TYPE`. `task_categories` is a real lookup table (not an enum at all) specifically so the Admin's category list can grow without a migration.

## Indexing

| Index | Supports |
|---|---|
| `task_assignments(task_id, is_current)` | "current assignees of this task" |
| `task_assignments(user_id, status)` | employee dashboards, workload counts |
| `tasks(status)`, `tasks(due_date)`, `tasks(category_id)` | dashboard filters, overdue queries |
| `notifications(user_id, is_read)` | notification bell / unread count |
| `audit_logs(entity_type, entity_id)` | audit log lookups per record |

## Task lifecycle data (Phase 3 fixes, migration `V4`)

- **Assignment status `REMOVED`** — an employee taken off a task by an Admin edit. Kept distinct from `REASSIGNED`, which only the Phase 4 reassignment flow (reason + history) will produce, so "reassigned" in reports never includes plain removals.
- **Task numbers** come from the `task_number_seq` sequence (`TASK-000123`), not `count()+1`, so concurrent creates can never collide.
- **Lifecycle timestamps** are populated: `tasks.completed_at` / `cancelled_at`, `task_assignments.started_at` / `completed_at`. `completed_at` is cleared if the task leaves COMPLETED (reopen, or a new assignee added); the original moment stays in `task_status_history`. `started_at` is set the first time an employee moves off ASSIGNED.
- **`task_status_history`** — one row per assignment or task status change (`assignment_id` NULL = task-level), with the acting user and a comment (e.g. the blocked/on-hold reason).
- **`audit_logs`** — `CREATE_TASK`, `UPDATE_TASK`, `CHANGE_DUE_DATE`, `CHANGE_PRIORITY`, `ASSIGN_TASK`, `UNASSIGN_TASK`, `CHANGE_STATUS`, `CHANGE_PROGRESS`, `COMPLETE_TASK`, `REOPEN_TASK`, `CANCEL_TASK`, with old/new values as JSONB. Field edits log only the fields that changed.
- Both tables are written inside the same transaction as the change, so a rolled-back change leaves no trail. Not yet written: `task_due_date_history` and `task_reassignment_history` (Phase 4 builds those flows).

### Guards and progress memory (migration `V5`)

- **`uq_task_assignments_one_current`** - unique `(task_id, user_id) WHERE is_current`: a person can be a current assignee of a task only once. Earlier (removed / reassigned) rows for the same person are unaffected.
- **`uq_tasks_open_title_category`** - unique `(lower(btrim(title)), category_id) WHERE status NOT IN ('CANCELLED','COMPLETED')`: no two open tasks with the same title in a category. Excluding finished and cancelled tasks lets a title be reused (e.g. a recurring task) and lets a cancelled task be reinstated rather than re-created. Because it depends on status, it also blocks reopening / reinstating a task while a same-title open task exists. The API maps both violations to a readable 409.
- **`task_assignments.progress_before_completion`** (0-99, nullable) - progress just before someone marked their part COMPLETED (completing forces 100%). Used by "reopen, keep progress"; when unknown (work completed before this existed) the reopen falls back to 99%.
- `idx_tasks_updated_at` supports the default "recently updated" list order. The list also sorts by priority via a read-only formula (`LOW=1 ... CRITICAL=4`) rather than a stored column.

## History & management controls (migration `V6`)

- **`task_reassignment_history`** gained `from_status`, `from_progress`, `to_status`, `to_progress` (all `NOT NULL` - the table was empty in every environment when this landed, so backfilling wasn't needed). Together with the existing columns this gives the full "previous assignment status / new assignment status" record the spec asks for.
- **Append-only enforcement moved into the database.** A `BEFORE UPDATE OR DELETE` trigger (`forbid_history_mutation()`) on `audit_logs`, `task_status_history`, `task_due_date_history` and `task_reassignment_history` raises an exception on any attempt to modify a row - this holds even against a direct `psql` session, not just through the API. Not applied to `tasks`/`task_assignments`/`users`, which are mutable by design.
- New indexes: `audit_logs(created_at DESC)`, `audit_logs(user_id)`, `audit_logs(action)` for the Audit Logs page; `task_due_date_history(task_id, changed_at)`, `task_reassignment_history(task_id, created_at)` for per-task history/timeline queries.

### A JPA pitfall worth documenting: `save()` on an already-loaded parent silently drops new children's ids

`Task.assignments` cascades `ALL`. Spring Data's `save()` calls `EntityManager.persist()` only for a *brand-new* (never-persisted) entity; for an entity that already has an id - like a `Task` you just loaded via `findById()` in the same transaction - `save()` calls `EntityManager.merge()` instead. `merge()` on a parent cascades a **merge**, not a **persist**, to any newly-added child entities: it creates a separate, correctly-persisted copy internally, but the original Java object you're still holding a reference to (e.g. a freshly-`new`'d `TaskAssignment` added to `task.getAssignments()`) never has its generated id set. Reading `newAssignment.getId()` right after `taskRepository.saveAndFlush(task)` returns `null`.

This broke reassignment outright (`task_reassignment_history.to_assignment_id` got a `NOT NULL` violation on every single call) and silently corrupted `task_status_history.assignment_id` for the "add an assignee via task edit" event ever since Phase 3 (nullable column, so it never threw - the event just quietly looked like a task-level one instead of an assignment-level one).

**The fix, applied everywhere a task is already managed when a new child might be added:** call `taskRepository.flush()` instead of `.save()`/`.saveAndFlush()`, and keep using the original `task` reference as the "saved" result. `flush()` just asks Hibernate to write out the existing persistence context - no merge, no copying, so cascade-`PERSIST` runs normally and generated ids populate on the objects you already hold. (`TaskService.createTask` is the one exception that correctly still uses `save()`: there, `task` is genuinely brand new, so `save()` calls `persist()`, which is safe.)

## Points & performance scoring (migration `V7`)

- **`tasks.status`** gained a `FAILED` value in its `CHECK` constraint - a real, filterable status like any other, set when a current assignment's scoring cycle seals a 3rd-strike failure (see `PointsService`). Decoupled from completion by design: a task can be `COMPLETED` while an earlier assignee's ledger permanently recorded `FAILED`.
- **`task_points_events`** - one append-only row per scoring event on one assignment (`task_id`, `assignment_id`, `user_id`, `event_type` - `ASSIGNED`/`STRIKE_1`/`STRIKE_2`/`FAILED`/`COMPLETED`/`CANCELLED`/`REASSIGNED`, `base_points`/`points_delta`/`resulting_points` as `NUMERIC(5,2)`, `reason`, `occurred_at` - the real moment the event happened, which may predate `created_at` when sealed retroactively). Guarded by the same `forbid_history_mutation()` trigger as the other history tables (reused, not redefined). Indexed on `(assignment_id, occurred_at)`, `(user_id, occurred_at)`, and `(task_id)`.
- Only `COMPLETED` and `FAILED` events count toward an employee's "possible"/"earned" scoring totals - `CANCELLED` and `REASSIGNED` are neutral and fully excluded from every aggregate.
- No `points` column was added to `tasks` or `task_assignments` - the ledger is the only source of truth, aggregated on read (`AnalyticsService`), consistent with every other history table in this schema never duplicating state onto the row it describes.
- **Backfill for tasks/assignments created before this migration existed** (`PointsService#backfillMissingBaselines`, `POST /api/analytics/points/backfill`, Admin-only): seals a fresh `ASSIGNED` baseline dated *now* (never backdated to the real `assignedAt`) for any current assignment with no points history yet, then evaluates it immediately. Dating it "now" rather than historically is deliberate - it stops old due-date-history rows that predate the points system from being misread as strikes against someone. Idempotent: an assignment that already has history is skipped entirely, so it's safe to run more than once.

## Due-date extension points waiver (migration `V8`)

- **`task_due_date_history.counts_toward_strikes`** (`boolean not null default true`) - lets the Admin decide, per extension, whether it's eligible to cost the assignee(s) points (`PointsService`'s strike-counting query filters on it). Default `true` preserves every existing row's actual historical effect unchanged. A waived extension (`false`) still moves the due date and still counts toward `extensionCount` / the advisory 3-extension cap - only the points math ignores it.
- Points are never split across co-assignees: on a shared task, each current assignee has their own full-base-value scoring cycle, and a due-date extension (waived or not) is evaluated against every one of those cycles at once, since the deadline itself is a task-level fact.
- **Follow-up policy fix (no schema change): the automatic "currently overdue with no extension" strike was removed entirely from `PointsService`.** It originally contributed one strike level purely from `task.getDueDate().isBefore(today)`, independent of any admin action - inconsistent with "no points are deducted until an Admin explicitly does that." Now the only thing that ever costs points is an extension with `countsTowardStrikes = true`. A production consequence: an earlier real-data backfill (`POST /analytics/points/backfill`) had already sealed 4 `STRIKE_1` events under the old automatic rule before this fix landed. Those events were left in place (append-only, never deleted) and corrected by sealing a fresh `ASSIGNED` baseline for each affected assignment - the same "start a clean cycle" mechanism already used for reassignment/reopen/reinstate, applied here via direct, audited `INSERT`s rather than new application code, since it was a one-time data correction, not a new capability.

## Self-service password change and category management (no schema change)

- **Password change** (`PUT /api/auth/password`) reuses the existing `users.password_hash` column and `PasswordEncoder` bean - no new column, no new table. Requires the caller's current password to match before accepting a new one.
- **Category management** reuses `task_categories.is_active`, which already existed in the schema (seeded `true` for every starter category in `V2`) but had no endpoint to ever flip it before now - `GET /api/task-categories` already filtered to `active = true`, it just had nothing that could ever make one `false`. No migration needed; the capability was always latent in the schema.
- **Related fix**: `TaskService#applyFields` was validating a task's category as "must be active" unconditionally, including when a task is edited without changing its category at all. Now it only enforces that when the category is genuinely changing (or being set for the first time) - matching the existing precedent for assignees, who stay assigned even after being deactivated.

## DRAFT completeness, reassignment forfeiture, and the Archive view (no schema change)

- **`TaskStatusRules.derive` now also forces `DRAFT` when a task has no due date yet**, not just when it has no assignees - either one missing means the task isn't real work yet. A DRAFT task (whichever way it got there) is invisible to team members even if they're technically a current assignee (`TaskLifecycleSupport#requireVisible`, `TaskService#listTasks`) - Admin-only until both are filled in.
- **`TaskService#updateTask`'s due-date guard** ("use the dedicated due-date action") now only fires when an *existing* due date would change. Filling in a `null` due date through the normal edit form is a plain field edit completing a DRAFT - it writes no `task_due_date_history` row and needs no reason, unlike a real extension.
- **Reassignment can now optionally fail the original assignee** (`ReassignTaskRequest.deductPoints`, default `false`): `PointsService#sealFailedByAdmin` seals their cycle as a permanent `FAILED` (0 points, counted in totals) instead of the default neutral `REASSIGNED` close. Reuses the existing `FAILED` event type and `TERMINAL` no-op guard - no new points event type needed.
- **Incidental fix while building the above:** `PointsService#sealNeutralClose` (cancel / plain reassign) was hardcoding `resultingPoints` to `0` for every neutral close, even though "neutral" means excluded from scoring entirely, not "scored at zero". It now carries forward whatever the assignee actually stood at (full base, or less if a strike had already landed) so the per-task points panel reads correctly. Never affected any aggregate - `AnalyticsService` already excluded `CANCELLED`/`REASSIGNED` events from every sum regardless of this field's value - purely a display fix.
- **Archive**: `GET /api/tasks?archived=true` shows cancelled tasks only (ignoring any `status` filter); the default (`false`) never includes a cancelled task. Implemented as an extra `Specification` predicate (`TaskSpecifications#excludingStatuses`), not a new table or column - a cancelled task was always just a task with `status = 'CANCELLED'`.

## Direct Fail action (no schema change)

- **`POST /api/tasks/{id}/assignments/{assignmentId}/fail`** (`TaskService#failAssignment`) is a third way into the same permanent `FAILED` points outcome as the 3-strike due-date path and the reassignment points-loss checkbox - it renames-by-reuse the sealing method the reassignment path already used (`PointsService#sealFailedByAdmin`, generalized from `sealFailedOnReassignment`). Unlike reassignment, the failed assignment stays *current*, so `TaskLifecycleSupport#recalculate` immediately flips `tasks.status` to `FAILED` the same way a 3rd strike does - no new column, event type, or table. The assignment's own `status`/`progress` are left untouched, matching the strike path's decoupling of status from scoring.
- **Individual report "Reassigned Away"** (`EmployeeReportResponse.reassignedTasks`, already shipped with `toUserName`) was verified against this change rather than modified - it already resolves and shows which task was reassigned and to whom, with no gap found.

## Live points (no schema change)

- Points used to only count once a cycle reached a terminal `COMPLETED`/`FAILED` event - an assigned-but-untouched task contributed nothing to anyone's `pointsPossible`/`pointsEarned` until it resolved. Per the Admin's request, `pointsPossible` now counts from the moment the task is assigned. `AnalyticsService#countableEvents` replaces the old `terminalEventsInRange` filter: it still includes every historical `COMPLETED`/`FAILED` event exactly as before, and additionally includes - for each assignment whose *current* cycle hasn't reached one of those yet - that cycle's single latest event (`ASSIGNED`, `STRIKE_1`, or `STRIKE_2`), valued at its own `basePoints`/`resultingPoints`. No new column, table, or points event type - this is purely a read-side aggregation change over the existing `task_points_events` ledger.
- **Why no double-counting or historical drift**: a cycle can only be "open" if its latest event is non-terminal, and every path that stops an assignment being current (remove-via-edit, reassignment, cancel, fail) already seals a terminal event first (`sealNeutralClose`/`sealFailedByAdmin`) - so a closed cycle is never mistaken for an open one, and an open cycle's contribution disappears the instant it's actually closed. A resolved cycle's terminal event is untouched by this change, so nothing about historical totals shifts.
- **Correction after the above shipped**: an open task's *full, untouched* value was briefly also counted as `pointsEarned` (not just `pointsPossible`), which the Admin flagged as wrong - "earned" should mean actually-completed work, nothing else. Fixed: `pointsEarned` now sums `resultingPoints` from `COMPLETED` events only (`AnalyticsService#buildPointsSummary`/`buildPointsLeaderboard`/`buildMonthlyRate`). A new `pointsLost` field (`EmployeeReportResponse.PointsSummary`, `DashboardAnalyticsResponse.PointsLeaderboardEntry`) sums `basePoints - resultingPoints` across every countable event, open or resolved, so a strike already landed on a still-open task is reflected immediately without waiting for completion. `efficiencyRate` moved from `earned ÷ possible` to `earned ÷ (earned + lost)` (`AnalyticsService#efficiencyRate`) so a pile of ordinary, still-open work no longer drags it down.
- **Why no jump on completion**: an open cycle's live `pointsLost` contribution (from its latest `ASSIGNED`/`STRIKE_1`/`STRIKE_2` event) and its eventual `COMPLETED` value are sealed from the identical strike math in `PointsService#evaluateAssignment` - completing a task locks in the same numbers it was already showing while open, never a surprise change.

## Report exports: charts and per-task detail (no schema change)

- **Real rendered charts** in both PDF and Excel exports, replacing the original "data-only" design: `ChartImageRenderer` (new class) draws the same shapes/colors as the on-screen SVG charts using plain Java2D, producing PNG bytes. `ReportExportService` embeds them inline via OpenPDF's `Image` for PDF, and into a new "Charts" sheet per workbook via POI's `Drawing`/`ClientAnchor` for Excel. One rendering call backs both formats.
- **`EmployeeReportResponse.taskDetails`** (new field, `AnalyticsService#buildTaskDetails`) - one row per (task, this employee's assignment) in the report window: priority, assigned date (`TaskAssignment.assignedAt`), the task's derived status, its full due-date history (`buildDueDateEntries`, reusing `TaskDueDateHistoryRepository` - the original due date flagged `first`, every later change flagged for whether it counted toward a strike), the task's base points, and how much this employee has actually had deducted on it specifically (computed the same way as the department-wide `pointsLost`, scoped to one assignment). Backs the new "Individual Tasks" table on-screen and in both exports.
- **Correction after the above shipped**: the Admin asked for the PDF specifically to carry *only* the charts (plus the Individual Tasks table) - every other data table (summary, distributions, workload, reassignments, activity, points summary/breakdown as plain numbers) was removed from `departmentPdf`/`employeePdf` only; `departmentExcel`/`employeeExcel` are untouched and still carry everything. The now-dead `kvTable` PDF helper was deleted along with its last two call sites, per the project's no-dead-code convention.

## Concurrency

`TaskLifecycleSupport.findTaskForWrite(id)` loads the task and calls `EntityManager.lock(task, LockModeType.OPTIMISTIC_FORCE_INCREMENT)`, forcing a version bump on `tasks.version` at commit regardless of whether any of the task's own columns actually changed. `TaskDueDateService` and `TaskReassignmentService` use this before mutating a task, so two concurrent actions on the same task - even two reassignments of two different people, which wouldn't otherwise touch a shared column - can never both succeed; the loser gets a 409. (Note: Spring Data's `@Lock` annotation only takes effect on a repository query method; it is silently a no-op if placed on a plain `@Component`/`@Service` method, which is why this goes through `EntityManager` directly.)

## Seed data

`V2__seed_task_categories.sql` inserts the 11 starter categories as real reference data (ships in every environment). Demo/dev-only sample tasks (20-30 tasks spanning every state, added in Phase 3) will live in a separate, explicitly-dev-only location — never mixed into the production migration path.
