package com.tripbudgetplanner.backend.savings;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface SavingsContributionRepository extends JpaRepository<SavingsContribution, Long> {

    List<SavingsContribution> findBySavingsGoalIdOrderByTransactionDateDescIdDesc(Long savingsGoalId);

    Optional<SavingsContribution> findByIdAndSavingsGoalId(Long id, Long savingsGoalId);

    @Query("select coalesce(sum(c.amount), 0) from SavingsContribution c where c.savingsGoalId = :goalId")
    BigDecimal sumByGoalId(@Param("goalId") Long goalId);

    void deleteBySavingsGoalId(Long savingsGoalId);
}
