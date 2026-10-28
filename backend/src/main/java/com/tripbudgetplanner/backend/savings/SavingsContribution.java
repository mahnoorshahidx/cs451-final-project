package com.tripbudgetplanner.backend.savings;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;

// A simulated deposit toward a trip's savings goal (table: transactions).
// Not a real bank transfer. bankAccountId is optional.
@Entity
@Table(name = "transactions")
public class SavingsContribution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "savings_goal_id", nullable = false)
    private Long savingsGoalId;

    @Column(name = "bank_account_id")
    private Long bankAccountId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "transaction_date", nullable = false)
    private LocalDate transactionDate;

    @Column(length = 255)
    private String description;

    protected SavingsContribution() {}

    public SavingsContribution(Long savingsGoalId, Long bankAccountId, BigDecimal amount, LocalDate transactionDate, String description) {
        this.savingsGoalId = savingsGoalId;
        this.bankAccountId = bankAccountId;
        this.amount = amount;
        this.transactionDate = transactionDate;
        this.description = description;
    }

    public Long getId() { return id; }
    public Long getSavingsGoalId() { return savingsGoalId; }
    public Long getBankAccountId() { return bankAccountId; }
    public BigDecimal getAmount() { return amount; }
    public LocalDate getTransactionDate() { return transactionDate; }
    public String getDescription() { return description; }
}
