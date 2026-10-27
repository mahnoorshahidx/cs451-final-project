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
    trip_reminders_enabled BOOLEAN NOT NULL DEFAULT TRUE
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
    CONSTRAINT fk_savings_goals_trip FOREIGN KEY (trip_id) REFERENCES trips (id)
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
    CONSTRAINT fk_expenses_trip FOREIGN KEY (trip_id) REFERENCES trips (id)
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
    CONSTRAINT chk_transaction_amount CHECK (amount > 0),
    CONSTRAINT fk_transactions_goal FOREIGN KEY (savings_goal_id) REFERENCES savings_goals (id),
    CONSTRAINT fk_transactions_account FOREIGN KEY (bank_account_id) REFERENCES bank_accounts (id)
);

-- Trip budget (UC4): one budget per trip, plus its per-category amounts.
-- ON DELETE CASCADE: deleting a trip deletes its budget.

CREATE TABLE IF NOT EXISTS trip_budgets (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    trip_id BIGINT NOT NULL UNIQUE,
    total_amount DECIMAL(12, 2) NOT NULL,
    currency CHAR(3) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_trip_budgets_trip
        FOREIGN KEY (trip_id) REFERENCES trips(id) ON DELETE CASCADE
);

<<<<<<< Updated upstream
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
    CONSTRAINT fk_notifications_trip
        FOREIGN KEY (trip_id) REFERENCES trips(id)
);
=======
    CREATE TABLE IF NOT EXISTS trip_budget_allocations (
        budget_id BIGINT NOT NULL,
        category VARCHAR(50) NOT NULL,
        amount DECIMAL(12, 2) NOT NULL,
        PRIMARY KEY (budget_id, category),
        CONSTRAINT fk_allocations_budget
            FOREIGN KEY (budget_id) REFERENCES trip_budgets(id) ON DELETE CASCADE
    );
>>>>>>> Stashed changes
