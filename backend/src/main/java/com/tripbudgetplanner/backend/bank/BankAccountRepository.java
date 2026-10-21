package com.tripbudgetplanner.backend.bank;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BankAccountRepository extends JpaRepository<BankAccount, Long> {
    List<BankAccount> findByUserId(Long userId);
    boolean existsByUserIdAndAccountFingerprint(Long userId, String accountFingerprint);
    Optional<BankAccount> findByIdAndUserId(Long id, Long userId);
}