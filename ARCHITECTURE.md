# Architecture

## Core principle

A task is a persistent business object. It is never deleted and recreated when its assignee, due date, priority, status, or progress changes — every change is appended to a history table. See `DATABASE.md`.

## Backend — Spring Boot modular monolith

```
com.slmtires.itms
├── config       # CORS, Security, JPA/Flyway wiring
├── controller   # REST endpoints only — no business logic
├── service      # business logic, transaction boundaries, history/audit writes
├── repository   # Spring Data JPA repositories
├── entity       # JPA entities, mirror DATABASE.md exactly
├── dto          # request/response shapes — entities are never returned directly
├── mapper       # entity <-> DTO conversion
├── security     # JWT filter, user details, role checks (Phase 2)
└── exception    # ApiException hierarchy + GlobalExceptionHandler
```

Deliberately a single deployable monolith, not microservices — appropriate for ~6 initial users and a small IT team to operate. Revisit only if a real scaling need appears.

Business rules enforced in the service layer, never trusted from the client:
- Row-level scoping: a `TEAM_MEMBER` only ever sees their own `task_assignments`, enforced from the authenticated principal, never from a request parameter.
- `COMPLETED` assignment status always implies `progress = 100` (DB `CHECK` constraint backs this up).
- Optimistic locking (`version` column) on `tasks` and `task_assignments` to catch concurrent Admin/employee edits.

## Frontend — React + TypeScript

```
src/
├── api/         # typed API client functions (one file per resource)
├── components/  # shared, reusable UI (shadcn/ui primitives + composed pieces)
├── pages/       # route-level screens, split by role (admin/, employee/)
├── routes/      # route guards
├── types/       # shared TypeScript types mirroring backend DTOs
└── lib/         # utilities (cn, etc.)
```

State management: TanStack Query for all server state (no client-side data duplication/store needed); local component state / React context only for UI-only state (e.g. current filter selection). No Redux — unnecessary at this scale.

**Dev-server file watching in Docker on Windows.** The frontend container bind-mounts `./frontend:/app` and runs `vite --host` directly (no rebuild needed for a source edit). But native filesystem change events from a Windows-host edit don't reliably reach Vite's watcher inside the Linux container over that bind mount, so edits could silently stop showing up in the browser with no error - the dev server just keeps serving what it last loaded. `vite.config.ts` now sets `server.watch.usePolling: true` to guarantee changes are picked up; if a change still doesn't seem to appear, `docker compose restart frontend` forces a fresh read of every file regardless of watch state.

## Authentication & Authorization

JWT-based (Phase 2). The backend is the single source of truth for permissions — the frontend hides controls a role can't use, but every endpoint independently re-checks the caller's role and, where relevant, ownership of the resource. See the permission matrix agreed in Phase 0 (Admin vs. Team Member).

## Real-time updates

