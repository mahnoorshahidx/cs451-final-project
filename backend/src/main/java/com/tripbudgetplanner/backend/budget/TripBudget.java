package com.tripbudgetplanner.backend.budget;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

// One budget per trip, with an amount per category (Flights, Food, ...)
@Entity
@Table(name = "trip_budgets")
public class TripBudget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trip_id", nullable = false, unique = true)
    private Long tripId;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(nullable = false, length = 3, columnDefinition = "CHAR(3)")
    private String currency;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // Stored in trip_budget_allocations as (budget_id, category, amount)
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "trip_budget_allocations", joinColumns = @JoinColumn(name = "budget_id"))
    @MapKeyColumn(name = "category", length = 50)
    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private Map<String, BigDecimal> allocations = new LinkedHashMap<>();

    protected TripBudget() {}

    public TripBudget(Long tripId) {
        this.tripId = tripId;
    }

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getTripId() { return tripId; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public Map<String, BigDecimal> getAllocations() { return allocations; }
}
