package com.tripbudgetplanner.backend.savings;

import com.tripbudgetplanner.backend.bank.BankAccount;
import com.tripbudgetplanner.backend.bank.BankAccountRepository;
import com.tripbudgetplanner.backend.budget.TripBudget;
import com.tripbudgetplanner.backend.budget.TripBudgetRepository;
import com.tripbudgetplanner.backend.budget.TripBudgetService;
import com.tripbudgetplanner.backend.savings.dto.BudgetOverviewItem;
import com.tripbudgetplanner.backend.savings.dto.ContributionRequest;
import com.tripbudgetplanner.backend.savings.dto.SavingsGoalRequest;
import com.tripbudgetplanner.backend.savings.dto.SavingsResponse;
import com.tripbudgetplanner.backend.trip.Trip;
import com.tripbudgetplanner.backend.member.TripRole;
import com.tripbudgetplanner.backend.trip.TripAccessService;
import com.tripbudgetplanner.backend.trip.TripAccessService.TripAccess;
import com.tripbudgetplanner.backend.trip.TripService;
import com.tripbudgetplanner.backend.trip.exception.TripAccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
    private final TripAccessService tripAccess;

    public SavingsService(SavingsGoalRepository goalRepository,
                          SavingsContributionRepository contributionRepository,
                          TripService tripService,
                          TripBudgetRepository budgetRepository,
                          BankAccountRepository bankAccountRepository,
                          TripAccessService tripAccess) {
        this.goalRepository = goalRepository;
        this.contributionRepository = contributionRepository;
        this.tripService = tripService;
        this.budgetRepository = budgetRepository;
        this.bankAccountRepository = bankAccountRepository;
        this.tripAccess = tripAccess;
    }

    // ---------- Goal ----------

    // Empty if the trip has no goal yet. Any trip member can view it; 404 without access.
    @Transactional(readOnly = true)
    public Optional<SavingsResponse> getSavings(Long tripId, Long userId) {
        TripAccess access = tripAccess.requireRole(tripId, userId, TripRole.VIEWER);
        return goalRepository.findByTripId(tripId).map(goal -> toResponse(goal, access, userId));
    }

    // Creates the goal, or updates it if it exists
    @Transactional
    public SavingsResponse saveGoal(Long tripId, SavingsGoalRequest request, Long userId) {
        // Owner or editor (403 for viewers)
        TripAccess access = tripAccess.requireRole(tripId, userId, TripRole.EDITOR);

        SavingsGoal goal = goalRepository.findByTripId(tripId)
            .orElseGet(() -> new SavingsGoal(tripId));

        goal.setTargetAmount(request.targetAmount());
        goal.setInitialSavedAmount(request.initialSavedAmount() == null ? BigDecimal.ZERO : request.initialSavedAmount());
        goal.setTargetDate(request.targetDate());
        goal.setPlannedMonthlyContribution(request.plannedMonthlyContribution());

        return toResponse(goalRepository.save(goal), access, userId);
    }

    // ---------- Contributions ----------

    @Transactional
    public SavingsResponse addContribution(Long tripId, ContributionRequest request, Long userId) {
        // Owner or editor (403 for viewers)
        TripAccess access = tripAccess.requireRole(tripId, userId, TripRole.EDITOR);
        SavingsGoal goal = goalRepository.findByTripId(tripId)
            .orElseThrow(() -> new IllegalArgumentException("Set a savings goal for this trip first"));

        // A linked bank account must belong to the user adding the contribution,
        // so members can never use (or see) someone else's bank account
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
            description,
            userId));

        return toResponse(goal, access, userId);
    }

    @Transactional
    public SavingsResponse deleteContribution(Long tripId, Long contributionId, Long userId) {
        // Owner or editor (403 for viewers)
        TripAccess access = tripAccess.requireRole(tripId, userId, TripRole.EDITOR);
        SavingsGoal goal = goalRepository.findByTripId(tripId)
            .orElseThrow(() -> new IllegalArgumentException("This trip has no savings goal"));

        SavingsContribution contribution = contributionRepository.findByIdAndSavingsGoalId(contributionId, goal.getId())
            .orElseThrow(() -> new IllegalArgumentException("Contribution not found"));

        // Editors can only delete contributions they added; the owner can delete any
        if (!canDelete(contribution, access, userId)) {
            throw new TripAccessDeniedException("You can only delete contributions you added.");
        }
        contributionRepository.delete(contribution);

        return toResponse(goal, access, userId);
    }

    // ---------- Who added a contribution (UC9) ----------

    // Contributions saved before sharing existed have no creator; only the owner could add them then
    private static Long creatorOf(SavingsContribution contribution, Trip trip) {
        return contribution.getCreatedByUserId() != null
            ? contribution.getCreatedByUserId()
            : trip.getUser().getId();
    }

    private static boolean canDelete(SavingsContribution contribution, TripAccess access, Long userId) {
        if (access.role() == TripRole.OWNER) {
            return true;
        }
        return access.role() == TripRole.EDITOR && userId.equals(creatorOf(contribution, access.trip()));
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

    // Builds the response for the user who asked.
    // A contribution's bank account ID is only shown if it is one of this user's own accounts,
    // so members never see each other's banking details.
    private SavingsResponse toResponse(SavingsGoal goal, TripAccess access, Long userId) {
        Progress p = progress(goal);
        Set<Long> myAccountIds = bankAccountRepository.findByUserId(userId).stream()
            .map(BankAccount::getId)
            .collect(Collectors.toSet());

        List<SavingsResponse.Contribution> history = contributionRepository
            .findBySavingsGoalIdOrderByTransactionDateDescIdDesc(goal.getId())
            .stream()
            .map(c -> new SavingsResponse.Contribution(
                c.getId(),
                c.getAmount(),
                c.getTransactionDate(),
                c.getDescription(),
                myAccountIds.contains(c.getBankAccountId()) ? c.getBankAccountId() : null,
                userId.equals(creatorOf(c, access.trip())),
                canDelete(c, access, userId)))
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
