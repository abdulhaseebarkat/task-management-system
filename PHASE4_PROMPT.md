# PHASE 4 — HISTORY & MANAGEMENT CONTROLS

You are working on the **SLM Tires IT Task Management System**, an internal app for one IT department (1 Admin + 5 team members, designed to grow). Phases 0-3 are complete, verified and running. Implement **Phase 4 only**, following everything below. Read the whole document before writing code.

---

## 0. Ground rules (non-negotiable)

1. **Phase 4 only.** Do not build anything belonging to another phase (see section 3, "Out of scope"). If you see something outside Phase 4 that looks wrong, note it in your final report; do not fix it.
2. **Never edit an applied Flyway migration** (`V1`-`V5`). All schema changes go in a new `V6__*.sql`. Editing an applied migration breaks Flyway's checksum and stops the app from starting.
3. **Do not commit, push, or create branches.** Leave everything uncommitted.
4. **History is never destroyed.** No hard deletes, no overwrites of a past value without a history row, no update/delete endpoints on any history/audit table.
5. **The backend enforces every permission.** The frontend hiding a button is not security. Every new endpoint is role-checked (`@PreAuthorize`) and, where the caller is an employee, ownership-checked in the service.
6. **Employees must never receive data they are not entitled to.** Enforce this in the *response types* (separate employee DTOs), not with `if` statements sprinkled through mappers. See section 6.6.
7. **Never run destructive tests against the dev database.** It holds the user's real data. Verification uses a throwaway stack (section 9.3).
8. **Everything must build and pass before you say a step is done:** backend compiles, unit tests pass, frontend `npm run build` has no TypeScript errors, `npm run lint` has no *new* warnings, Flyway migrates an existing database cleanly.
9. **Code style:** match the existing code. Records for DTOs, Lombok on entities, constructor injection (`@RequiredArgsConstructor`), no business logic in controllers, entities never returned from the API, friendly (non-technical) error messages, comments only where the *why* is non-obvious, no emojis, no speculative abstractions, no new libraries unless unavoidable (justify any).
10. **Where the spec is ambiguous, follow section 4 ("Decisions already made")**. Do not stop to ask unless something is genuinely blocking; list every assumption you made in the final report.

---

## 1. Environment and stack

- Windows 11, Git Bash / PowerShell. **No local JDK or Maven**: backend builds and tests run in Docker. Node 24 + npm are installed locally. Docker + Compose are available.
- **Backend**: Java 21, Spring Boot **4.1.1**, Spring Data JPA (Hibernate 7.4), Spring Security 7 (stateless JWT), Flyway, PostgreSQL 16, Lombok. Package `com.slmtires.itms`: `config, controller, dto, entity, exception, repository, security, service`.
  - **Jackson 3**: the JSON engine is `tools.jackson.*` (e.g. `tools.jackson.databind.ObjectMapper`), NOT `com.fasterxml.jackson.databind`. Do not add the old artifact.
  - Spring Boot 4 starter names differ from Boot 3 (e.g. `spring-boot-starter-webmvc`). Do not change the pom's starters.
  - `audit_logs` has JSONB columns. Hibernate JSON mapping under Jackson 3 was deliberately avoided: `TaskHistoryService` writes them with `JdbcTemplate` and `CAST(? AS jsonb)`. **Read them the same way** (JdbcTemplate + row mapper, parse with the injected `tools.jackson` `ObjectMapper`). Do not map JSONB columns on JPA entities.
- **Frontend**: React 19, TypeScript (strict, `noUnusedLocals`), Vite 8, Tailwind CSS v4 (CSS-variable tokens in `src/index.css`, e.g. `bg-primary`, `text-muted-foreground`, `border-border`), React Router, TanStack Query v5, axios. No shadcn components are installed yet; use plain Tailwind like the existing components. Path alias `@/` -> `src/`.
- Dev stack (Docker Compose, project root): Postgres `localhost:5433`, backend `localhost:8081` (`/api`), frontend `localhost:5174`. Another unrelated project uses 5432/8080/5173 - do not use those ports.

---

## 2. What exists after Phase 3 (do not break it)

