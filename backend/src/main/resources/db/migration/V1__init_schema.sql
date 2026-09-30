-- SLM Tires IT Task Management System - initial schema
-- See ARCHITECTURE.md / DATABASE.md for the ERD and design rationale.

CREATE TABLE users (
    id              BIGSERIAL PRIMARY KEY,
    employee_code   VARCHAR(20)  NOT NULL UNIQUE,
    name            VARCHAR(150) NOT NULL,
    email           VARCHAR(150) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    role            VARCHAR(20)  NOT NULL CHECK (role IN ('BOSS', 'TEAM_MEMBER')),
    department      VARCHAR(100),
    manager_id      BIGINT REFERENCES users(id),
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE task_categories (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(100) NOT NULL UNIQUE,
    is_active   BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE tasks (
    id                BIGSERIAL PRIMARY KEY,
    task_number       VARCHAR(20)  NOT NULL UNIQUE,
    title             VARCHAR(200) NOT NULL,
    description       TEXT,
    priority          VARCHAR(20)  NOT NULL CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    category_id       BIGINT       NOT NULL REFERENCES task_categories(id),
    status            VARCHAR(20)  NOT NULL CHECK (status IN
                          ('DRAFT', 'ASSIGNED', 'IN_PROGRESS', 'ON_HOLD', 'BLOCKED', 'COMPLETED', 'CANCELLED', 'REOPENED')),
    overall_progress  SMALLINT     NOT NULL DEFAULT 0 CHECK (overall_progress BETWEEN 0 AND 100),
    due_date          DATE,
    created_by        BIGINT       NOT NULL REFERENCES users(id),
    version           INTEGER      NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    completed_at      TIMESTAMPTZ,
    cancelled_at      TIMESTAMPTZ
);

CREATE TABLE task_assignments (
    id              BIGSERIAL PRIMARY KEY,
    task_id         BIGINT      NOT NULL REFERENCES tasks(id),
    user_id         BIGINT      NOT NULL REFERENCES users(id),
    status          VARCHAR(20) NOT NULL CHECK (status IN
                        ('ASSIGNED', 'IN_PROGRESS', 'ON_HOLD', 'BLOCKED', 'COMPLETED', 'REASSIGNED', 'CANCELLED')),
    progress        SMALLINT    NOT NULL DEFAULT 0 CHECK (progress BETWEEN 0 AND 100),
    is_current      BOOLEAN     NOT NULL DEFAULT TRUE,
    blocked_reason  VARCHAR(255),
    on_hold_reason  VARCHAR(255),
    version         INTEGER     NOT NULL DEFAULT 0,
    assigned_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    started_at      TIMESTAMPTZ,
    completed_at    TIMESTAMPTZ,
    CONSTRAINT chk_completed_is_full_progress CHECK (status <> 'COMPLETED' OR progress = 100)
);

CREATE TABLE task_due_date_history (
    id                  BIGSERIAL PRIMARY KEY,
    task_id             BIGINT      NOT NULL REFERENCES tasks(id),
    previous_due_date   DATE,
    new_due_date        DATE        NOT NULL,
    changed_by          BIGINT      NOT NULL REFERENCES users(id),
    reason              VARCHAR(255),
    changed_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE task_reassignment_history (
    id                  BIGSERIAL PRIMARY KEY,
    task_id             BIGINT      NOT NULL REFERENCES tasks(id),
    from_assignment_id  BIGINT      NOT NULL REFERENCES task_assignments(id),
    from_user_id        BIGINT      NOT NULL REFERENCES users(id),
    to_assignment_id    BIGINT      NOT NULL REFERENCES task_assignments(id),
    to_user_id          BIGINT      NOT NULL REFERENCES users(id),
    reassigned_by       BIGINT      NOT NULL REFERENCES users(id),
    reason              VARCHAR(255) NOT NULL,
    classification      VARCHAR(30) CHECK (classification IN
                            ('NEUTRAL_ADMINISTRATIVE', 'PERFORMANCE_RELATED', 'OPERATIONAL', 'OTHER')),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE task_status_history (
    id              BIGSERIAL PRIMARY KEY,
    task_id         BIGINT      NOT NULL REFERENCES tasks(id),
    assignment_id   BIGINT      REFERENCES task_assignments(id),
    old_status      VARCHAR(20),
    new_status      VARCHAR(20) NOT NULL,
    changed_by      BIGINT      NOT NULL REFERENCES users(id),
    comment         VARCHAR(500),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE task_comments (
    id              BIGSERIAL PRIMARY KEY,
    task_id         BIGINT      NOT NULL REFERENCES tasks(id),
    assignment_id   BIGINT      REFERENCES task_assignments(id),
    user_id         BIGINT      NOT NULL REFERENCES users(id),
    comment         TEXT        NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ
);

CREATE TABLE task_attachments (
    id              BIGSERIAL PRIMARY KEY,
    task_id         BIGINT       NOT NULL REFERENCES tasks(id),
    uploaded_by     BIGINT       NOT NULL REFERENCES users(id),
    file_name       VARCHAR(255) NOT NULL,
    file_path       VARCHAR(500) NOT NULL,
    file_type       VARCHAR(100),
    file_size       BIGINT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE notifications (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT       NOT NULL REFERENCES users(id),
    task_id     BIGINT       REFERENCES tasks(id),
    type        VARCHAR(50)  NOT NULL,
    title       VARCHAR(200) NOT NULL,
    message     VARCHAR(500),
    is_read     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE audit_logs (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT      REFERENCES users(id),
    entity_type VARCHAR(50) NOT NULL,
    entity_id   BIGINT      NOT NULL,
    action      VARCHAR(50) NOT NULL,
    old_value   JSONB,
    new_value   JSONB,
    metadata    JSONB,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Indexes supporting the query patterns from the dashboard, task list and reports
CREATE INDEX idx_users_manager ON users(manager_id);

CREATE INDEX idx_tasks_status ON tasks(status);
CREATE INDEX idx_tasks_due_date ON tasks(due_date);
CREATE INDEX idx_tasks_category ON tasks(category_id);
CREATE INDEX idx_tasks_created_by ON tasks(created_by);

CREATE INDEX idx_task_assignments_task_current ON task_assignments(task_id, is_current);
CREATE INDEX idx_task_assignments_user_status ON task_assignments(user_id, status);

CREATE INDEX idx_due_date_history_task ON task_due_date_history(task_id);
CREATE INDEX idx_reassignment_history_task ON task_reassignment_history(task_id);
CREATE INDEX idx_status_history_task ON task_status_history(task_id);
CREATE INDEX idx_comments_task ON task_comments(task_id);
CREATE INDEX idx_attachments_task ON task_attachments(task_id);

CREATE INDEX idx_notifications_user_read ON notifications(user_id, is_read);
CREATE INDEX idx_audit_logs_entity ON audit_logs(entity_type, entity_id);
