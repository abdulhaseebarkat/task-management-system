-- Phase 3 fixes: assignment REMOVED status, race-free task numbers, lifecycle timestamps.

-- 1. REMOVED = an employee taken off a task by an Admin edit. It is deliberately
--    distinct from REASSIGNED, which is reserved for the Phase 4 reassignment flow
--    (with reason + history) so "reassigned" in reports never includes plain removals.
ALTER TABLE task_assignments DROP CONSTRAINT task_assignments_status_check;
ALTER TABLE task_assignments ADD CONSTRAINT task_assignments_status_check CHECK (status IN
    ('ASSIGNED', 'IN_PROGRESS', 'ON_HOLD', 'BLOCKED', 'COMPLETED', 'REASSIGNED', 'CANCELLED', 'REMOVED'));

-- Before this migration the only way to become REASSIGNED was being unassigned in
-- the task edit form, so every existing REASSIGNED row is really a removal.
UPDATE task_assignments SET status = 'REMOVED' WHERE status = 'REASSIGNED';

-- 2. Task numbers come from a sequence instead of count()+1, which collided under
--    concurrent creates. Start after the highest number already issued.
DO $$
DECLARE
    next_number bigint;
BEGIN
    SELECT COALESCE(MAX(CAST(SUBSTRING(task_number FROM 6) AS BIGINT)), 0) + 1
    INTO next_number
    FROM tasks;
    EXECUTE format('CREATE SEQUENCE task_number_seq START WITH %s', next_number);
END $$;

-- 3. completed_at / cancelled_at were never populated. Backfill the best
--    available approximation (last update) for rows that already reached the state.
UPDATE tasks SET cancelled_at = updated_at WHERE status = 'CANCELLED' AND cancelled_at IS NULL;
UPDATE tasks SET completed_at = updated_at WHERE status = 'COMPLETED' AND completed_at IS NULL;
UPDATE task_assignments a
SET completed_at = t.updated_at
FROM tasks t
WHERE a.task_id = t.id AND a.status = 'COMPLETED' AND a.completed_at IS NULL;
