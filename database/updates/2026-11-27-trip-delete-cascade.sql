-- Trip Budget Planner: UC12 delete a trip
--
-- Problem: deleting a trip failed with "Cannot delete or update a parent row" when the trip
-- had expenses or notifications, because those foreign keys had no ON DELETE CASCADE.
--
-- What it does: makes these four foreign keys ON DELETE CASCADE
--   expenses.trip_id              -> trips.id          (fk_expenses_trip)
--   notifications.trip_id         -> trips.id          (fk_notifications_trip)
--   savings_goals.trip_id         -> trips.id          (fk_savings_goals_trip)
--   transactions.savings_goal_id  -> savings_goals.id  (fk_transactions_goal)
-- Nothing else changes. Users, bank accounts and the other foreign keys are untouched
-- (transactions.bank_account_id still has no cascade, so bank accounts are never deleted).
-- No data is changed.
--
-- Run it with the backend stopped. Needs MySQL 8.0. Works on a database created by
-- schema.sql AND on one created by Hibernate (ddl-auto), where the foreign keys have
-- generated names like FK8xyz...: the script finds them by column, not by name.
--
-- Safe to run more than once. MySQL commits every ALTER TABLE on its own (a script can't be
-- one transaction), so each foreign key is fixed in two small steps that each check the
-- current state first. If a run stops halfway (lost connection, error), just run it again:
-- finished steps are skipped and the rest are completed.
--
-- The backend deletes these rows itself too, so trip deletion also works before this runs.

USE trip_budget_planner;

