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
--          happens in schema.sql
USE trip_budget_planner;


-- TODO #2: PARTIALLY COMPLETE
-- Test users are created through the Spring Boot registration
-- endpoint so the backend handles BCrypt password hashing.
--
-- Adds a fictional Denver trip for testuser@example.com.
-- The user must already exist before this script runs.
-- The trip will not be inserted if the user is missing.
--
-- Retrieves the user's ID by email rather than assuming
-- every database assigns the same user ID.
-- Prevents inserting the same trip twice for that user.

INSERT INTO trips (
    destination,
    end_date,
    name,
    number_of_travelers,
    start_date,
    user_id
)
SELECT
    'Denver, Colorado',
    '2027-06-14',
    'Denver Vacation',
    2,
    '2027-06-07',
    users.id
FROM users
WHERE users.email = 'testuser@example.com'
AND NOT EXISTS (
    SELECT 1
    FROM trips
    WHERE trips.user_id = users.id
      AND trips.name = 'Denver Vacation'
);


-- TODO #3: PENDING
-- Add sample budgets, savings goals, and expenses
-- after their database tables are defined.


-- TODO #4: PARTIALLY READY
-- The bank_accounts table exists, but no bank account
-- sample data has been inserted yet.
-- The transactions table is still pending.
-- Account fingerprints must follow the backend's method.


-- TODO #5: PARTIALLY COMPLETE
-- The trips table has a foreign key referencing users(id).
-- The Denver trip uses an existing user's ID.
-- Remaining foreign key relationships still need verification.