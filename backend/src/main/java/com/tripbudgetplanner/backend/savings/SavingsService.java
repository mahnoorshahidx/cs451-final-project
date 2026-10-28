package com.tripbudgetplanner.backend.savings;

import com.tripbudgetplanner.backend.bank.BankAccountRepository;
import com.tripbudgetplanner.backend.budget.TripBudget;
import com.tripbudgetplanner.backend.budget.TripBudgetRepository;
import com.tripbudgetplanner.backend.budget.TripBudgetService;
import com.tripbudgetplanner.backend.savings.dto.BudgetOverviewItem;
import com.tripbudgetplanner.backend.savings.dto.ContributionRequest;
import com.tripbudgetplanner.backend.savings.dto.SavingsGoalRequest;
import com.tripbudgetplanner.backend.savings.dto.SavingsResponse;
import com.tripbudgetplanner.backend.trip.Trip;
import com.tripbudgetplanner.backend.trip.TripService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

// Trip savings goals (UC6/UC7): set a goal, log contributions, track progress
@Service
public class SavingsService {

    private final SavingsGoalRepository goalRepository;
    private final SavingsContributionRepository contributionRepository;
    private final TripService tripService;
    private final TripBudgetRepository budgetRepository;
    private final BankAccountRepository bankAccountRepository;

    public SavingsService(SavingsGoalRepository goalRepository,
                          SavingsContributionRepository contributionRepository,
                          TripService tripService,
                          TripBudgetRepository budgetRepository,
                          BankAccountRepository bankAccountRepository) {
        this.goalRepository = goalRepository;
        this.contributionRepository = contributionRepository;
        this.tripService = tripService;
        this.budgetRepository = budgetRepository;
        this.bankAccountRepository = bankAccountRepository;
    }

    // ---------- Goal ----------

    // Empty if the trip has no goal yet. Throws 404 if the trip isn't this user's.
    @Transactional(readOnly = true)
    public Optional<SavingsResponse> getSavings(Long tripId, Long userId) {
        tripService.getTrip(tripId, userId);
        return goalRepository.findByTripId(tripId).map(this::toResponse);
    }

    // Creates the goal, or updates it if it exists
    @Transactional
    public SavingsResponse saveGoal(Long tripId, SavingsGoalRequest request, Long userId) {
        tripService.getTrip(tripId, userId);

        SavingsGoal goal = goalRepository.findByTripId(tripId)
            .orElseGet(() -> new SavingsGoal(tripId));

        goal.setTargetAmount(request.targetAmount());
        goal.setInitialSavedAmount(request.initialSavedAmount() == null ? BigDecimal.ZERO : request.initialSavedAmount());
        goal.setTargetDate(request.targetDate());
        goal.setPlannedMonthlyContribution(request.plannedMonthlyContribution());

        return toResponse(goalRepository.save(goal));
    }

    // ---------- Contributions ----------

    @Transactional
    public SavingsResponse addContribution(Long tripId, ContributionRequest request, Long userId) {
        tripService.getTrip(tripId, userId);
        SavingsGoal goal = goalRepository.findByTripId(tripId)
            .orElseThrow(() -> new IllegalArgumentException("Set a savings goal for this trip first"));

        // A linked bank account must belong to the same user
        if (request.bankAccountId() != null
                && bankAccountRepository.findByIdAndUserId(request.bankAccountId(), userId).isEmpty()) {
            throw new IllegalArgumentException("Bank account not found");
        }

        String description = request.description() == null || request.description().isBlank()
            ? null
            : request.description().trim();

        contributionRepository.save(new SavingsContribution(
            goal.getId(),
            request.bankAccountId(),
            request.amount(),
            request.date() == null ? LocalDate.now() : request.date(),
            description));

        return toResponse(goal);
    }

    @Transactional
    public SavingsResponse deleteContribution(Long tripId, Long contributionId, Long userId) {
        tripService.getTrip(tripId, userId);
        SavingsGoal goal = goalRepository.findByTripId(tripId)
            .orElseThrow(() -> new IllegalArgumentException("This trip has no savings goal"));

        SavingsContribution contribution = contributionRepository.findByIdAndSavingsGoalId(contributionId, goal.getId())
            .orElseThrow(() -> new IllegalArgumentException("Contribution not found"));
        contributionRepository.delete(contribution);

        return toResponse(goal);
    }

