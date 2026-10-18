package com.tripbudgetplanner.backend.bank;

import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.tripbudgetplanner.backend.user.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

// A bank account connected to a user's profile.
// The full account number is never stored.
@Entity
@Table(name = "bank_accounts")
public class BankAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // VARCHAR on purpose: without this, Hibernate may expect a native MySQL enum column
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private AccountType accountType;

    @Column(nullable = false, length = 9)
    private String routingNumber;

    @Column(nullable = false, length = 4)
    private String accountLast4;

    // Keyed hash of routing + account number, used only to detect duplicates
    @Column(nullable = false, length = 64)
    private String accountFingerprint;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public BankAccount() {}

    public Long getId() { return id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public AccountType getAccountType() { return accountType; }
    public void setAccountType(AccountType accountType) { this.accountType = accountType; }

    public String getRoutingNumber() { return routingNumber; }
    public void setRoutingNumber(String routingNumber) { this.routingNumber = routingNumber; }

    public String getAccountLast4() { return accountLast4; }
    public void setAccountLast4(String accountLast4) { this.accountLast4 = accountLast4; }

    public String getAccountFingerprint() { return accountFingerprint; }
    public void setAccountFingerprint(String accountFingerprint) { this.accountFingerprint = accountFingerprint; }

    public Instant getCreatedAt() { return createdAt; }
}