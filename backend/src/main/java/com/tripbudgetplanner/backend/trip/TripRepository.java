
package com.tripbudgetplanner.backend.trip;

import org.springframework.data.jpa.repository.JpaRepository;

// Handles database operations for trips
// JpaRepository provides built-in methods like save(), findById(), and findAll()
public interface TripRepository extends JpaRepository<Trip, Long> {

}