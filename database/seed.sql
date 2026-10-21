-- Trip Budget Planner
-- Sample development data
-- Owner: Hajar Wilkes
-- Prerequisite: run schema.sql first.
-- Prerequisite: register testuser@example.com using POST /api/auth/register
-- so Spring Boot creates a valid BCrypt password hash. No passwords are stored here.
-- If the test user does not exist, these sample rows are skipped.
-- Bank accounts should be connected via POST /api/bank-accounts so the
-- backend generates the correct keyed fingerprint; do not fabricate it in SQL.

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

-- TODO #3: Estimated trip budget ($2,600 total).
INSERT INTO budgets (trip_id, flights, hotel, rental_car, food, activities, shopping)
SELECT t.id, 400.00, 900.00, 350.00, 450.00, 300.00, 200.00
FROM trips t
JOIN users u ON u.id = t.user_id
WHERE u.email = 'testuser@example.com'
  AND t.name = 'Denver Vacation'
  AND NOT EXISTS (SELECT 1 FROM budgets b WHERE b.trip_id = t.id);

-- TODO #3: One savings goal for Denver Vacation.
INSERT INTO savings_goals
    (trip_id, target_amount, initial_saved_amount, target_date, planned_monthly_contribution)
SELECT t.id, 2600.00, 500.00, '2027-05-31', 300.00
FROM trips t
JOIN users u ON u.id = t.user_id
WHERE u.email = 'testuser@example.com'
  AND t.name = 'Denver Vacation'
  AND NOT EXISTS (SELECT 1 FROM savings_goals sg WHERE sg.trip_id = t.id);

-- TODO #3: Three fictional advance purchases, without duplicate reinsertion.
INSERT INTO expenses (trip_id, category, amount, expense_date, description)
SELECT t.id, samples.category, samples.amount, samples.expense_date, samples.description
FROM trips t
JOIN users u ON u.id = t.user_id
CROSS JOIN (
    SELECT 'Flights' AS category, 350.00 AS amount,
           CAST('2026-10-15' AS DATE) AS expense_date,
           'Round-trip airfare' AS description
    UNION ALL SELECT 'Hotel', 200.00, '2026-10-16', 'Hotel reservation deposit'
    UNION ALL SELECT 'Activities', 75.00, '2026-10-17', 'Rocky Mountain tour'
) samples
WHERE u.email = 'testuser@example.com'
  AND t.name = 'Denver Vacation'
  AND NOT EXISTS (
      SELECT 1 FROM expenses e
      WHERE e.trip_id = t.id AND e.category = samples.category
        AND e.amount = samples.amount AND e.expense_date = samples.expense_date
        AND e.description = samples.description
  );

-- TODO #4: Two manual savings contributions ($350 total).
-- bank_account_id stays NULL because the connected test bank account
-- belongs to a different user than the original Denver test trip.
INSERT INTO transactions
    (savings_goal_id, bank_account_id, amount, transaction_date, description)
SELECT sg.id, NULL, samples.amount, samples.transaction_date, samples.description
FROM savings_goals sg
JOIN trips t ON t.id = sg.trip_id
JOIN users u ON u.id = t.user_id
CROSS JOIN (
    SELECT 150.00 AS amount, CAST('2026-10-16' AS DATE) AS transaction_date,
           'October savings contribution' AS description
    UNION ALL SELECT 200.00, '2026-10-17', 'Additional trip savings'
) samples
WHERE u.email = 'testuser@example.com'
  AND t.name = 'Denver Vacation'
  AND NOT EXISTS (
      SELECT 1 FROM transactions tx
      WHERE tx.savings_goal_id = sg.id
        AND tx.bank_account_id IS NULL
        AND tx.amount = samples.amount
        AND tx.transaction_date = samples.transaction_date
        AND tx.description = samples.description
  );

-- TODO #5: Foreign key constraints are declared in schema.sql.
-- This seed uses existing parents and never assumes auto-increment IDs.