### 2.1 Roles and access
`ADMIN` (was "Boss") and `TEAM_MEMBER`. JWT auth. Admin nav includes Dashboard, Tasks, Team, Reports, Notifications, **Audit Logs** (`/admin/audit-logs`, currently a dead link), Settings.

### 2.2 Database (Flyway V1-V5 applied)
Tables already exist in `V1` and are **currently empty / unused by code**: `task_due_date_history`, `task_reassignment_history`. `task_status_history` and `audit_logs` are written today.

- `task_due_date_history(id, task_id, previous_due_date DATE null, new_due_date DATE not null, changed_by, reason VARCHAR(255) null, changed_at TIMESTAMPTZ default now())`, index on `task_id`.
- `task_reassignment_history(id, task_id, from_assignment_id, from_user_id, to_assignment_id, to_user_id, reassigned_by, reason VARCHAR(255) NOT NULL, classification VARCHAR(30) null CHECK IN ('NEUTRAL_ADMINISTRATIVE','PERFORMANCE_RELATED','OPERATIONAL','OTHER'), created_at)`, index on `task_id`.
- `task_status_history(id, task_id, assignment_id null, old_status, new_status, changed_by, comment, created_at)` - assignment-level and task-level (`assignment_id` NULL) status changes.
- `audit_logs(id, user_id null, entity_type, entity_id, action VARCHAR(50), old_value JSONB, new_value JSONB, metadata JSONB, created_at)`, index `(entity_type, entity_id)`. All task events use `entity_type = 'TASK'`, `entity_id = task id`.
- `task_assignments` has `is_current`, `status`, `progress`, `blocked_reason`, `on_hold_reason`, `started_at`, `completed_at`, `progress_before_completion`, `version`. Assignment status includes both **`REASSIGNED` (reserved for Phase 4 - nothing produces it yet)** and `REMOVED` (an Admin took someone off via the edit form).
- `tasks` has `due_date`, `status`, `overall_progress`, `completed_at`, `cancelled_at`, `version` (optimistic lock).
- Guards (V5): unique current assignee per task (`uq_task_assignments_one_current`), unique open task title per category (`uq_tasks_open_title_category`), task numbers from `task_number_seq`.

### 2.3 Backend behaviour
- **Task status is derived**, never set by a client: computed from the *current* assignments (`TaskStatusRules.derive`, `TaskService.recalculate`). Overall progress = average of current assignments' progress. `CANCELLED` is terminal until **reinstated**. Existing actions: create, edit, employee update, **admin adjust** (`PUT /tasks/{id}/assignments/{assignmentId}`), cancel, **reopen** (keep/reset progress), **reinstate**.
- `TaskResponse` has two views: `forAdmin(task)` and `forEmployee(task, viewerId)`. Employees get `{id, name}` per person (no email/code/role/creator) and other people's blocked/on-hold reasons are hidden.
- **A task an employee is not currently on returns 404** ("Task not found."), identical to a task that does not exist. Keep this rule everywhere.
- `TaskHistoryService.recordStatus(...)` and `.audit(actorId, action, taskId, oldMap, newMap)` write the trail **inside the caller's transaction**.
- Audit actions written today: `CREATE_TASK, UPDATE_TASK, CHANGE_DUE_DATE, CHANGE_PRIORITY, ASSIGN_TASK, UNASSIGN_TASK, CHANGE_STATUS, CHANGE_PROGRESS, COMPLETE_TASK, REOPEN_TASK, CANCEL_TASK, REINSTATE_TASK`. Payload shapes: `ASSIGN_TASK new={userId,user}`; `UNASSIGN_TASK old={userId,user,status}`; assignment-level `CHANGE_STATUS old={scope:"assignment",userId,status} new={scope,userId,status,reason,adjustedByAdmin}`; task-level `CHANGE_STATUS`/`COMPLETE_TASK old={status} new={status}`; `CHANGE_PROGRESS old={userId,progress} new={userId,progress,adjustedByAdmin}`; `REOPEN_TASK new={status,progress:"KEPT"|"RESET"}`; field edits log only changed keys (`dueDate`, `priority`, `title/description/category`); `CREATE_TASK new` = snapshot.
- **Today, `PUT /api/tasks/{id}` can silently change `dueDate`** (it only writes an audit row, no `task_due_date_history`). Phase 4 must close this (see 6.2).
- `GlobalExceptionHandler` maps to a uniform `ErrorResponse` (400 validation/bad body/type mismatch, 401, 403, 404, 405, 409 conflicts incl. optimistic-lock and the two unique-index violations). `PagedResponse<T>` is the paged envelope. Use `BadRequestException` (400), `ConflictException` (409), `ResourceNotFoundException` (404).
- Tests: `TaskStatusRulesTest` (pure JUnit, no DB). `ItmsApplicationTests` needs a live DB, so run targeted tests with `-Dtest=...`.

