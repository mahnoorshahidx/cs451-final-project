package com.tripbudgetplanner.backend.savings;

import com.tripbudgetplanner.backend.bank.BankAccount;
import com.tripbudgetplanner.backend.bank.BankAccountRepository;
import com.tripbudgetplanner.backend.bank.exception.BankAccountNotFoundException;
import com.tripbudgetplanner.backend.budget.TripBudget;
import com.tripbudgetplanner.backend.budget.TripBudgetRepository;
import com.tripbudgetplanner.backend.budget.TripBudgetService;
import com.tripbudgetplanner.backend.currency.SupportedCurrency;
import com.tripbudgetplanner.backend.savings.dto.BudgetOverviewItem;
import com.tripbudgetplanner.backend.savings.dto.ContributionHistoryResponse;
import com.tripbudgetplanner.backend.savings.dto.ContributionRequest;
import com.tripbudgetplanner.backend.savings.dto.SavingsGoalRequest;
import com.tripbudgetplanner.backend.savings.dto.SavingsResponse;
import com.tripbudgetplanner.backend.trip.Trip;
import com.tripbudgetplanner.backend.member.TripRole;
import com.tripbudgetplanner.backend.trip.TripAccessService;
import com.tripbudgetplanner.backend.trip.TripAccessService.TripAccess;
import com.tripbudgetplanner.backend.trip.TripService;
import com.tripbudgetplanner.backend.trip.exception.TripAccessDeniedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

    // "Today" comes from this clock (the server's date), so tests can pick the date
    private final Clock clock;

    @Autowired
    public SavingsService(SavingsGoalRepository goalRepository,
                          SavingsContributionRepository contributionRepository,
                          TripService tripService,
                          TripBudgetRepository budgetRepository,
                          BankAccountRepository bankAccountRepository,
                          TripAccessService tripAccess) {
        this(goalRepository, contributionRepository, tripService, budgetRepository, bankAccountRepository,
            tripAccess, Clock.systemDefaultZone());
    }

    // For tests: same as above with a fixed "today"
    SavingsService(SavingsGoalRepository goalRepository,
                   SavingsContributionRepository contributionRepository,
                   TripService tripService,
                   TripBudgetRepository budgetRepository,
                   BankAccountRepository bankAccountRepository,
                   TripAccessService tripAccess,
                   Clock clock) {
        this.goalRepository = goalRepository;
        this.contributionRepository = contributionRepository;
        this.tripService = tripService;
        this.budgetRepository = budgetRepository;
        this.bankAccountRepository = bankAccountRepository;
        this.tripAccess = tripAccess;
        this.clock = clock;
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

        Optional<SavingsGoal> existing = goalRepository.findByTripId(tripId);
        SavingsGoal goal = existing.orElseGet(() -> new SavingsGoal(tripId));

        // UC8: a new or changed target date can't be in the past (today is fine).
        // An existing date that has already passed may stay as it is, so the other
        // fields can still be updated. A missing date is rejected by validation (@NotNull).
        LocalDate newDate = request.targetDate();
        boolean dateChanges = existing.isEmpty() || !Objects.equals(goal.getTargetDate(), newDate);
        if (newDate != null && dateChanges && newDate.isBefore(today())) {
            throw new IllegalArgumentException("Target date can't be in the past. Choose today or a later date.");
        }

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
            request.date() == null ? today() : request.date(),
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

    // ---------- Contribution history with filters (UC7) ----------

    // Lists the goal's contributions, optionally filtered by date range and bank account.
    // All filters are optional and can be combined; with none, every contribution is listed.
    //   from / to:      dates like 2027-01-31, both days included
    //   bankAccountId:  one of the requesting user's OWN bank accounts (404 otherwise, so no one
    //                   can find out whether someone else's account exists)
    // The goal totals, status and projection in the response are for the whole goal
    // and are never changed by the filters. Any trip member can use this (404 without access).
    @Transactional(readOnly = true)
    public ContributionHistoryResponse contributionHistory(Long tripId, String from, String to,
                                                           String bankAccountId, Long userId) {
        TripAccess access = tripAccess.requireRole(tripId, userId, TripRole.VIEWER);

        LocalDate fromDate = parseDate(from, "from");
        LocalDate toDate = parseDate(to, "to");
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new IllegalArgumentException("Start date (from) can't be after end date (to)");
        }
        Long accountId = parseId(bankAccountId);
        if (accountId != null && bankAccountRepository.findByIdAndUserId(accountId, userId).isEmpty()) {
            throw new BankAccountNotFoundException();
        }

        Optional<SavingsGoal> found = goalRepository.findByTripId(tripId);
        if (found.isEmpty()) {
            return new ContributionHistoryResponse(tripId, fromDate, toDate, accountId,
                0, BigDecimal.ZERO, List.of(),
                null, null, null, null, null, null, null,
                "This trip has no savings goal yet");
        }
        SavingsGoal goal = found.get();

        // The whole goal first: these numbers ignore the filters
        Progress p = progress(goal);
        List<SavingsContribution> all = contributionRepository
            .findBySavingsGoalIdOrderByTransactionDateDescIdDesc(goal.getId());
        SavingsProjection.Result projection = projection(goal, p, all, access.trip());

        // Then the filtered list (same order: newest first)
        List<SavingsContribution> matching = all.stream()
            .filter(c -> fromDate == null || !c.getTransactionDate().isBefore(fromDate))
            .filter(c -> toDate == null || !c.getTransactionDate().isAfter(toDate))
            .filter(c -> accountId == null || accountId.equals(c.getBankAccountId()))
            .toList();
        BigDecimal filteredTotal = matching.stream()
            .map(SavingsContribution::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        String message = null;
        if (all.isEmpty()) {
            message = "No contributions yet";
        } else if (matching.isEmpty()) {
            message = "No contributions match these filters";
        }

        return new ContributionHistoryResponse(tripId, fromDate, toDate, accountId,
            matching.size(), filteredTotal, toContributions(matching, access, userId),
            goal.getTargetAmount(), p.saved(), p.remaining(), p.percent(), p.status(),
            projection.projectedCompletionDate(), projection.status(),
            message);
    }

    // Empty means "not used"; anything else must be a date like 2027-01-31 (400 otherwise)
    private static LocalDate parseDate(String value, String name) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(name + " must be a date like 2027-01-31");
        }
    }

    // Empty means "not used"; anything else must be a whole number (400 otherwise)
    private static Long parseId(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("bankAccountId must be a number");
        }
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

        LocalDate today = today();
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
        List<SavingsContribution> all = contributionRepository
            .findBySavingsGoalIdOrderByTransactionDateDescIdDesc(goal.getId());
        SavingsProjection.Result projection = projection(goal, p, all, access.trip());

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
            toContributions(all, access, userId),
            projection.projectedCompletionDate(),
            projection.monthlyRate(),
            projection.status());
    }

    // Each contribution as this user may see it (bank account only if it is theirs)
    private List<SavingsResponse.Contribution> toContributions(List<SavingsContribution> list,
                                                               TripAccess access, Long userId) {
        Set<Long> myAccountIds = myBankAccountIds(userId);
        return list.stream()
            .map(c -> new SavingsResponse.Contribution(
                c.getId(),
                c.getAmount(),
                c.getTransactionDate(),
                c.getDescription(),
                myAccountIds.contains(c.getBankAccountId()) ? c.getBankAccountId() : null,
                userId.equals(creatorOf(c, access.trip())),
                canDelete(c, access, userId)))
            .toList();
    }

    private Set<Long> myBankAccountIds(Long userId) {
        return bankAccountRepository.findByUserId(userId).stream()
            .map(BankAccount::getId)
            .collect(Collectors.toSet());
    }

    // UC7: projected completion date. Always uses ALL of the goal's contributions.
    private SavingsProjection.Result projection(SavingsGoal goal, Progress p,
                                                List<SavingsContribution> all, Trip trip) {
        return SavingsProjection.project(p.remaining(), all, goal.getTargetDate(), today(), currencyDecimals(trip));
    }

    // Digits after the decimal point for the trip's currency (JPY 0); 2 if unknown
    private static int currencyDecimals(Trip trip) {
        String code = trip == null ? null : trip.getCurrency();
        return SupportedCurrency.isSupported(code) ? SupportedCurrency.fromCode(code).getDecimalPlaces() : 2;
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }
}