-- ---------------------------------------------------------------------------
-- 1. Stop if there are orphaned rows (rows pointing at a trip or goal that doesn't exist)
-- ---------------------------------------------------------------------------
-- A cascade foreign key can't be added while such rows exist, so we check first and
-- stop BEFORE changing any foreign key. Normally all counts are 0.
SELECT
    (SELECT COUNT(*) FROM expenses e LEFT JOIN trips t ON t.id = e.trip_id WHERE t.id IS NULL)
        AS orphan_expenses,
    (SELECT COUNT(*) FROM notifications n LEFT JOIN trips t ON t.id = n.trip_id WHERE t.id IS NULL)
        AS orphan_notifications,
    (SELECT COUNT(*) FROM savings_goals g LEFT JOIN trips t ON t.id = g.trip_id WHERE t.id IS NULL)
        AS orphan_savings_goals,
    (SELECT COUNT(*) FROM transactions x LEFT JOIN savings_goals g ON g.id = x.savings_goal_id WHERE g.id IS NULL)
        AS orphan_contributions
INTO @orphan_expenses, @orphan_notifications, @orphan_goals, @orphan_contributions;

SET @orphans = @orphan_expenses + @orphan_notifications + @orphan_goals + @orphan_contributions;

SELECT @orphan_expenses AS orphan_expenses, @orphan_notifications AS orphan_notifications,
       @orphan_goals AS orphan_savings_goals, @orphan_contributions AS orphan_contributions;

-- If any count above is not 0, this line fails on purpose with
--   "Table 'trip_budget_planner.STOPPED_orphaned_rows_found_see_step_1' doesn't exist"
-- and the mysql client / MySQL Workbench stops here. Nothing has been changed.
-- Even when run with --force, every ALTER below is skipped while @orphans > 0.
-- To find the rows, run the matching query from step 1 with "SELECT e.*" instead of COUNT(*).
-- Delete them (they belong to trips that no longer exist) and run this script again.
SET @step = IF(@orphans = 0, 'DO 0',
    'SELECT 1 FROM `STOPPED_orphaned_rows_found_see_step_1`');
PREPARE step FROM @step; EXECUTE step; DEALLOCATE PREPARE step;

-- ---------------------------------------------------------------------------
-- 2. Make each foreign key ON DELETE CASCADE
-- ---------------------------------------------------------------------------
-- Each block does the same two steps for one column:
--   a) drop every foreign key on that column, except one that is already our named cascade key
--   b) add our named cascade key if it isn't there yet
-- (MySQL can't drop and re-add the same constraint name in one ALTER TABLE.)

-- ----- expenses.trip_id -----
SET @t = 'expenses', @c = 'trip_id', @rt = 'trips', @name = 'fk_expenses_trip';

SELECT GROUP_CONCAT(CONCAT('DROP FOREIGN KEY `', k.CONSTRAINT_NAME, '`') SEPARATOR ', ') INTO @drops
FROM information_schema.KEY_COLUMN_USAGE k
JOIN information_schema.REFERENTIAL_CONSTRAINTS r
  ON r.CONSTRAINT_SCHEMA = k.CONSTRAINT_SCHEMA AND r.TABLE_NAME = k.TABLE_NAME AND r.CONSTRAINT_NAME = k.CONSTRAINT_NAME
WHERE k.TABLE_SCHEMA = DATABASE() AND k.TABLE_NAME = @t AND k.COLUMN_NAME = @c AND k.REFERENCED_TABLE_NAME = @rt
  AND NOT (k.CONSTRAINT_NAME = @name AND r.DELETE_RULE = 'CASCADE');
SET @step = IF(@orphans = 0 AND @drops IS NOT NULL, CONCAT('ALTER TABLE `', @t, '` ', @drops), 'DO 0');
PREPARE step FROM @step; EXECUTE step; DEALLOCATE PREPARE step;

SELECT COUNT(*) INTO @has FROM information_schema.REFERENTIAL_CONSTRAINTS
WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = @t AND CONSTRAINT_NAME = @name AND DELETE_RULE = 'CASCADE';
SET @step = IF(@orphans = 0 AND @has = 0,
    CONCAT('ALTER TABLE `', @t, '` ADD CONSTRAINT `', @name, '` FOREIGN KEY (`', @c, '`) REFERENCES `', @rt, '` (`id`) ON DELETE CASCADE'),
    'DO 0');
PREPARE step FROM @step; EXECUTE step; DEALLOCATE PREPARE step;

-- ----- notifications.trip_id -----
SET @t = 'notifications', @c = 'trip_id', @rt = 'trips', @name = 'fk_notifications_trip';

SELECT GROUP_CONCAT(CONCAT('DROP FOREIGN KEY `', k.CONSTRAINT_NAME, '`') SEPARATOR ', ') INTO @drops
FROM information_schema.KEY_COLUMN_USAGE k
JOIN information_schema.REFERENTIAL_CONSTRAINTS r
  ON r.CONSTRAINT_SCHEMA = k.CONSTRAINT_SCHEMA AND r.TABLE_NAME = k.TABLE_NAME AND r.CONSTRAINT_NAME = k.CONSTRAINT_NAME
WHERE k.TABLE_SCHEMA = DATABASE() AND k.TABLE_NAME = @t AND k.COLUMN_NAME = @c AND k.REFERENCED_TABLE_NAME = @rt
  AND NOT (k.CONSTRAINT_NAME = @name AND r.DELETE_RULE = 'CASCADE');
SET @step = IF(@orphans = 0 AND @drops IS NOT NULL, CONCAT('ALTER TABLE `', @t, '` ', @drops), 'DO 0');
PREPARE step FROM @step; EXECUTE step; DEALLOCATE PREPARE step;

SELECT COUNT(*) INTO @has FROM information_schema.REFERENTIAL_CONSTRAINTS
WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = @t AND CONSTRAINT_NAME = @name AND DELETE_RULE = 'CASCADE';
SET @step = IF(@orphans = 0 AND @has = 0,
    CONCAT('ALTER TABLE `', @t, '` ADD CONSTRAINT `', @name, '` FOREIGN KEY (`', @c, '`) REFERENCES `', @rt, '` (`id`) ON DELETE CASCADE'),
    'DO 0');
PREPARE step FROM @step; EXECUTE step; DEALLOCATE PREPARE step;

-- ----- savings_goals.trip_id -----
SET @t = 'savings_goals', @c = 'trip_id', @rt = 'trips', @name = 'fk_savings_goals_trip';

SELECT GROUP_CONCAT(CONCAT('DROP FOREIGN KEY `', k.CONSTRAINT_NAME, '`') SEPARATOR ', ') INTO @drops
FROM information_schema.KEY_COLUMN_USAGE k
JOIN information_schema.REFERENTIAL_CONSTRAINTS r
  ON r.CONSTRAINT_SCHEMA = k.CONSTRAINT_SCHEMA AND r.TABLE_NAME = k.TABLE_NAME AND r.CONSTRAINT_NAME = k.CONSTRAINT_NAME
WHERE k.TABLE_SCHEMA = DATABASE() AND k.TABLE_NAME = @t AND k.COLUMN_NAME = @c AND k.REFERENCED_TABLE_NAME = @rt
  AND NOT (k.CONSTRAINT_NAME = @name AND r.DELETE_RULE = 'CASCADE');
SET @step = IF(@orphans = 0 AND @drops IS NOT NULL, CONCAT('ALTER TABLE `', @t, '` ', @drops), 'DO 0');
PREPARE step FROM @step; EXECUTE step; DEALLOCATE PREPARE step;

SELECT COUNT(*) INTO @has FROM information_schema.REFERENTIAL_CONSTRAINTS
WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = @t AND CONSTRAINT_NAME = @name AND DELETE_RULE = 'CASCADE';
SET @step = IF(@orphans = 0 AND @has = 0,
    CONCAT('ALTER TABLE `', @t, '` ADD CONSTRAINT `', @name, '` FOREIGN KEY (`', @c, '`) REFERENCES `', @rt, '` (`id`) ON DELETE CASCADE'),
    'DO 0');
PREPARE step FROM @step; EXECUTE step; DEALLOCATE PREPARE step;

-- ----- transactions.savings_goal_id (savings contributions) -----
SET @t = 'transactions', @c = 'savings_goal_id', @rt = 'savings_goals', @name = 'fk_transactions_goal';

SELECT GROUP_CONCAT(CONCAT('DROP FOREIGN KEY `', k.CONSTRAINT_NAME, '`') SEPARATOR ', ') INTO @drops
FROM information_schema.KEY_COLUMN_USAGE k
JOIN information_schema.REFERENTIAL_CONSTRAINTS r
  ON r.CONSTRAINT_SCHEMA = k.CONSTRAINT_SCHEMA AND r.TABLE_NAME = k.TABLE_NAME AND r.CONSTRAINT_NAME = k.CONSTRAINT_NAME
WHERE k.TABLE_SCHEMA = DATABASE() AND k.TABLE_NAME = @t AND k.COLUMN_NAME = @c AND k.REFERENCED_TABLE_NAME = @rt
  AND NOT (k.CONSTRAINT_NAME = @name AND r.DELETE_RULE = 'CASCADE');
SET @step = IF(@orphans = 0 AND @drops IS NOT NULL, CONCAT('ALTER TABLE `', @t, '` ', @drops), 'DO 0');
PREPARE step FROM @step; EXECUTE step; DEALLOCATE PREPARE step;

SELECT COUNT(*) INTO @has FROM information_schema.REFERENTIAL_CONSTRAINTS
WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = @t AND CONSTRAINT_NAME = @name AND DELETE_RULE = 'CASCADE';
SET @step = IF(@orphans = 0 AND @has = 0,
    CONCAT('ALTER TABLE `', @t, '` ADD CONSTRAINT `', @name, '` FOREIGN KEY (`', @c, '`) REFERENCES `', @rt, '` (`id`) ON DELETE CASCADE'),
    'DO 0');
PREPARE step FROM @step; EXECUTE step; DEALLOCATE PREPARE step;

-- ---------------------------------------------------------------------------
-- 3. Check the result
-- ---------------------------------------------------------------------------
-- Expect exactly 4 rows, all with delete_rule = CASCADE, and no other foreign key
-- on these four columns.
SELECT k.TABLE_NAME AS table_name, k.COLUMN_NAME AS column_name, k.CONSTRAINT_NAME AS constraint_name,
       k.REFERENCED_TABLE_NAME AS references_table, r.DELETE_RULE AS delete_rule
FROM information_schema.KEY_COLUMN_USAGE k
JOIN information_schema.REFERENTIAL_CONSTRAINTS r
  ON r.CONSTRAINT_SCHEMA = k.CONSTRAINT_SCHEMA AND r.TABLE_NAME = k.TABLE_NAME AND r.CONSTRAINT_NAME = k.CONSTRAINT_NAME
WHERE k.TABLE_SCHEMA = DATABASE()
  AND ((k.TABLE_NAME IN ('expenses', 'notifications', 'savings_goals') AND k.COLUMN_NAME = 'trip_id')
    OR (k.TABLE_NAME = 'transactions' AND k.COLUMN_NAME = 'savings_goal_id'))
ORDER BY k.TABLE_NAME;
