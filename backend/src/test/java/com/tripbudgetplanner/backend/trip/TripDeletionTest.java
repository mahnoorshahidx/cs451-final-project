package com.tripbudgetplanner.backend.trip;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import com.tripbudgetplanner.backend.budget.TripBudget;
import com.tripbudgetplanner.backend.budget.TripBudgetRepository;
import com.tripbudgetplanner.backend.expense.ExpenseRepository;
import com.tripbudgetplanner.backend.itinerary.ItineraryActivityRepository;
import com.tripbudgetplanner.backend.member.TripInvitationRepository;
import com.tripbudgetplanner.backend.member.TripMember;
import com.tripbudgetplanner.backend.member.TripMemberRepository;
import com.tripbudgetplanner.backend.member.TripRole;
import com.tripbudgetplanner.backend.notification.NotificationRepository;
import com.tripbudgetplanner.backend.savings.SavingsContributionRepository;
import com.tripbudgetplanner.backend.savings.SavingsGoal;
import com.tripbudgetplanner.backend.savings.SavingsGoalRepository;
import com.tripbudgetplanner.backend.trip.exception.TripAccessDeniedException;
import com.tripbudgetplanner.backend.trip.exception.TripNotFoundException;
import com.tripbudgetplanner.backend.user.User;
import com.tripbudgetplanner.backend.user.UserRepository;

// UC12: deleting a trip removes everything that belongs to it, in a safe order,
// and nothing else. Only the owner can delete.
//
// The repositories here are small recording fakes, so the test can check the exact
// order of every database call across all repositories.
class TripDeletionTest {

    private static final Long TRIP_ID = 10L;
    private static final Long OTHER_TRIP_ID = 11L;
    private static final Long GOAL_ID = 100L;
    private static final Long OWNER_ID = 1L;
    private static final Long EDITOR_ID = 2L;
    private static final Long VIEWER_ID = 3L;
    private static final Long STRANGER_ID = 4L;

    private Recorder rec;
    private TripService service;
    private Trip trip;
    private SavingsGoal goal;
    private TripBudget budget;
    private UserRepository users;

    @BeforeEach
    void setUp() {
        rec = new Recorder();
        User owner = user(OWNER_ID);
        trip = new Trip();
        ReflectionTestUtils.setField(trip, "id", TRIP_ID);
        trip.setName("Tokyo");
        trip.setUser(owner);

        goal = new SavingsGoal(TRIP_ID);
        ReflectionTestUtils.setField(goal, "id", GOAL_ID);
        budget = new TripBudget(TRIP_ID);

        // The trip has a savings goal and a budget; the rest is deleted by trip ID
        rec.answer("trips.findByIdAndUserId", args -> args[0].equals(TRIP_ID) && args[1].equals(OWNER_ID)
            ? Optional.of(trip) : Optional.empty());
        rec.answer("members.findByTripIdAndUserId", args -> {
            if (!args[0].equals(TRIP_ID)) return Optional.empty();
            if (args[1].equals(EDITOR_ID)) return Optional.of(member(TripRole.EDITOR, EDITOR_ID));
            if (args[1].equals(VIEWER_ID)) return Optional.of(member(TripRole.VIEWER, VIEWER_ID));
            return Optional.empty();
        });
        rec.answer("budgets.lockTrip", args -> Optional.of(trip));
        rec.answer("goals.findByTripId", args -> args[0].equals(TRIP_ID) ? Optional.of(goal) : Optional.empty());
        rec.answer("budgets.findByTripId", args -> args[0].equals(TRIP_ID) ? Optional.of(budget) : Optional.empty());

        users = rec.repo(UserRepository.class, "users");
        service = newService(true, true);
    }

    // ---------- Everything goes, in a safe order ----------

    @Test
    void ownerDeletesTheTripAndEverythingThatBelongsToIt() {
        service.deleteTrip(TRIP_ID, OWNER_ID);

        assertEquals(List.of(
            "goals.findByTripId(10)",
            "contributions.deleteBySavingsGoalId(100)",   // contributions before their goal
            "goals.delete(goal)",
            "expenses.deleteByTripId(10)",
            "notifications.deleteByTripId(10)",
            "activities.deleteByTripId(10)",              // includes UC6 search results and cost estimates
            "budgets.findByTripId(10)",
            "budgets.delete(budget)",                     // the budget and its allocations
            "members.deleteByTripId(10)",
            "invitations.deleteByTripId(10)",
            "trips.delete(trip)"),                        // the trip row last
            rec.callsAfter("trips.findByIdAndUserId"));
    }