### 2.4 Frontend
`components/tasks/`: `TaskDetail` (detail panel), `AdminActions` (Edit/Reopen/Reinstate/Cancel with **inline** confirmation panels), `AssignmentForm` (used by employees and by the Admin "Adjust" panel), `TaskTable`, `TaskFilters`, `TaskForm`, `taskDisplay.ts`. `pages/TasksPage.tsx` orchestrates (server-side paging, filters, separate detail query keyed `["tasks","detail",id]`; all task queries live under the `["tasks", ...]` key so one `invalidateQueries({queryKey:["tasks"]})` refreshes everything). `lib/apiError.ts#apiErrorMessage` surfaces backend messages. New Admin actions use **inline panels, not `window.confirm`** (a native dialog also freezes browser test tooling); the pre-existing "Cancel task" `confirm` is out of scope.

---

## 3. Phase 4 scope

**Build:** reassignment, due-date change/extension, due-date history, reassignment history, status history surfaced to users, the per-task activity timeline, and the audit-log trail + Audit Logs page. **Preserve all historical data** and **test repeated reassignment and repeated due-date changes.**

**Out of scope (do NOT build or touch):** notifications and bell (Phase 5), task comments / progress-update messages / attachments (Phase 5), employee dashboard and stats (Phase 5), Admin dashboard, charts, KPIs, performance metrics and "overdue" analytics (Phase 6), real-time/WebSockets (Phase 7), reports and PDF/Excel export (Phase 8), broad QA/hardening and password-change (Phase 9), user-management changes. Do not compute or store an "overdue" flag, an employee score, or any "failed" classification. Design so Phase 5 can later call one method per action to create notifications, but implement none.

---

## 4. Decisions already made

Implement exactly these. (Items marked **[confirm]** are judgement calls the owner may change - implement them as written and list them in your report.)

**Due dates**
- D1. Admin only can change a due date. The reason is **optional** (max 255).
- D2. Every change of an existing task's due date writes one `task_due_date_history` row (previous, new, who, when, reason) and one `CHANGE_DUE_DATE` audit row. Never overwrite silently, whatever entry point is used.
- D3. Kinds of change: `SET` (previous was null), `EXTENDED` (new later than previous), `BROUGHT_FORWARD` (new earlier). Same date -> 400 "That is already the due date."
- D4. "Extended N times" counts only `EXTENDED` changes. Original due date = `previous_due_date` of the earliest history row (or its `new_due_date` if that was null); with no history rows it is the current due date.
- D5. Once set, a due date cannot be cleared (400). A due date in the past is allowed (Admin authority) - the UI just warns.
- D6. **[confirm]** Due date cannot be changed on a `CANCELLED` task (409, "reinstate it first") or a `COMPLETED` task (409, "reopen it first"), so past performance facts are not rewritten.
- D7. The general edit (`PUT /api/tasks/{id}`) must route any due-date difference through the same due-date logic (so it is always recorded, with no reason), and the edit form shows the due date read-only for existing tasks with a pointer to "Change due date".

