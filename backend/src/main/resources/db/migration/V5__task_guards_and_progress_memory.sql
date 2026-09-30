-- Phase 3 follow-ups: duplicate guards + remembering progress across a reopen.

-- 1. The progress an employee had just before marking their part COMPLETED (completing
--    forces 100%). Lets an Admin reopen a task and restore each person to where they were.
--    Capped at 99 because 100% is only valid for COMPLETED work.
ALTER TABLE task_assignments
    ADD COLUMN progress_before_completion SMALLINT
        CHECK (progress_before_completion BETWEEN 0 AND 99);

-- 2. A person can be a *current* assignee of a given task only once.
CREATE UNIQUE INDEX uq_task_assignments_one_current
    ON task_assignments (task_id, user_id)
    WHERE is_current;

-- 3. No two OPEN tasks may share a title within a category (case- and space-insensitive).
--    Cancelled and completed tasks are excluded, so a finished task's title can be reused
--    (e.g. a monthly task), and a cancelled task can be reinstated instead of re-created.
CREATE UNIQUE INDEX uq_tasks_open_title_category
    ON tasks (lower(btrim(title)), category_id)
    WHERE status NOT IN ('CANCELLED', 'COMPLETED');

-- 4. Task list is sorted by last update by default.
CREATE INDEX idx_tasks_updated_at ON tasks (updated_at DESC);
