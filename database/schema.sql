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
    created_at DATETIME(6) NOT NULL
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