**Reassignment**
- D8. A reassignment keeps the **same task** (same id/number). The old assignment becomes `is_current=false`, status `REASSIGNED` (never "failed"); a **new** assignment row is created for the new person. Nothing is deleted.
- D9. Reason is **required** (non-blank, max 255), entered as free text (the UI offers presets: Employee unavailable, Employee unable to complete, Workload issue, Skill/resource issue, Priority changed, Management decision, Operational requirement, Other). Classification is **optional** (`NEUTRAL_ADMINISTRATIVE`, `PERFORMANCE_RELATED`, `OPERATIONAL`, `OTHER`), stored, shown only to the Admin, and **never used in any automatic calculation or label**.
- D10. **[confirm]** Optional `keepProgress` (default false), mirroring reopen: false -> the new person starts `ASSIGNED` at 0%; true -> starts `IN_PROGRESS` at the old person's progress (`ASSIGNED` if that was 0). Blocked/on-hold reasons are never carried over. The old row keeps its own progress untouched.
- D11. **[confirm]** Only unfinished assignments can be reassigned: a `COMPLETED` assignment -> 409 ("Completed work can't be reassigned. Reopen the task first."). Cancelled or completed tasks -> 409.
- D12. The new person must be an active `TEAM_MEMBER` and not already a *current* assignee (400/409 with a clear message). Reassigning **away from a deactivated employee is allowed** (this is a main use case). A person who previously left the task (REMOVED/REASSIGNED) can be assigned again as a new row.
- D13. Reassignment is distinct from add/remove in the edit form: those keep their Phase 3 meaning (`ASSIGN_TASK` / `UNASSIGN_TASK`, status `REMOVED`). Add helper text in the edit form: "To hand someone's work to another person, use Reassign."
- D14. Visibility: only the Admin sees reassignment history. The new person simply sees "Task assigned to you." Nobody else learns who had it before. A person reassigned away no longer sees the task (404).

**History / audit**
- D15. The activity timeline is built from `audit_logs` (one row per meaningful action) rendered into sentences server-side. Status history remains the detailed status ledger and is surfaced through timeline entries (old -> new, actor, comment/reason). Do not build a second, duplicate event source.
- D16. All history/audit tables become **append-only at the database level** (V6 triggers reject `UPDATE`/`DELETE`), in addition to having no update/delete endpoints.
- D17. **[confirm]** Employees may see the due-date history (old -> new, when, reason) for tasks they are on, but **not who changed it**.

---

## 5. Database - migration `V6__history_and_management_controls.sql`

1. `task_reassignment_history`: add `from_status VARCHAR(20) NOT NULL`, `from_progress SMALLINT NOT NULL`, `to_status VARCHAR(20) NOT NULL`, `to_progress SMALLINT NOT NULL` (the table is empty in every environment, so `NOT NULL` is safe; verify). These satisfy "previous assignment status / new assignment status".
2. Append-only guard: one function `forbid_history_mutation()` raising `EXCEPTION 'History records are append-only'`, and `BEFORE UPDATE OR DELETE` (row-level) triggers on `audit_logs`, `task_status_history`, `task_due_date_history`, `task_reassignment_history`. Do **not** put it on `task_assignments`, `tasks` or `users`.
3. Indexes for the new queries: `audit_logs (created_at DESC)`, `audit_logs (user_id)`, `audit_logs (action)`, and composite `task_due_date_history (task_id, changed_at)` / `task_reassignment_history (task_id, created_at)` if the existing single-column indexes are not enough.
4. Test the migration on a database that already contains Phase 3 data (section 9.3). Hibernate runs with `ddl-auto=validate`, so entity mappings must match exactly.

---

## 6. Backend

### 6.1 Structure
Add, in the existing packages:
- entities `TaskDueDateHistory`, `TaskReassignmentHistory` (+ enum `ReassignmentClassification`); repositories for them. (No JPA entity for `audit_logs`.)
- services `TaskDueDateService`, `TaskReassignmentService`, `TaskActivityService`, `AuditLogService` (JdbcTemplate reads). `TaskService` is already large - **do not grow it further**.
- `TaskService`'s private helpers (`findTask`, `currentAssignments`, `recalculate`, visibility check, response mapping) are needed by the new services. Extract them into a shared package-private collaborator (e.g. a small `TaskLifecycle`/`TaskSupport` class) **without changing behaviour**, and make `TaskService` use it too. Do not copy/paste `recalculate`.
- `AuditLogController` (`/api/audit-logs`); add the new task endpoints to `TaskController` (or a `TaskHistoryController` if it stays readable).
- Records for all request/response types. **Separate employee response types** for anything employee-visible.

