
package com.tripbudgetplanner.backend.itinerary;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

import com.tripbudgetplanner.backend.trip.Trip;

import jakarta.persistence.*;

// Represents one scheduled activity in a trip itinerary.
@Entity
@Table(name = "itinerary_activities")
public class ItineraryActivity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Links this activity to an existing trip.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    @Column(name = "activity_date", nullable = false)
    private LocalDate activityDate;

    @Column(nullable = false)
    private String title;

    @Column(name = "start_time")
    private LocalTime startTime;

    @Column(name = "end_time")
    private LocalTime endTime;

    @Column(nullable = false, length = 100)
    private String category = "Activity";

    private String location;

    @Column(columnDefinition = "TEXT")
    private String notes;

    // ---------- UC6: costs and search results (all optional) ----------

    // Estimated cost for the whole group, in costCurrency (the trip's currency when saved).
    // Null means unknown. Manual and AI activities usually have none.
    @Column(name = "estimated_cost", precision = 12, scale = 2)
    private BigDecimal estimatedCost;

    @Column(name = "cost_currency", length = 3, columnDefinition = "CHAR(3)")
    private String costCurrency;

    // Where the activity came from: FLIGHT, HOTEL, RESTAURANT or ACTIVITY search result.
    // Null for manual and AI activities.
    @Column(name = "source_type", length = 20)
    private String sourceType;

    // The search provider, e.g. "demo"
    @Column(name = "source_provider", length = 30)
    private String sourceProvider;

    // The provider's ID for the result
    @Column(name = "source_item_id", length = 120)
    private String sourceItemId;

    // MAIN for most results; OUTBOUND or RETURN for the two legs of a round-trip flight
    @Column(name = "source_segment", length = 10)
    private String sourceSegment;

    // What updateBudget=true added to the budget, and in which currency (the budget's)
    @Column(name = "budget_amount_added", precision = 12, scale = 2)
    private BigDecimal budgetAmountAdded;

    @Column(name = "budget_amount_currency", length = 3, columnDefinition = "CHAR(3)")
    private String budgetAmountCurrency;

    // Required by JPA.
    public ItineraryActivity() {}

    public Long getId() {
        return id;
    }

    public Trip getTrip() {
        return trip;
    }

    public void setTrip(Trip trip) {
        this.trip = trip;
    }

    public LocalDate getActivityDate() {
        return activityDate;
    }

    public void setActivityDate(LocalDate activityDate) {
        this.activityDate = activityDate;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalTime startTime) {
        this.startTime = startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalTime endTime) {
        this.endTime = endTime;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public BigDecimal getEstimatedCost() { return estimatedCost; }
    public String getCostCurrency() { return costCurrency; }

    // Cost and currency always go together
    public void setEstimatedCost(BigDecimal estimatedCost, String costCurrency) {
        this.estimatedCost = estimatedCost;
        this.costCurrency = estimatedCost == null ? null : costCurrency;
    }

    public String getSourceType() { return sourceType; }
    public String getSourceProvider() { return sourceProvider; }
    public String getSourceItemId() { return sourceItemId; }
    public String getSourceSegment() { return sourceSegment; }

    public void setSource(String sourceType, String sourceProvider, String sourceItemId, String sourceSegment) {
        this.sourceType = sourceType;
        this.sourceProvider = sourceProvider;
        this.sourceItemId = sourceItemId;
        this.sourceSegment = sourceSegment;
    }

    public BigDecimal getBudgetAmountAdded() { return budgetAmountAdded; }
    public String getBudgetAmountCurrency() { return budgetAmountCurrency; }

    public void setBudgetAmountAdded(BigDecimal amount, String currency) {
        this.budgetAmountAdded = amount;
        this.budgetAmountCurrency = amount == null ? null : currency;
    }
}
