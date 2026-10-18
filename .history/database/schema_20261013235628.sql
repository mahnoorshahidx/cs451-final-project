-- Trip Budget Planner
-- Creates the initial development database.
-- Table definitions will be added by the database team.


CREATE DATABASE IF NOT EXISTS trip_budget_planner;

USE trip_budget_planner;

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