### 6.2 Due date
`PUT /api/tasks/{id}/due-date` (ADMIN) - body `{ "dueDate": "2026-09-23", "reason": "optional" }` -> `TaskResponse.forAdmin`.
- Load the task with the concurrency rule in 6.7. Enforce D3-D6. In one transaction: insert history row, update `tasks.due_date`, write **one** `CHANGE_DUE_DATE` audit row (old `{dueDate}`, new `{dueDate, reason, kind}`).
- `PUT /api/tasks/{id}` (edit): if `dueDate` differs, call this same service method (no reason) instead of writing the field directly, and make sure that exactly one history row and one audit row result (the edit path currently emits its own `CHANGE_DUE_DATE` audit diff - remove that duplicate). Clearing (null when a date exists) -> 400.
- `GET /api/tasks/{id}/due-date-history` (ADMIN, or an assigned employee; anyone else 404). Response: `{ originalDueDate, currentDueDate, extensionCount, changes: [{ id, changedAt, previousDueDate, newDueDate, kind, reason, changedBy }] }` in chronological order. **`changedBy` exists only in the Admin type**; the employee type has no such field.
- `TaskResponse` (both views) gains `originalDueDate` and `dueDateExtensions` so the list/detail can show "Extended 2 times" without an extra call. Compute for a whole page with **one grouped query** keyed by the page's task ids (no per-row queries) and keep the existing 2-3 query cost of the paged list.

### 6.3 Reassignment
`POST /api/tasks/{id}/reassign` (ADMIN) - body:

    {
      "fromAssignmentId": 12,
      "toUserId": 5,
      "reason": "Employee unavailable",
      "classification": "NEUTRAL_ADMINISTRATIVE",
      "keepProgress": false
    }

(`classification` and `keepProgress` optional.) Validation: `fromAssignmentId`, `toUserId` required; `reason` non-blank, max 255. Errors per D11/D12: 404 (task, or assignment not a *current* assignment of this task), 409 (task cancelled/completed, assignment completed, new person already on the task), 400 (new person inactive or not a team member).

Effects, all in **one transaction**:
1. Old assignment: `is_current=false`, status `REASSIGNED`, blocked/on-hold reasons cleared, its progress/started_at kept.
2. New assignment for `toUserId` per D10 (`assigned_at=now`; `started_at=now` only if progress is carried over and > 0).
3. Insert `task_reassignment_history` (from/to assignment and user ids, `reassigned_by`, reason, classification, `from_status`, `from_progress`, `to_status`, `to_progress`).
4. `task_status_history` rows: old assignment `<previous status> -> REASSIGNED`, new assignment `null -> <new status>` (comments like "Reassigned to X: <reason>" / "Reassigned from X").
5. One `REASSIGN_TASK` audit row: old `{fromUserId, fromUser, status, progress}`, new `{toUserId, toUser, status, progress, reason, classification, keepProgress}`.
6. Recompute the task (shared `recalculate`) and record any task-level status change exactly as Phase 3 does.

`GET /api/tasks/{id}/reassignment-history` (**ADMIN only**; employees get 403). Chronological list: `{ id, createdAt, from:{id,name,active}, to:{id,name,active}, reassignedBy:{id,name}, reason, classification, fromStatus, fromProgress, toStatus, toProgress }`. Users are returned with names even when deactivated (spec edge case 21).

Add `reassignmentCount` to the **Admin** `TaskResponse` only (batched like 6.2).

### 6.4 Activity timeline
`GET /api/tasks/{id}/activity?page=0&size=50` (ADMIN, or an assigned employee; anyone else 404). Chronological (oldest first), size default 50, max 200. Entry: `{ id, at, action, kind, summary, actorName, details }` where `actorName` and `details` (old/new maps) are **null for employees**.

