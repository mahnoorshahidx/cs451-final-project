package com.tripbudgetplanner.backend.itinerary.options;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.tripbudgetplanner.backend.trip.Trip;

import jakarta.persistence.*;

// UC5: one AI-generated itinerary option, kept as a draft until the user picks one
// (table: itinerary_options). Drafts never show in the itinerary; applying one copies its
// activities into itinerary_activities and clears the trip's drafts.
@Entity
@Table(name = "itinerary_options")
public class ItineraryOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    // "budget", "balanced" or "luxury"
    @Column(nullable = false, length = 20)
    private String style;

    @Column(nullable = false, length = 60)
    private String label;

    @Column(length = 300)
    private String summary;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    // Sum of the activities' estimated costs, for the whole group (null when none had a cost)
    @Column(name = "total_cost", precision = 12, scale = 2)
    private BigDecimal totalCost;

    @Column(length = 3, columnDefinition = "CHAR(3)")
    private String currency;

    @Column(name = "created_by_user_id", nullable = false)
    private Long createdByUserId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "option", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("activityDate ASC, startTime ASC")
    private List<ItineraryOptionActivity> activities = new ArrayList<>();

    public void addActivity(ItineraryOptionActivity activity) {
        activity.setOption(this);
        activities.add(activity);
    }

    public Long getId() { return id; }

    public Trip getTrip() { return trip; }
    public void setTrip(Trip trip) { this.trip = trip; }

    public String getStyle() { return style; }
    public void setStyle(String style) { this.style = style; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }

    public BigDecimal getTotalCost() { return totalCost; }
    public void setTotalCost(BigDecimal totalCost) { this.totalCost = totalCost; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public Long getCreatedByUserId() { return createdByUserId; }
    public void setCreatedByUserId(Long createdByUserId) { this.createdByUserId = createdByUserId; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public List<ItineraryOptionActivity> getActivities() { return activities; }
}
