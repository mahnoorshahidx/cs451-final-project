
package com.tripbudgetplanner.backend.trip.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

// Stores the information submitted when a user creates a new trip
public class CreateTripRequest {

    // Trip name and destination are required
    @NotBlank(message = "Trip name is required")
    private String name;

    @NotBlank(message = "Destination is required")
    private String destination;

    // Departure and return dates are required
    @NotNull(message = "Departure date is required")
    private LocalDate startDate;

    @NotNull(message = "Return date is required")
    private LocalDate endDate;

    // At least one traveler must be included
    @NotNull(message = "Number of travelers is required")
    @Min(value = 1, message = "At least one traveler is required")
    private Integer numberOfTravelers;

    // Getters and setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDestination() { return destination; }
    public void setDestination(String destination) { this.destination = destination; }

    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }

    public Integer getNumberOfTravelers() { return numberOfTravelers; }
    public void setNumberOfTravelers(Integer numberOfTravelers) {
        this.numberOfTravelers = numberOfTravelers;
    }
}