Source = `audit_logs` where `entity_type='TASK'` and `entity_id=:taskId`, ordered `created_at, id`, read with parameterised SQL. `summary` is a sentence produced server-side per known action, e.g. "Task created", "Assigned to Najeeb Ahmed", "Najeeb Ahmed changed status from Assigned to In Progress", "Progress changed from 20% to 40%", "Due date moved from 20 Sep 2026 to 23 Sep 2026 (extended) - reason", "Task reassigned from Najeeb Ahmed to Abdul Haseeb - reason", "Task completed", "Task reopened (progress kept)". Unknown/future actions render a safe generic line for the Admin and are **hidden from employees**.

**Employee timeline whitelist** (deny by default):

| Action | Employee sees |
|---|---|
| `CREATE_TASK` | "Task created" (no actor) |
| `ASSIGN_TASK` | only when the subject is the viewer: "Task assigned to you" |
| `REASSIGN_TASK` | only when the new person is the viewer: "Task assigned to you". Never the previous person, reason, classification or actor. |
| `UNASSIGN_TASK` | hidden |
| assignment-level `CHANGE_STATUS`, `CHANGE_PROGRESS` | only the viewer's own ("You changed your status..."; if `adjustedByAdmin`: "Your status/progress was adjusted") |
| task-level `CHANGE_STATUS`, `COMPLETE_TASK`, `REOPEN_TASK`, `CANCEL_TASK`, `REINSTATE_TASK` | task-level sentence, no actor |
| `CHANGE_DUE_DATE` | "Due date moved from X to Y" + reason, no actor |
| `CHANGE_PRIORITY`, `UPDATE_TASK` | generic sentence, no actor, no old free-text |
| anything else | hidden |

Anything about another person (their assignment, status, progress, removal) is hidden from an employee.

### 6.5 Audit logs
`GET /api/audit-logs` (**ADMIN only**), paged (`page`, `size` default 20, max 100), newest first. Optional filters: `action`, `actorId`, `taskId`, `from`, `to` (dates, inclusive, `from` <= `to`). Build the SQL dynamically **with bound parameters only** (never concatenate user input). Row: `{ id, at, actor:{id,name}|null, entityType, entityId, taskNumber (resolved for TASK rows), action, summary, oldValue, newValue }`. There must be **no** create/update/delete audit endpoint.

### 6.6 Privacy rules (structural)
- Two response type families per feature (`...ForAdmin` / `...ForEmployee`); the employee types simply do not declare `changedBy`, `actorName`, `details`, `createdBy`, reassignment fields, emails, codes, roles.
- Services return 404 (not 403) when an employee asks about a task they are not currently on. Admin-only *list/read* endpoints that are not tied to task visibility (`reassignment-history`, `audit-logs`) use `@PreAuthorize("hasRole('ADMIN')")` (403 for employees).
- A regression check must assert the raw JSON an employee receives contains no email, employee code, role name, or previous assignee name.

### 6.7 Concurrency (edge case 22)
Reassignment and due-date change must not silently overwrite a concurrent change. Ensure every Phase 4 mutation increments `Task.version` (e.g. load the task with `LockModeType.OPTIMISTIC_FORCE_INCREMENT` via a repository method), so two Admin actions or an Admin action racing an employee update produce **one success and one 409** ("This record was updated by someone else. Please refresh and try again.") - never two "successes". `TaskAssignment.version` already protects a concurrent employee update to the same assignment.

---

## 7. Frontend

Match the existing look (light palette, compact tables, Tailwind tokens) and structure; keep components small; do not enlarge `TaskDetail.tsx` - extract.

