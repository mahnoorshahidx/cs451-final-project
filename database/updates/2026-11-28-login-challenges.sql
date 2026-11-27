-- Trip Budget Planner: UC1 two-factor sign-in
--
-- Adds login_challenges: one row per sign-in that is waiting for its emailed code.
-- Only a BCrypt hash of the code is stored. Rows are closed (closed_at) when the code is used,
-- after too many wrong codes, or when the user starts a newer sign-in.
--
-- Safe to run more than once. Hibernate (ddl-auto=update) would also create this table,
-- but running this keeps the foreign key and indexes the same on every machine.

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
