package com.tripbudgetplanner.backend.itinerary.options;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

import jakarta.persistence.*;

// One activity inside a draft option (table: itinerary_option_activities).
// Same fields as an itinerary activity; copied over when the option is applied.
@Entity
@Table(name = "itinerary_option_activities")
public class ItineraryOptionActivity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "option_id", nullable = false)
    private ItineraryOption option;

    @Column(name = "activity_date", nullable = false)
    private LocalDate activityDate;

    @Column(nullable = false)
    private String title;

    @Column(name = "start_time")
    private LocalTime startTime;

    @Column(name = "end_time")
    private LocalTime endTime;

    @Column(nullable = false, length = 100)
    private String category;

    private String location;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "estimated_cost", precision = 12, scale = 2)
    private BigDecimal estimatedCost;

    @Column(name = "cost_currency", length = 3, columnDefinition = "CHAR(3)")
    private String costCurrency;

    public Long getId() { return id; }

    public ItineraryOption getOption() { return option; }
    void setOption(ItineraryOption option) { this.option = option; }

    public LocalDate getActivityDate() { return activityDate; }
    public void setActivityDate(LocalDate activityDate) { this.activityDate = activityDate; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public LocalTime getStartTime() { return startTime; }
    public void setStartTime(LocalTime startTime) { this.startTime = startTime; }

    public LocalTime getEndTime() { return endTime; }
    public void setEndTime(LocalTime endTime) { this.endTime = endTime; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public BigDecimal getEstimatedCost() { return estimatedCost; }
    public String getCostCurrency() { return costCurrency; }

    public void setEstimatedCost(BigDecimal estimatedCost, String costCurrency) {
        this.estimatedCost = estimatedCost;
        this.costCurrency = costCurrency;
    }
}