    // ---------- Budget page overview: every trip with its budget and savings ----------

    @Transactional(readOnly = true)
    public List<BudgetOverviewItem> overview(Long userId) {
        List<Trip> trips = tripService.listTrips(userId);
        Map<Long, SavingsGoal> goals = goalRepository
            .findByTripIdIn(trips.stream().map(Trip::getId).toList())
            .stream()
            .collect(Collectors.toMap(SavingsGoal::getTripId, Function.identity()));

        return trips.stream().map(trip -> {
            TripBudget budget = budgetRepository.findByTripId(trip.getId()).orElse(null);
            SavingsGoal goal = goals.get(trip.getId());
            Progress p = goal == null ? null : progress(goal);

            return new BudgetOverviewItem(
                trip.getId(),
                trip.getName(),
                trip.getDestination(),
                trip.getStartDate(),
                trip.getEndDate(),
                trip.getNumberOfTravelers(),
                budget == null ? null : budget.getTotalAmount(),
                budget == null ? null : budget.getCurrency(),
                budget == null ? null : TripBudgetService.totalAllocated(budget),
                goal == null ? null : goal.getTargetAmount(),
                p == null ? null : p.saved(),
                p == null ? null : p.percent(),
                goal == null ? null : goal.getTargetDate(),
                p == null ? null : p.status());
        }).toList();
    }

    // ---------- Progress math ----------

    private record Progress(BigDecimal contributed, BigDecimal saved, BigDecimal remaining, int percent,
                            BigDecimal requiredMonthly, BigDecimal requiredWeekly, String status) {}

    private Progress progress(SavingsGoal goal) {
        BigDecimal contributed = contributionRepository.sumByGoalId(goal.getId());
        BigDecimal saved = goal.getInitialSavedAmount().add(contributed);
        BigDecimal target = goal.getTargetAmount();
        BigDecimal remaining = target.subtract(saved).max(BigDecimal.ZERO);

        int percent = target.signum() == 0
            ? 100
            : saved.multiply(BigDecimal.valueOf(100)).divide(target, 0, RoundingMode.DOWN).min(BigDecimal.valueOf(100)).intValue();

        LocalDate today = LocalDate.now();
        long daysLeft = ChronoUnit.DAYS.between(today, goal.getTargetDate());
        long weeksLeft = Math.max(1, (long) Math.ceil(daysLeft / 7.0));
        long monthsLeft = Math.max(1, (long) Math.ceil(daysLeft / 30.44));

        BigDecimal requiredMonthly = remaining.divide(BigDecimal.valueOf(monthsLeft), 2, RoundingMode.CEILING);
        BigDecimal requiredWeekly = remaining.divide(BigDecimal.valueOf(weeksLeft), 2, RoundingMode.CEILING);

        String status;
        BigDecimal planned = goal.getPlannedMonthlyContribution();
        if (remaining.signum() == 0) {
            status = "FUNDED";
        } else if (daysLeft < 0) {
            status = "PAST_DUE";
        } else if (planned == null || planned.signum() == 0) {
            status = "NO_PLAN";
        } else if (planned.compareTo(requiredMonthly) >= 0) {
            status = "ON_TRACK";
        } else {
            status = "BEHIND";
        }

        return new Progress(contributed, saved, remaining, percent, requiredMonthly, requiredWeekly, status);
    }

    private SavingsResponse toResponse(SavingsGoal goal) {
        Progress p = progress(goal);
        List<SavingsResponse.Contribution> history = contributionRepository
            .findBySavingsGoalIdOrderByTransactionDateDescIdDesc(goal.getId())
            .stream()
            .map(c -> new SavingsResponse.Contribution(c.getId(), c.getAmount(), c.getTransactionDate(), c.getDescription(), c.getBankAccountId()))
            .toList();

        return new SavingsResponse(
            goal.getTripId(),
            goal.getTargetAmount(),
            goal.getInitialSavedAmount(),
            p.contributed(),
            p.saved(),
            p.remaining(),
            p.percent(),
            goal.getTargetDate(),
            goal.getPlannedMonthlyContribution(),
            p.requiredMonthly(),
            p.requiredWeekly(),
            p.status(),
            history);
    }
}
