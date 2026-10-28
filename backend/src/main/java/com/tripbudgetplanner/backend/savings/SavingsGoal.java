package com.tripbudgetplanner.backend.savings;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;

// One savings goal per trip (table: savings_goals)
@Entity
@Table(name = "savings_goals")
public class SavingsGoal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trip_id", nullable = false, unique = true)
    private Long tripId;

    @Column(name = "target_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal targetAmount;

    // Money already saved before the goal was created
    @Column(name = "initial_saved_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal initialSavedAmount = BigDecimal.ZERO;

    @Column(name = "target_date", nullable = false)
    private LocalDate targetDate;

    // Optional: what the user plans to put aside each month
    @Column(name = "planned_monthly_contribution", precision = 12, scale = 2)
    private BigDecimal plannedMonthlyContribution;

    protected SavingsGoal() {}

    public SavingsGoal(Long tripId) {
        this.tripId = tripId;
    }

    public Long getId() { return id; }
    public Long getTripId() { return tripId; }

    public BigDecimal getTargetAmount() { return targetAmount; }
    public void setTargetAmount(BigDecimal targetAmount) { this.targetAmount = targetAmount; }

    public BigDecimal getInitialSavedAmount() { return initialSavedAmount; }
    public void setInitialSavedAmount(BigDecimal initialSavedAmount) { this.initialSavedAmount = initialSavedAmount; }

    public LocalDate getTargetDate() { return targetDate; }
    public void setTargetDate(LocalDate targetDate) { this.targetDate = targetDate; }

    public BigDecimal getPlannedMonthlyContribution() { return plannedMonthlyContribution; }
    public void setPlannedMonthlyContribution(BigDecimal plannedMonthlyContribution) {
        this.plannedMonthlyContribution = plannedMonthlyContribution;
    }
}
