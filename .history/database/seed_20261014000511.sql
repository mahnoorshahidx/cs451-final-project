-- Trip Budget Planner
-- Sample development data
-- Owner: Hajar Wilkes
-- Status: Pending schema definition

-- TODO:
-- 1. Wait for the database tables to be finalized in schema.sql.
-- 2. Insert fictional users and trips.
-- 3. Add sample budgets, savings goals, and expenses.
-- 4. Add fictional bank accounts and transactions.
-- 5. Ensure foreign key references match existing records.

-- Do not insert data until the table definitions are ready.

-- TODO #1. Selects the database, but table creation 
--          happens in schema.sql, not seed.sql
USE trip_budget_planner;

INSERT INTO users (
    name,
    email,
    password_hash,
    created_at
)
SELECT
    'Test User',
    'testuser@example.com',
    '$2y$12$Y2QwuehaNZsSSmEP.1rO0O3A8yRP0eU/P0gbI6PVwacbJK/C5l75e',
    UTC_TIMESTAMP(6)
WHERE NOT EXISTS (
    SELECT 1
    FROM users
    WHERE email = 'testuser@example.com'
);