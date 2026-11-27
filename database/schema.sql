-- Trip Budget Planner
-- Creates the initial development database.
-- Table definitions will be added by the database team.


CREATE DATABASE IF NOT EXISTS trip_budget_planner;

-- Selects the database where the tables will be created.
USE trip_budget_planner;

-- Creates the users table based on the backend's User.java file.
-- Stores user information needed for registration and login.
CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    phone_number VARCHAR(255),
    date_of_birth DATE,
    address VARCHAR(255),
    created_at DATETIME(6) NOT NULL,
    trip_reminders_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    currency VARCHAR(255) NOT NULL DEFAULT 'USD',
    date_format VARCHAR(255) NOT NULL DEFAULT 'MM/DD/YYYY',
    appearance VARCHAR(255) NOT NULL DEFAULT 'light'
);

-- Creates the trips table based on the backend's Trip.java file.
-- Each trip belongs to one user.
CREATE TABLE IF NOT EXISTS trips (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    destination VARCHAR(255) NOT NULL,
    end_date DATE NOT NULL,
    name VARCHAR(255) NOT NULL,
    number_of_travelers INT NOT NULL,
    start_date DATE NOT NULL,
    -- One of the 8 supported currencies: USD, EUR, GBP, JPY, CAD, AUD, INR, KRW
    currency CHAR(3) NOT NULL DEFAULT 'USD',
    user_id BIGINT NOT NULL,
    CONSTRAINT fk_trips_user FOREIGN KEY (user_id) REFERENCES users (id)
);

-- Creates the bank_accounts table based on the backend's BankAccount.java file.
-- The full account number is never stored: only the last 4 digits and a keyed hash used to detect duplicates.
-- The backend generates the keyed fingerprint when an account is connected.
CREATE TABLE IF NOT EXISTS bank_accounts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    account_type VARCHAR(20) NOT NULL,
    routing_number VARCHAR(9) NOT NULL,
    account_last4 VARCHAR(4) NOT NULL,
    account_fingerprint VARCHAR(64) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_bank_accounts_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT uq_bank_accounts_user_fingerprint UNIQUE (user_id, account_fingerprint)
);

CREATE TABLE IF NOT EXISTS savings_goals (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    trip_id BIGINT NOT NULL UNIQUE,
    target_amount DECIMAL(12,2) NOT NULL,
    initial_saved_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
    target_date DATE NOT NULL,
    planned_monthly_contribution DECIMAL(12,2),
    CONSTRAINT chk_savings_amounts CHECK (
        target_amount >= 0 AND initial_saved_amount >= 0
        AND (planned_monthly_contribution IS NULL OR planned_monthly_contribution >= 0)
    ),
    -- UC12: deleting a trip deletes its savings goal
    CONSTRAINT fk_savings_goals_trip FOREIGN KEY (trip_id) REFERENCES trips (id) ON DELETE CASCADE
);

-- TODO #1: A trip may have multiple actual expenses.
CREATE TABLE IF NOT EXISTS expenses (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    trip_id BIGINT NOT NULL,
    category VARCHAR(50) NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    expense_date DATE NOT NULL,
    description VARCHAR(255),
    CONSTRAINT chk_expense_amount CHECK (amount > 0),
    -- UC12: deleting a trip deletes its expenses
    CONSTRAINT fk_expenses_trip FOREIGN KEY (trip_id) REFERENCES trips (id) ON DELETE CASCADE
);

-- TODO #1: Simulated savings contributions; not actual bank transfers.
-- bank_account_id is optional. Backend must ensure any linked bank account
-- belongs to the owner of the associated trip/savings goal.
CREATE TABLE IF NOT EXISTS transactions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    savings_goal_id BIGINT NOT NULL,
    bank_account_id BIGINT,
    amount DECIMAL(12,2) NOT NULL,
    transaction_date DATE NOT NULL,
    description VARCHAR(255),
    -- UC9: who added the contribution (editors can delete only their own). NULL = the trip owner.
    created_by_user_id BIGINT NULL,
    CONSTRAINT chk_transaction_amount CHECK (amount > 0),
    -- UC12: deleting a savings goal deletes its contributions (bank accounts are never deleted)
    CONSTRAINT fk_transactions_goal FOREIGN KEY (savings_goal_id) REFERENCES savings_goals (id) ON DELETE CASCADE,
    CONSTRAINT fk_transactions_account FOREIGN KEY (bank_account_id) REFERENCES bank_accounts (id),
    CONSTRAINT fk_transactions_created_by FOREIGN KEY (created_by_user_id) REFERENCES users (id) ON DELETE SET NULL
);

-- Trip budget (UC4): one budget per trip, plus its per-category amounts.
-- ON DELETE CASCADE: deleting a trip deletes its budget.

