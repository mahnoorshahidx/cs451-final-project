-- Trip Budget Planner: add a currency to each trip (Currency Conversion feature)
--
-- Only needed if your trips table was created BEFORE this change.
-- New databases created from schema.sql already have the column.
--
-- 1. Check first:  SHOW COLUMNS FROM trips LIKE 'currency';
--    - No rows: run step 2.
--    - One row (the backend may have added it already): skip step 2 and run step 3.

USE trip_budget_planner;

-- 2. Add the column. Existing trips get USD.
ALTER TABLE trips ADD COLUMN currency CHAR(3) NOT NULL DEFAULT 'USD';

-- 3. Fix any trips that ended up with an empty currency
UPDATE trips SET currency = 'USD' WHERE currency IS NULL OR currency = '';
