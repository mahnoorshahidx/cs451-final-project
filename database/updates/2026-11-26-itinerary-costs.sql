-- Trip Budget Planner: UC6 travel search and itinerary costs
--
-- Run BEFORE starting the updated backend. Needs MySQL 8.0.16+ (CHECK constraints).
-- Check with: SELECT VERSION();   (Aiven MySQL 8 is fine.)
--
-- What it does:
--   1. itinerary_activities: optional cost, search-result source and budget amount columns
--   2. trip_budgets: a version number used to avoid lost budget updates
-- Existing activities and budgets are not changed: the new columns are empty (NULL),
-- and existing budgets start at version 0.

USE trip_budget_planner;

-- ---------------------------------------------------------------------------
-- 0. Check first
-- ---------------------------------------------------------------------------
-- SHOW COLUMNS FROM itinerary_activities LIKE 'estimated_cost';
-- SHOW COLUMNS FROM trip_budgets LIKE 'version';
-- If the backend was started before this script, Hibernate may already have added some
-- of these columns (without the checks and unique key). In that case, drop the columns it
-- added and run this script again, e.g.:
--   ALTER TABLE itinerary_activities DROP COLUMN estimated_cost, DROP COLUMN cost_currency, ...;
--   ALTER TABLE trip_budgets DROP COLUMN version;

-- ---------------------------------------------------------------------------
-- 1. Itinerary costs and search results
-- ---------------------------------------------------------------------------
ALTER TABLE itinerary_activities
    ADD COLUMN estimated_cost DECIMAL(12, 2) NULL,
    ADD COLUMN cost_currency CHAR(3) NULL,
    ADD COLUMN source_type VARCHAR(20) NULL,
    ADD COLUMN source_provider VARCHAR(30) NULL,
    ADD COLUMN source_item_id VARCHAR(120) NULL,
    ADD COLUMN source_segment VARCHAR(10) NULL,
    ADD COLUMN budget_amount_added DECIMAL(12, 2) NULL,
    ADD COLUMN budget_amount_currency CHAR(3) NULL;

ALTER TABLE itinerary_activities
    ADD CONSTRAINT chk_itinerary_estimated_cost CHECK (estimated_cost IS NULL OR estimated_cost >= 0),
    ADD CONSTRAINT chk_itinerary_cost_currency CHECK ((estimated_cost IS NULL) = (cost_currency IS NULL)),
    ADD CONSTRAINT chk_itinerary_budget_amount CHECK (budget_amount_added IS NULL OR budget_amount_added >= 0),
    ADD CONSTRAINT chk_itinerary_budget_currency CHECK ((budget_amount_added IS NULL) = (budget_amount_currency IS NULL)),
    ADD CONSTRAINT chk_itinerary_source_type CHECK (source_type IS NULL OR source_type IN ('FLIGHT', 'HOTEL', 'RESTAURANT', 'ACTIVITY')),
    ADD CONSTRAINT chk_itinerary_source_segment CHECK (source_segment IS NULL OR source_segment IN ('MAIN', 'OUTBOUND', 'RETURN')),
    -- Each search result (and flight leg) only once per trip. Manual and AI rows have
    -- NULL here, and MySQL allows any number of NULLs in a unique key.
    ADD CONSTRAINT uq_itinerary_source_item UNIQUE (trip_id, source_provider, source_item_id, source_segment);

-- ---------------------------------------------------------------------------
-- 2. Budget version (for safe concurrent edits)
-- ---------------------------------------------------------------------------
ALTER TABLE trip_budgets ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- ---------------------------------------------------------------------------
-- 3. Verify
-- ---------------------------------------------------------------------------
-- Existing activities have no cost yet (expect the same number as all activities):
--   SELECT COUNT(*) FROM itinerary_activities WHERE estimated_cost IS NULL;
--   SHOW CREATE TABLE itinerary_activities;
--   SELECT id, version FROM trip_budgets;
