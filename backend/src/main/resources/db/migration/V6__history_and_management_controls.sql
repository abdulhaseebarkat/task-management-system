-- Phase 4: append-only history, reassignment metadata, and audit indexes.

ALTER TABLE task_reassignment_history
    ADD COLUMN from_status VARCHAR(20) NOT NULL DEFAULT 'ASSIGNED',
    ADD COLUMN from_progress SMALLINT NOT NULL DEFAULT 0,
    ADD COLUMN to_status VARCHAR(20) NOT NULL DEFAULT 'ASSIGNED',
    ADD COLUMN to_progress SMALLINT NOT NULL DEFAULT 0;

CREATE OR REPLACE FUNCTION forbid_history_mutation()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'History records are append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_logs_append_only
BEFORE UPDATE OR DELETE ON audit_logs
FOR EACH ROW
EXECUTE FUNCTION forbid_history_mutation();

CREATE TRIGGER trg_task_status_history_append_only
BEFORE UPDATE OR DELETE ON task_status_history
FOR EACH ROW
EXECUTE FUNCTION forbid_history_mutation();

CREATE TRIGGER trg_task_due_date_history_append_only
BEFORE UPDATE OR DELETE ON task_due_date_history
FOR EACH ROW
EXECUTE FUNCTION forbid_history_mutation();

CREATE TRIGGER trg_task_reassignment_history_append_only
BEFORE UPDATE OR DELETE ON task_reassignment_history
FOR EACH ROW
EXECUTE FUNCTION forbid_history_mutation();

CREATE INDEX idx_audit_logs_created_at ON audit_logs(created_at DESC);
CREATE INDEX idx_audit_logs_user_id ON audit_logs(user_id);
CREATE INDEX idx_audit_logs_action ON audit_logs(action);
CREATE INDEX idx_task_due_date_history_task_changed_at ON task_due_date_history(task_id, changed_at);
CREATE INDEX idx_task_reassignment_history_task_created_at ON task_reassignment_history(task_id, created_at);
