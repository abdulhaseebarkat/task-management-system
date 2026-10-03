-- A priority edit on an already-open task previously left base_points frozen at whatever it was
-- when the assignment cycle started - task_points_events is append-only (see V7), so the fix can't
-- mutate that original row. REBASED is a new event type: inserted instead, carrying the new base
-- value while preserving whatever strike level the cycle was already at. See PointsService#
-- recalculateBasePointsForPriorityChange.
ALTER TABLE task_points_events DROP CONSTRAINT task_points_events_event_type_check;
ALTER TABLE task_points_events ADD CONSTRAINT task_points_events_event_type_check CHECK (event_type IN
    ('ASSIGNED', 'STRIKE_1', 'STRIKE_2', 'FAILED', 'COMPLETED', 'CANCELLED', 'REASSIGNED', 'REBASED'));
