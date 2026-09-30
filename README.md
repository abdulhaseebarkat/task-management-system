# SLM Tires — IT Task Management System

Internal task management and performance-monitoring system for the SLM Tires IT department.

See [`ARCHITECTURE.md`](./ARCHITECTURE.md), [`DATABASE.md`](./DATABASE.md), and [`API.md`](./API.md) for design detail. This README covers setup only.

## Stack

- **Backend**: Java 21, Spring Boot 4, Spring Data JPA, Spring Security, PostgreSQL, Flyway
- **Frontend**: React 19, TypeScript, Vite, Tailwind CSS, shadcn/ui, React Router, TanStack Query

## Running with Docker (recommended)

```bash
cp .env.example .env
docker compose up --build
```

- Frontend: http://localhost:5174
- Backend API: http://localhost:8081/api (health check: http://localhost:8081/api/health)
- Postgres: localhost:5433

These non-default ports (5174/8081/5433 instead of 5173/8080/5432) avoid clashing with any other local project already using the conventional ones. Override via `DB_HOST_PORT` / `BACKEND_HOST_PORT` / `FRONTEND_HOST_PORT` in `.env` if you'd rather use different values.

## Running locally without Docker

Requires a local JDK 21 + Postgres 16, or point `DB_HOST`/`DB_PORT` at a Postgres instance started via `docker compose up postgres`.

**Backend**

```bash
cd backend
./mvnw spring-boot:run
```

**Frontend**

```bash
cd frontend
npm install
cp .env.example .env
npm run dev
```

## Environment variables

See `.env.example` at the repo root (used by `docker-compose.yml`) and `frontend/.env.example` (used by Vite). Never commit a real `.env` file or a production `JWT_SECRET`.

## Project status

Currently through **Phase 8 — Reporting**: authentication, user management, task CRUD, multi-member assignment, status/progress/priority/category/due dates, reassignment and due-date history, audit logs, employee dashboard/comments/notifications, the Admin analytics dashboard, WebSocket push for notifications/task-detail/dashboard refresh, and date-filtered individual/department reports with PDF and Excel export. See the development roadmap in `ARCHITECTURE.md` for what's next.