WebSockets (STOMP over Spring's WebSocket support, `/ws`), scoped narrowly to: notification push, task-detail live refresh, and Admin dashboard KPI refresh on completion/block events. Not used for every field edit — most of the UI refetches via TanStack Query on navigation/focus, and the WebSocket push is purely a trigger for that same refetch (never a parallel payload schema to keep in sync). Every payload is a lightweight "something changed" signal - see `RealtimeEventService`.

A browser's native WebSocket constructor can't set an `Authorization` header on the handshake, so `/ws` is permitted anonymously in `SecurityConfig`; real per-connection authentication happens on the STOMP CONNECT frame instead (`StompAuthChannelInterceptor`), where a bearer token is just another STOMP header the client can set freely. No valid CONNECT means no session Principal, which every `/user/**` destination and every subsequent frame depends on.

If the socket is disconnected, the existing 60s notification poll and on-focus refetches remain as the fallback - nothing depends on the socket being up.

## Points & performance scoring (Phase 9)

Every task carries a base point value from its priority (LOW=2, MEDIUM=3, HIGH=5, CRITICAL=8). Per assignee, per "cycle" (started by an `ASSIGNED` ledger event - initial assignment, becoming a reassignment's new assignee, or a fresh start on reopen/reinstate): **points are never deducted automatically just because a task sits overdue.** The only thing that costs points is an Admin explicitly granting a due-date extension with "deduct points" left checked (`TaskDueDateHistory.countsTowardStrikes`, defaulted true - see `TaskDueDateService`) - the 1st such extension drops resulting points to 50% of base (`STRIKE_1`), the 2nd drops to 25% (`STRIKE_2`), and the 3rd marks the task `FAILED` at 0 points - permanently, for that assignment, regardless of what happens to the task afterward. A task can sit arbitrarily overdue, untouched, forever, and still hold full points until an Admin acts. An extension granted with the checkbox unchecked still moves the due date and still counts toward the visible extension count / advisory cap, it just never enters the strike math. Completing before a 3rd strike locks in whatever the strike level left. Cancelling or being reassigned away before a 3rd strike is neutral - excluded from scoring entirely. The 3-strike/3-extension cap is advisory only: the Admin can still extend or reassign past it. Points are never split across co-assignees on a shared task either - each current assignee has their own full-base-value cycle.

`FAILED` is a real `TaskStatus` value, filterable and visible everywhere status is shown to both Admin and employees - but status and scoring are tracked separately by design: a task can show `TaskStatus.COMPLETED` (the real operational outcome) while a specific assignee's ledger permanently recorded a `FAILED` outcome earlier in that same cycle. Points visibility: a team member sees their own points (own individual report, own per-task points panel) but never anyone else's - a co-assignee's points on a shared task are invisible to them, enforced server-side in `AnalyticsService`/`AnalyticsController`, not just hidden in the UI. There is no department-wide comparison available to a team member; the dashboard/leaderboard endpoints remain Admin-only outright.

Append-only ledger (`task_points_events`, guarded by the same `forbid_history_mutation()` trigger as the other history tables) - see `PointsService`. No new scheduler: strikes and the `FAILED` status transition are evaluated at natural task-mutation touch-points (`TaskLifecycleSupport.recalculate()`, called from every task-mutating service method) via the idempotent `PointsService.evaluateAndSeal()`, rather than a background job.

There are three ways into the same terminal `FAILED` outcome, all sharing one sealing method (`PointsService#sealFailedByAdmin`) and identical scoring math (0 points, counted): the 3rd due-date strike above; a reassignment with `deductPoints: true`; and the Admin directly failing a still-current assignment via `POST /api/tasks/{id}/assignments/{assignmentId}/fail` (reason required). The direct Fail action is the only one of the three where the failed assignment is still current, so `recalculate()` flips the task's own live status to `FAILED` in the same call - the other two involve an assignment that's already being made non-current. None of the three touch the assignment's own `status`/`progress`.

**`pointsPossible` is live; `pointsEarned` and `pointsLost` only move when something actually happens.** `AnalyticsService#countableEvents` is the single choke point every points aggregate (leaderboard, efficiency trend, strike distribution, individual summary/breakdown) runs through: it takes every resolved cycle's terminal `COMPLETED`/`FAILED` event (there can be more than one per assignment if it's been reopened) and adds, for each still-open assignment, its current cycle's latest event (`ASSIGNED`/`STRIKE_1`/`STRIKE_2`) valued at whatever it presently holds. From that combined set:
- `pointsPossible` sums every event's `basePoints` - this is the number that shows up the instant a task is assigned, whether open or resolved.
- `pointsEarned` sums `resultingPoints` from `COMPLETED` events only - an open task, however untouched, never contributes here.
- `pointsLost` sums `basePoints - resultingPoints` across every event, open or resolved - a strike that's already landed on a still-open task counts immediately, without waiting for the task to finish.
- `efficiencyRate = earned ÷ (earned + lost)` (`AnalyticsService#efficiencyRate`) - of the points already decided one way or the other, what fraction were kept, so a pile of ordinary open work never drags it down.

An open cycle's live `pointsLost` contribution and its eventual `COMPLETED` value are computed from the exact same numbers, so resolving a task never causes a jump - only a genuine strike, a failure, or the Fail action changes anything. A neutrally-closed cycle (`CANCELLED`, or `REASSIGNED` without the penalty) is excluded the moment it closes, dropping straight out of `pointsPossible` too, not just `pointsEarned`/`pointsLost`.

## Report exports (PDF/Excel)

`ReportExportService` builds both formats from the same report DTOs the on-screen pages consume - no separate export-only queries. As of the Admin's request for exports that visually match the app, both formats carry real charts: `ChartImageRenderer` draws them with plain Java2D (`BufferedImage`/`Graphics2D`, no charting library) using the same shapes and theme colors as the frontend's hand-rolled SVG charts, and hands back PNG bytes that OpenPDF embeds inline and POI embeds into a dedicated "Charts" sheet. One rendering method backs both formats, so a chart never drifts between the two exports.

**The PDF and Excel exports intentionally carry different amounts of content.** The Excel export keeps every data table the on-screen report shows (summary, distributions, workload, activity, etc.) plus the charts and the per-task ledger. The PDF, per a later, more specific instruction from the Admin, is deliberately lean: `departmentPdf`/`employeePdf` build *only* the chart images (`chartImage`/`ChartImageRenderer`) and, for the individual report, the "Individual Tasks" table right after them - no summary tiles, no distribution tables, no workload/activity lists. The now-unused `kvTable` PDF helper was deleted rather than left dead once both call sites were removed.

The individual export's per-task ledger (`EmployeeReportResponse#taskDetails`, built by `AnalyticsService#buildTaskDetails`) is one row per (task, this employee's assignment), with the task's due-date history (original date plus every later change, each flagged for whether it counted toward a strike) and how many points were actually deducted on it specifically.

## Development roadmap

0. Requirements & architecture *(done)*
1. Project foundation *(done)* — scaffolding, schema, CORS/exceptions/logging
2. Authentication & users *(done)* — JWT, roles, user management screens
3. Core task management *(done)* — CRUD, multi-member assignment, status/progress/priority/category/due date
4. History & management controls *(done)* — reassignment, due-date history, audit trail
5. Employee experience *(done)* — dashboard, My Tasks, comments, blocked/on-hold workflows
6. Admin dashboard & analytics *(done)* — KPIs, charts, employee/team performance views
7. Real-time system *(done)* — WebSockets for notifications and live dashboard/task updates
8. Reporting *(done)* — date-filtered individual/department reports, PDF/Excel export
9. Points & performance scoring *(done, current)* — priority-based points, strikes, `FAILED` status; own-points visible to each employee, department-wide leaderboards/reports Admin-only
10. QA & hardening — testing, security review, edge-case verification

Each phase ships buildable, verified code before the next starts.