    @Test
    void theTripRowIsLockedBeforeAnythingElse() {
        service.deleteTrip(TRIP_ID, OWNER_ID);

        // Locked first, then the ownership check, so the check sees the latest data
        // and a second delete of the same trip waits and then gets a 404
        assertEquals("budgets.lockTrip(10)", rec.calls.get(0));
        assertEquals("trips.findByIdAndUserId(10, 1)", rec.calls.get(1));
    }

    @Test
    void aTripWithNoSavingsGoalOrBudgetIsStillDeleted() {
        rec.answer("goals.findByTripId", args -> Optional.empty());
        rec.answer("budgets.findByTripId", args -> Optional.empty());

        service.deleteTrip(TRIP_ID, OWNER_ID);

        assertFalse(rec.called("contributions.deleteBySavingsGoalId"));
        assertFalse(rec.called("goals.delete"));
        assertFalse(rec.called("budgets.delete"));
        assertTrue(rec.calls.contains("expenses.deleteByTripId(10)"));
        assertTrue(rec.calls.contains("notifications.deleteByTripId(10)"));
        assertEquals("trips.delete(trip)", last());
    }

    // ---------- Nothing else is touched ----------

    @Test
    void onlyThisTripsRowsAreDeleted() {
        service.deleteTrip(TRIP_ID, OWNER_ID);

        for (String call : rec.calls) {
            if (call.contains("ByTripId(")) {
                assertTrue(call.endsWith("(10)"), "only trip 10's rows: " + call);
            }
            assertFalse(call.contains("(" + OTHER_TRIP_ID + ")"), call);
        }
        // Contributions are deleted by this trip's goal only
        assertEquals(1L, rec.count("contributions."));
        assertTrue(rec.calls.contains("contributions.deleteBySavingsGoalId(100)"));
    }

    @Test
    void usersAndBankAccountsAreNeverTouched() {
        service.deleteTrip(TRIP_ID, OWNER_ID);

        // TripService doesn't even have the bank account repository, and the user repository isn't used
        assertEquals(0L, rec.count("users."));
        assertFalse(rec.calls.stream().anyMatch(c -> c.toLowerCase().contains("bank")));
        // Nothing is deleted "all at once"
        assertFalse(rec.calls.stream().anyMatch(c -> c.contains("deleteAll")));
    }

    // ---------- Only the owner ----------

    @Test
    void editorsAndViewersCannotDeleteAndNothingIsRemoved() {
        assertThrows(TripAccessDeniedException.class, () -> service.deleteTrip(TRIP_ID, EDITOR_ID));
        assertThrows(TripAccessDeniedException.class, () -> service.deleteTrip(TRIP_ID, VIEWER_ID));

        assertEquals(0L, rec.count(".delete"));
    }

    @Test
    void strangersGet404AndNothingIsRemoved() {
        assertThrows(TripNotFoundException.class, () -> service.deleteTrip(TRIP_ID, STRANGER_ID));

        assertEquals(0L, rec.count(".delete"));
    }

    @Test
    void aSecondDeleteOfTheSameTripGets404() {
        service.deleteTrip(TRIP_ID, OWNER_ID);
        // After the first delete commits, the trip is gone
        rec.answer("trips.findByIdAndUserId", args -> Optional.empty());
        rec.answer("budgets.lockTrip", args -> Optional.empty());
        rec.calls.clear();

        assertThrows(TripNotFoundException.class, () -> service.deleteTrip(TRIP_ID, OWNER_ID));
        assertEquals(0L, rec.count(".delete"));
    }

    // ---------- All or nothing ----------

    @Test
    void ifACleanupStepFailsTheTripRowIsNotDeletedAndTheErrorIsPassedOn() {
        RuntimeException failure = new IllegalStateException("database error");
        rec.answer("notifications.deleteByTripId", args -> { throw failure; });

        RuntimeException thrown = assertThrows(IllegalStateException.class, () -> service.deleteTrip(TRIP_ID, OWNER_ID));

        assertSame(failure, thrown);
        assertFalse(rec.called("trips.delete"));
        assertFalse(rec.called("members.deleteByTripId"));
    }

    @Test
    void deleteTripRunsInOneTransactionThatRollsBackOnErrors() throws Exception {
        Method method = TripService.class.getMethod("deleteTrip", Long.class, Long.class);
        Transactional tx = method.getAnnotation(Transactional.class);

        // Spring rolls back on any RuntimeException unless told otherwise,
        // so the failure above undoes every row deleted before it
        assertTrue(tx != null, "deleteTrip must be @Transactional");
        assertFalse(tx.readOnly());
        assertEquals(0, tx.noRollbackFor().length);
    }

    // ---------- Older constructors still work ----------

    @Test
    void theConstructorFromBeforeUc12StillDeletesWithoutNotifications() {
        service = newService(true, false);

        service.deleteTrip(TRIP_ID, OWNER_ID);

        assertFalse(rec.called("notifications."));
        assertTrue(rec.calls.contains("activities.deleteByTripId(10)"));
        assertEquals("trips.delete(trip)", last());
    }