1. **Types/API** (`types/tasks.ts`, `api/tasks.ts`, new `api/auditLogs.ts`): request/response types for the new endpoints; new `TaskResponse` fields (`originalDueDate`, `dueDateExtensions`, `reassignmentCount?`).
2. **Due date in the task header**: show "Due: 23 Sep 2026" (format dates as `D Mon YYYY` via a small shared `formatDate` util; use it wherever due dates are shown). If `dueDateExtensions > 0` show a small "Extended N times" link that expands an inline list of changes (old -> new, kind, when, reason; the Admin also sees who). Do not put history on the card itself.
3. **Change due date** (Admin, non-cancelled/non-completed): inline panel - date input, optional reason, Confirm/Back; warn "This date is in the past" when applicable; show backend messages via `apiErrorMessage`.
4. **Reassign** (Admin): a "Reassign" link on each *unfinished* current assignment (next to "Adjust"), opening an inline panel: current assignee (read-only), new assignee (active team members not already on the task), reason (required, presets via `<datalist>`), classification (optional select), progress choice (start at 0% / carry over progress, mirroring the reopen panel), Confirm/Back. Show `Task -> Reassign -> Select employee -> Enter reason -> Confirm`.
5. **Reassignment history** (Admin only): section in the detail showing "Originally assigned to A -> Reassigned to B", when, by whom, reason, classification (labelled as an Admin note), previous/new status and progress. Mark deactivated people "(deactivated)".
6. **Activity timeline** (both roles): collapsible "Activity" section, lazy-loaded when opened (own query under `["tasks","activity",id]`), rendering the server's `summary`, timestamp, and (Admin) actor. An employee who received a reassigned task just sees "Task assigned to you."
7. **Edit form**: due date read-only for existing tasks with "Use Change due date" text; helper text about Reassign (D13).
8. **Audit Logs page** (`pages/admin/AuditLogsPage.tsx`, route `/admin/audit-logs`, `ProtectedRoute` ADMIN): filter bar (action, actor, task number/id, from/to dates, Clear), paged table (time, actor, action, task, summary), a per-row expander showing old/new values read-only, empty and error states. Purely read-only - no edit/delete affordances. The sidebar link already exists.
9. Employees never see Reassign, Adjust, reassignment history, audit logs, or who changed a date. Route guards and API both enforce this.
10. After every mutation invalidate `["tasks"]` so list, detail, history and activity refresh.

---

## 8. Edge cases you must handle and test (spec section 40)

- Reassign while other people remain assigned; the task's derived status/progress recomputes correctly.
- **Repeated reassignment**: A -> B -> C -> A. Four+ history rows, correct chain, A can be reassigned back in (new row), no unique-index violation, nothing lost.
- Reassign after the task is overdue (past due date) and after a due-date change; due date changed after a reassignment. The history rows stay individually correct and ordered.
- **Repeated due-date changes**: several extensions, then an earlier date, then a further extension; counts and "original" are right; history strictly ordered; clearing rejected; same date rejected.
- Reassigning away from a **deactivated** employee; history still shows their name; deactivated users' records are never lost.
- Reopen -> reassign -> complete; reinstate -> reassign (existing Phase 3 actions keep working with the new state).
- A task with **no** current assignee after edits still behaves (Phase 3 -> `DRAFT`); reassign correctly refuses (no current assignment).
- Concurrency (6.7): parallel reassignments of the same assignment -> exactly one wins; reassign racing an employee progress update -> one succeeds, the other 409; parallel due-date changes.
- Unauthorised access: employees hitting every Admin endpoint (403), employees on a task they are not on (404), unauthenticated (401).
- Append-only: a direct SQL `UPDATE`/`DELETE` on each history/audit table is rejected by the trigger.
- Do not mutate or lose any Phase 3 behaviour (section 9.2 regression list).

---

## 9. Testing and verification

