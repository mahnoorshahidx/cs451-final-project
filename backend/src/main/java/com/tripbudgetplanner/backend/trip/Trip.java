
package com.tripbudgetplanner.backend.trip;

import java.time.LocalDate;

import com.tripbudgetplanner.backend.user.User;

import jakarta.persistence.*;

// Represents a trip created by a registered user
@Entity
@Table(name = "trips")
public class Trip {

    // Unique ID for each trip
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Basic trip information
    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String destination;

    // Departure and return dates
    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    // Number of people traveling
    @Column(nullable = false)
    private int numberOfTravelers;

    // The trip's currency (one of the 8 supported codes, e.g. "USD").
    // The column default fills in "USD" for trips created before this column existed.
    @Column(nullable = false, length = 3, columnDefinition = "CHAR(3) DEFAULT 'USD'")
    private String currency = "USD";

    // Links each trip to the user who created it
    // One user can create multiple trips
    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // Required by JPA
    public Trip() {}

    // Getters and setters for trip information
    public Long getId() { return id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDestination() { return destination; }
    public void setDestination(String destination) { this.destination = destination; }

    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }

    public int getNumberOfTravelers() { return numberOfTravelers; }
    public void setNumberOfTravelers(int numberOfTravelers) {
        this.numberOfTravelers = numberOfTravelers;
    }

    // Old rows with an empty value are treated as USD
    public String getCurrency() {
        return currency == null || currency.isBlank() ? "USD" : currency;
    }
    public void setCurrency(String currency) { this.currency = currency; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
}