CREATE TABLE IF NOT EXISTS trip_budgets (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    trip_id BIGINT NOT NULL UNIQUE,
    total_amount DECIMAL(12, 2) NOT NULL,
    currency CHAR(3) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    -- UC6: goes up by one on every change (lets a page detect someone else's newer save)
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_trip_budgets_trip
        FOREIGN KEY (trip_id) REFERENCES trips(id) ON DELETE CASCADE
);

-- Stores the budget allocation for each trip category.
CREATE TABLE IF NOT EXISTS trip_budget_allocations (
    budget_id BIGINT NOT NULL,
    category VARCHAR(50) NOT NULL,
    amount DECIMAL(12, 2) NOT NULL,
    PRIMARY KEY (budget_id, category),
    CONSTRAINT fk_allocations_budget
        FOREIGN KEY (budget_id) REFERENCES trip_budgets(id) ON DELETE CASCADE
);

-- Stores trip reminder notifications for users.
CREATE TABLE IF NOT EXISTS notifications (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    trip_id BIGINT NOT NULL,
    type VARCHAR(255) NOT NULL,
    message VARCHAR(255) NOT NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT uq_notifications_trip_type
        UNIQUE (trip_id, type),
    CONSTRAINT fk_notifications_user
        FOREIGN KEY (user_id) REFERENCES users(id),
    -- UC12: deleting a trip deletes its notifications
    CONSTRAINT fk_notifications_trip
        FOREIGN KEY (trip_id) REFERENCES trips(id) ON DELETE CASCADE
);

-- Stores scheduled activities for each trip.
-- Each activity belongs to an existing trip.
CREATE TABLE IF NOT EXISTS itinerary_activities (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    trip_id BIGINT NOT NULL,
    activity_date DATE NOT NULL,
    title VARCHAR(255) NOT NULL,
    start_time TIME,
    end_time TIME,
    category VARCHAR(100) NOT NULL DEFAULT 'Activity',
    location VARCHAR(255),
    notes TEXT,

    -- UC6: optional estimated cost for the whole group, in cost_currency (the trip's currency)
    estimated_cost DECIMAL(12, 2) NULL,
    cost_currency CHAR(3) NULL,
    -- UC6: set when the activity was added from a search result (NULL for manual and AI activities)
    source_type VARCHAR(20) NULL,
    source_provider VARCHAR(30) NULL,
    source_item_id VARCHAR(120) NULL,
    -- MAIN, or OUTBOUND / RETURN for the two entries of a round-trip flight
    source_segment VARCHAR(10) NULL,
    -- UC6: what updateBudget=true added to the budget, in the budget's currency
    budget_amount_added DECIMAL(12, 2) NULL,
    budget_amount_currency CHAR(3) NULL,

    CONSTRAINT fk_itinerary_activities_trip
        FOREIGN KEY (trip_id) REFERENCES trips(id)
        ON DELETE CASCADE,
    CONSTRAINT chk_itinerary_estimated_cost CHECK (estimated_cost IS NULL OR estimated_cost >= 0),
    CONSTRAINT chk_itinerary_cost_currency CHECK ((estimated_cost IS NULL) = (cost_currency IS NULL)),
    CONSTRAINT chk_itinerary_budget_amount CHECK (budget_amount_added IS NULL OR budget_amount_added >= 0),
    CONSTRAINT chk_itinerary_budget_currency CHECK ((budget_amount_added IS NULL) = (budget_amount_currency IS NULL)),
    CONSTRAINT chk_itinerary_source_type CHECK (source_type IS NULL OR source_type IN ('FLIGHT', 'HOTEL', 'RESTAURANT', 'ACTIVITY')),
    CONSTRAINT chk_itinerary_source_segment CHECK (source_segment IS NULL OR source_segment IN ('MAIN', 'OUTBOUND', 'RETURN')),
    -- Each search result (and flight leg) can be added to a trip only once
    CONSTRAINT uq_itinerary_source_item UNIQUE (trip_id, source_provider, source_item_id, source_segment)
);


-- UC9: people who joined someone else's trip.
-- The trip owner is always trips.user_id and is never stored here,
-- so members can only be EDITOR or VIEWER.
CREATE TABLE IF NOT EXISTS trip_members (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    trip_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role VARCHAR(10) NOT NULL,
    joined_at DATETIME(6) NOT NULL,
    invited_by_user_id BIGINT NULL,
    -- One membership per user per trip
    CONSTRAINT uq_trip_members_trip_user UNIQUE (trip_id, user_id),
    CONSTRAINT chk_trip_members_role CHECK (role IN ('EDITOR', 'VIEWER')),
    CONSTRAINT fk_trip_members_trip FOREIGN KEY (trip_id) REFERENCES trips (id) ON DELETE CASCADE,
    CONSTRAINT fk_trip_members_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_trip_members_inviter FOREIGN KEY (invited_by_user_id) REFERENCES users (id) ON DELETE SET NULL,
    -- Fast lookup of "trips shared with me"
    INDEX idx_trip_members_user (user_id)
);

-- UC9: invitations to join a trip.
-- invited_email NULL = a single-use shareable link.
-- Only a SHA-256 hash of the invitation token is stored.
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
    -- Filled only while PENDING, so the unique key below allows one active
    -- invitation per email per trip (expired or answered ones don't count)
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

-- UC1 two-factor sign-in: a sign-in waiting for its emailed code (only the code's hash is stored)
CREATE TABLE IF NOT EXISTS login_challenges (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    email VARCHAR(255) NOT NULL,
    code_hash VARCHAR(100) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    last_sent_at DATETIME(6) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    send_count INT NOT NULL DEFAULT 1,
    closed_at DATETIME(6) NULL,
    CONSTRAINT fk_login_challenges_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    INDEX idx_login_challenges_user_open (user_id, closed_at)
);