### 9.1 Automated (in the repo)
- **Unit tests** (pure JUnit, no DB), e.g. `DueDateRulesTest` (kind classification, extension count, original date derivation, same-date/clearing rejection), `ReassignmentRulesTest` (eligibility, carry-over status/progress), `ActivityRulesTest` / summary rendering and the **employee whitelist** (including unknown-action -> hidden, other person's events -> hidden). Put the logic in small pure classes like `TaskStatusRules` so it is testable.
- **A dependency-free end-to-end script** `scripts/verify-phase4.mjs` (plain Node `fetch`, no npm packages) that drives the real API through every scenario in section 8 with explicit PASS/FAIL assertions and a non-zero exit code on any failure. It reads the API base URL and the seeded passwords from environment variables, uses **unique task titles per run** (a per-run suffix) because of the duplicate-title guard, and can query the database through `docker exec <postgres-container> psql` for assertions on history rows and triggers.

### 9.2 Regression - these Phase 3 behaviours must still hold
Derived task status rules; employee can only set In Progress/On Hold/Blocked/Completed with the 100%/99% and reason rules; completed work is locked for the employee; cancel keeps finished work; reinstate restores work; reopen keep/reset; admin adjust; duplicate-title and duplicate-assignee guards (409, race-proof); task numbers unique under concurrency; paged/filtered/sorted list; employees see only `{id,name}` per person, no creator, no email; non-assignee gets 404; error mapping (bad enum/malformed JSON -> 400, unknown URL -> 404). Existing `TaskStatusRulesTest` (18 tests) still passes.

### 9.3 How to verify safely (never use the dev DB)
Run a **separate Compose project on different ports** so the dev stack and its data stay untouched:

    # backend + database, isolated
    DB_HOST_PORT=5544 BACKEND_HOST_PORT=8582 CORS_ALLOWED_ORIGINS=http://localhost:5575 docker compose -p itms-phase4 up -d --build postgres backend
    # seeded passwords are printed ONCE in the backend log
    docker compose -p itms-phase4 logs backend | grep "temporary password"

To prove the migration works on **existing data**, first start the *previous* backend image against the isolated database, create some tasks (a completed one, a cancelled one, an open one), then start the new build on the same database and confirm V6 applied and the data is intact.

Unit tests without a local JDK:

    docker build --target build -t itms-buildstage ./backend
    docker run --rm itms-buildstage ./mvnw test -Dtest="*RulesTest" -B

Frontend: `cd frontend && npm run build && npm run lint`; for the UI, run `VITE_API_URL=http://localhost:8582/api npm run dev -- --port 5575 --strictPort` against the isolated backend and click through the flows below. Tear everything down afterwards (`docker compose -p itms-phase4 down -v`, remove temporary images, stop the dev server).

**Manual UI checklist:** Admin creates a task with two people -> reassigns one (reason + classification) -> sees the reassignment history and timeline -> changes the due date three times (two extensions, one earlier) -> sees "Extended 2 times" and the history -> reassigns again. Employee B sees "Task assigned to you." and a filtered timeline with no trace of the previous person; the due-date history shows no "changed by". Employee A (reassigned away) no longer sees the task. Audit Logs page filters, pages and expands rows; nothing on it is editable.

---

## 10. Documentation to update
`API.md` (every new endpoint, request/response, error cases, employee vs Admin fields, the timeline whitelist), `DATABASE.md` (V6: columns, append-only triggers, definitions of original due date / extension / kind, the meaning of `REASSIGNED` vs `REMOVED`), `ARCHITECTURE.md` (roadmap: Phase 4 done/in progress; note the append-only guarantee and the timeline-from-audit design). Keep the existing style.

---

## 11. Working method
Work in milestones, each ending with a green build: **(1)** migration + entities + repositories, **(2)** due-date backend, **(3)** reassignment backend, **(4)** activity + audit backend, **(5)** frontend, **(6)** verification script, regression run, docs. Before coding, post a short plan (files to create/change). After each milestone, state what changed and how you verified it. Do not skip verification.

**Final report must contain:** what was built (by milestone), the exact files added/changed, every decision/assumption you made beyond section 4, verification results (unit test counts, e2e PASS/FAIL totals, migration-on-existing-data result, UI checklist result), anything you noticed but deliberately left alone (out of phase), and how the owner can verify manually.

## 12. Definition of done
- [ ] V6 applies cleanly to a database containing Phase 3 data; app starts with `ddl-auto=validate`.
- [ ] All new endpoints implemented with the exact permissions and privacy rules above.
- [ ] Nothing historical can be updated or deleted (API and database).
- [ ] Reassignment and due-date history are complete, ordered, and survive repeated changes.
- [ ] Employees receive no data they are not entitled to (asserted on raw JSON).
- [ ] Concurrent conflicting actions yield one success and one 409.
- [ ] Unit tests + `scripts/verify-phase4.mjs` + Phase 3 regression all pass; frontend builds with no TS errors and no new lint warnings.
- [ ] Docs updated. Nothing committed. No out-of-phase work.
