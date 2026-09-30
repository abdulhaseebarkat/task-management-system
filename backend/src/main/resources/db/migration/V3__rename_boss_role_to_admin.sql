-- Renames the BOSS role to ADMIN throughout, including existing data and employee codes.
ALTER TABLE users DROP CONSTRAINT users_role_check;

UPDATE users
SET role = 'ADMIN',
    employee_code = REPLACE(employee_code, 'BOSS', 'ADMIN')
WHERE role = 'BOSS';

ALTER TABLE users ADD CONSTRAINT users_role_check CHECK (role IN ('ADMIN', 'TEAM_MEMBER'));
