-- Phase 9: task points / performance scoring system.
-- See AnalyticsService / PointsService Javadoc for the full scoring rules.

ALTER TABLE tasks DROP CONSTRAINT tasks_status_check;
ALTER TABLE tasks ADD CONSTRAINT tasks_status_check CHECK (status IN
    ('DRAFT', 'ASSIGNED', 'IN_PROGRESS', 'ON_HOLD', 'BLOCKED', 'COMPLETED', 'CANCELLED', 'REOPENED', 'FAILED'));

-- One row per scoring-relevant event on one assignment. Append-only, same guard as every other
-- history table. ASSIGNED starts a "scoring cycle" (base value set); STRIKE_1/STRIKE_2 are
-- progressive deadline-miss penalties within that cycle; COMPLETED/FAILED/CANCELLED/REASSIGNED
-- each close a cycle (COMPLETED and FAILED are the only two that count toward an employee's
-- earned/possible totals - CANCELLED and REASSIGNED are neutral, excluded from scoring entirely).
-- A fresh ASSIGNED row (on reopen/reinstate/reassignment-to) starts a new cycle for the same
-- assignment_id, so its history can span more than one cycle over time.
CREATE TABLE task_points_events (
    id                BIGSERIAL PRIMARY KEY,
    task_id           BIGINT       NOT NULL REFERENCES tasks(id),
    assignment_id     BIGINT       NOT NULL REFERENCES task_assignments(id),
    user_id           BIGINT       NOT NULL REFERENCES users(id),
    event_type        VARCHAR(20)  NOT NULL CHECK (event_type IN
                          ('ASSIGNED', 'STRIKE_1', 'STRIKE_2', 'FAILED', 'COMPLETED', 'CANCELLED', 'REASSIGNED')),
    base_points       NUMERIC(5,2) NOT NULL,
    points_delta      NUMERIC(5,2) NOT NULL DEFAULT 0,
    resulting_points  NUMERIC(5,2) NOT NULL,
    reason            VARCHAR(255),
    occurred_at       TIMESTAMPTZ  NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_points_events_assignment ON task_points_events(assignment_id, occurred_at);
CREATE INDEX idx_points_events_user ON task_points_events(user_id, occurred_at);
CREATE INDEX idx_points_events_task ON task_points_events(task_id);

CREATE TRIGGER trg_task_points_events_append_only
BEFORE UPDATE OR DELETE ON task_points_events
FOR EACH ROW
EXECUTE FUNCTION forbid_history_mutation();
