-- Trip Budget Planner: UC5 compare AI trip options
--
-- Adds draft itinerary options: the AI makes a budget, balanced and luxury plan from the same
-- preferences; the user compares them and applies one, which copies its activities into
-- itinerary_activities and deletes the drafts. Nothing here shows in the itinerary itself.
--
-- Safe to run more than once. Hibernate (ddl-auto=update) would also create these tables.

CREATE TABLE IF NOT EXISTS itinerary_options (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    trip_id BIGINT NOT NULL,
    style VARCHAR(20) NOT NULL,
    label VARCHAR(60) NOT NULL,
    summary VARCHAR(300) NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    total_cost DECIMAL(12, 2) NULL,
    currency CHAR(3) NULL,
    created_by_user_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_itinerary_options_trip FOREIGN KEY (trip_id) REFERENCES trips (id) ON DELETE CASCADE,
    INDEX idx_itinerary_options_trip (trip_id)
);

CREATE TABLE IF NOT EXISTS itinerary_option_activities (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    option_id BIGINT NOT NULL,
    activity_date DATE NOT NULL,
    title VARCHAR(255) NOT NULL,
    start_time TIME NULL,
    end_time TIME NULL,
    category VARCHAR(100) NOT NULL,
    location VARCHAR(255) NULL,
    notes TEXT NULL,
    estimated_cost DECIMAL(12, 2) NULL,
    cost_currency CHAR(3) NULL,
    CONSTRAINT fk_itinerary_option_activities_option FOREIGN KEY (option_id) REFERENCES itinerary_options (id) ON DELETE CASCADE,
    INDEX idx_itinerary_option_activities_option (option_id)
);
