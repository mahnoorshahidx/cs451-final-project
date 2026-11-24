-- Trip Budget Planner: UC9 shared trips and invitations
--
-- Run this in MySQL Workbench BEFORE starting the updated backend.
-- Needs MySQL 8.0.16 or newer (for CHECK constraints). Check with: SELECT VERSION();
--
-- What it does:
--   1. Adds transactions.created_by_user_id and fills it in for existing contributions
--   2. Creates trip_members
--   3. Creates trip_invitations
-- Existing trips, budgets, expenses and savings are not changed (only the new column is filled in).
-- Owners keep full access: the owner is still trips.user_id.

USE trip_budget_planner;

-- ---------------------------------------------------------------------------
-- 0. Check first
-- ---------------------------------------------------------------------------
-- SHOW TABLES LIKE 'trip_members';
-- SHOW TABLES LIKE 'trip_invitations';
-- SHOW COLUMNS FROM transactions LIKE 'created_by_user_id';
--
-- If the new backend was already started before this script, Hibernate may have
-- created trip_members / trip_invitations WITHOUT the cascades, checks and unique keys.
-- If those tables exist and are empty (SELECT COUNT(*) FROM trip_members; ...), drop them
-- and run this script again:
--   DROP TABLE trip_invitations;
--   DROP TABLE trip_members;
-- If transactions.created_by_user_id already exists, skip step 1a and run 1b to 1c.

-- ---------------------------------------------------------------------------
-- 1. Who added each savings contribution
-- ---------------------------------------------------------------------------

-- 1a. New column (NULL is allowed, so existing rows stay valid)
ALTER TABLE transactions ADD COLUMN created_by_user_id BIGINT NULL;

-- 1b. Foreign key to users
ALTER TABLE transactions
    ADD CONSTRAINT fk_transactions_created_by
    FOREIGN KEY (created_by_user_id) REFERENCES users (id) ON DELETE SET NULL;

-- 1c. Existing contributions were all added by the trip owner (sharing didn't exist yet).
-- Workbench's "safe updates" mode blocks UPDATEs like this one, so turn it off just for this.
SET SQL_SAFE_UPDATES = 0;
UPDATE transactions tx
    JOIN savings_goals sg ON sg.id = tx.savings_goal_id
    JOIN trips t ON t.id = sg.trip_id
SET tx.created_by_user_id = t.user_id
WHERE tx.created_by_user_id IS NULL;
SET SQL_SAFE_UPDATES = 1;

-- ---------------------------------------------------------------------------
-- 2. Trip members (EDITOR or VIEWER; the owner is never stored here)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS trip_members (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    trip_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role VARCHAR(10) NOT NULL,
    joined_at DATETIME(6) NOT NULL,
    invited_by_user_id BIGINT NULL,
    CONSTRAINT uq_trip_members_trip_user UNIQUE (trip_id, user_id),
    CONSTRAINT chk_trip_members_role CHECK (role IN ('EDITOR', 'VIEWER')),
    CONSTRAINT fk_trip_members_trip FOREIGN KEY (trip_id) REFERENCES trips (id) ON DELETE CASCADE,
    CONSTRAINT fk_trip_members_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_trip_members_inviter FOREIGN KEY (invited_by_user_id) REFERENCES users (id) ON DELETE SET NULL,
    INDEX idx_trip_members_user (user_id)
);

-- ---------------------------------------------------------------------------
-- 3. Trip invitations (invited_email NULL = single-use shareable link)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS trip_invitations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    trip_id BIGINT NOT NULL,
    token_hash CHAR(64) NOT NULL,
    invited_email VARCHAR(255) NULL,
    role VARCHAR(10) NOT NULL,
    status VARCHAR(10) NOT NULL DEFAULT 'PENDING',
    invited_by_user_id BIGINT NOT NULL,
    accepted_by_user_id BIGINT NULL,
    created_at DATETIME(6) NOT NULL,
    last_sent_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    responded_at DATETIME(6) NULL,
    -- Only filled while PENDING: one active invitation per email per trip
    pending_email VARCHAR(255) GENERATED ALWAYS AS (IF(status = 'PENDING', invited_email, NULL)) STORED,
    CONSTRAINT uq_trip_invitations_token UNIQUE (token_hash),
    CONSTRAINT uq_trip_invitations_pending_email UNIQUE (trip_id, pending_email),
    CONSTRAINT chk_trip_invitations_role CHECK (role IN ('EDITOR', 'VIEWER')),
    CONSTRAINT chk_trip_invitations_status CHECK (status IN ('PENDING', 'ACCEPTED', 'DECLINED', 'REVOKED', 'EXPIRED')),
    CONSTRAINT fk_trip_invitations_trip FOREIGN KEY (trip_id) REFERENCES trips (id) ON DELETE CASCADE,
    CONSTRAINT fk_trip_invitations_inviter FOREIGN KEY (invited_by_user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_trip_invitations_accepter FOREIGN KEY (accepted_by_user_id) REFERENCES users (id) ON DELETE SET NULL,
    INDEX idx_trip_invitations_email_status (invited_email, status),
    INDEX idx_trip_invitations_trip_status (trip_id, status)
);

-- ---------------------------------------------------------------------------
-- 4. Verify
-- ---------------------------------------------------------------------------
-- Every contribution should now have a creator (expect 0):
--   SELECT COUNT(*) FROM transactions WHERE created_by_user_id IS NULL;
-- Foreign keys and checks:
--   SHOW CREATE TABLE trip_members;
--   SHOW CREATE TABLE trip_invitations;
--   SHOW CREATE TABLE transactions;
