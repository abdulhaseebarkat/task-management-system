-- Lets an Admin decide, per due-date extension, whether it costs the assignee(s) points.
-- Defaults true so every existing row keeps the effect it already had (it already counted).
ALTER TABLE task_due_date_history
    ADD COLUMN counts_toward_strikes BOOLEAN NOT NULL DEFAULT true;