    @Test
    void theConstructorFromBeforeUc6StillDeletes() {
        service = new TripService(
            rec.repo(TripRepository.class, "trips"), users,
            rec.repo(SavingsGoalRepository.class, "goals"),
            rec.repo(SavingsContributionRepository.class, "contributions"),
            rec.repo(TripBudgetRepository.class, "budgets"),
            rec.repo(ExpenseRepository.class, "expenses"),
            access(),
            rec.repo(TripMemberRepository.class, "members"),
            rec.repo(TripInvitationRepository.class, "invitations"));

        service.deleteTrip(TRIP_ID, OWNER_ID);

        assertTrue(rec.calls.contains("expenses.deleteByTripId(10)"));
        assertEquals("trips.delete(trip)", last());
    }

    // ---------- Helpers ----------

    private TripService newService(boolean withActivities, boolean withNotifications) {
        TripRepository trips = rec.repo(TripRepository.class, "trips");
        TripMemberRepository members = rec.repo(TripMemberRepository.class, "members");
        TripAccessService access = new TripAccessService(trips, members);
        ItineraryActivityRepository activities = withActivities ? rec.repo(ItineraryActivityRepository.class, "activities") : null;
        if (!withNotifications) {
            return new TripService(trips, users,
                rec.repo(SavingsGoalRepository.class, "goals"),
                rec.repo(SavingsContributionRepository.class, "contributions"),
                rec.repo(TripBudgetRepository.class, "budgets"),
                rec.repo(ExpenseRepository.class, "expenses"),
                access, members,
                rec.repo(TripInvitationRepository.class, "invitations"),
                activities);
        }
        return new TripService(trips, users,
            rec.repo(SavingsGoalRepository.class, "goals"),
            rec.repo(SavingsContributionRepository.class, "contributions"),
            rec.repo(TripBudgetRepository.class, "budgets"),
            rec.repo(ExpenseRepository.class, "expenses"),
            access, members,
            rec.repo(TripInvitationRepository.class, "invitations"),
            activities,
            rec.repo(NotificationRepository.class, "notifications"));
    }

    private TripAccessService access() {
        return new TripAccessService(rec.repo(TripRepository.class, "trips"), rec.repo(TripMemberRepository.class, "members"));
    }

    private String last() {
        return rec.calls.get(rec.calls.size() - 1);
    }

    private TripMember member(TripRole role, Long userId) {
        return new TripMember(trip, user(userId), role, Instant.parse("2026-11-01T12:00:00Z"), OWNER_ID);
    }

    private static User user(Long id) {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    // Records every repository call as "name.method(args)" and returns simple defaults
    // (empty Optional, false, 0, empty list, null) unless an answer was set.
    private final class Recorder {
        final List<String> calls = new ArrayList<>();
        final Map<String, Function<Object[], Object>> answers = new HashMap<>();

        void answer(String nameAndMethod, Function<Object[], Object> answer) {
            answers.put(nameAndMethod, answer);
        }

        @SuppressWarnings("unchecked")
        <T> T repo(Class<T> type, String name) {
            return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
                switch (method.getName()) {
                    case "hashCode": return System.identityHashCode(proxy);
                    case "equals": return proxy == args[0];
                    case "toString": return name;
                    default: break;
                }
                Object[] a = args == null ? new Object[0] : args;
                calls.add(name + "." + method.getName() + "(" + describe(a) + ")");
                Function<Object[], Object> answer = answers.get(name + "." + method.getName());
                return answer != null ? answer.apply(a) : defaultFor(method.getReturnType());
            });
        }

        boolean called(String prefix) {
            return calls.stream().anyMatch(c -> c.startsWith(prefix));
        }

        long count(String part) {
            return calls.stream().filter(c -> c.contains(part)).count();
        }

        // The calls made after the first one starting with the given text
        List<String> callsAfter(String prefix) {
            for (int i = 0; i < calls.size(); i++) {
                if (calls.get(i).startsWith(prefix)) {
                    return calls.subList(i + 1, calls.size());
                }
            }
            return List.of();
        }

        private String describe(Object[] args) {
            return String.join(", ", Arrays.stream(args).map(arg ->
                arg == goal ? "goal" : arg == budget ? "budget" : arg == trip ? "trip" : String.valueOf(arg)).toList());
        }

        private Object defaultFor(Class<?> type) {
            if (type == Optional.class) return Optional.empty();
            if (type == boolean.class) return false;
            if (type == long.class) return 0L;
            if (type == int.class) return 0;
            if (List.class.isAssignableFrom(type)) return new ArrayList<>();
            return null;
        }
    }
}
