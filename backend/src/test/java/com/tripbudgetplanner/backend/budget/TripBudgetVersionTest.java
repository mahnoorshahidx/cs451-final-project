package com.tripbudgetplanner.backend.budget;

import static com.tripbudgetplanner.backend.member.SharedTripFixture.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.tripbudgetplanner.backend.budget.dto.BudgetRequest;
import com.tripbudgetplanner.backend.budget.exception.BudgetConflictException;
import com.tripbudgetplanner.backend.currency.SupportedCurrency;
import com.tripbudgetplanner.backend.member.SharedTripFixture;
import com.tripbudgetplanner.backend.trip.exception.TripAccessDeniedException;

// UC6: safe budget updates when several trip members edit at once
class TripBudgetVersionTest {

    private final SharedTripFixture f = new SharedTripFixture();
    private final TripBudgetRepository repository = mock(TripBudgetRepository.class);
    private final TripBudgetService service = new TripBudgetService(repository, f.access);
    private final TripBudget saved = new TripBudget(TRIP_ID);

    TripBudgetVersionTest() {
        saved.setCurrency("USD");
        saved.setTotalAmount(new BigDecimal("1000.00"));
        ReflectionTestUtils.setField(saved, "id", 5L);
        when(repository.findByTripId(TRIP_ID)).thenReturn(Optional.of(saved));
        when(repository.save(any())).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void savingTakesTheTripLockAndBumpsTheVersion() {
        service.saveBudget(TRIP_ID, request(null), OWNER_ID);
        service.saveBudget(TRIP_ID, request(null), EDITOR_ID);

        verify(repository, times(2)).lockTrip(TRIP_ID);
        assertEquals(2L, saved.getVersion());
    }

    @Test
    void staleVersionIsRefusedWith409() {
        saved.nextVersion();   // someone else saved: version 1

        assertThrows(BudgetConflictException.class, () -> service.saveBudget(TRIP_ID, request(0L), OWNER_ID));
        verify(repository, never()).save(any());
        assertEquals(1L, saved.getVersion());
    }

    @Test
    void matchingVersionIsAccepted() {
        saved.nextVersion();

        TripBudget result = service.saveBudget(TRIP_ID, request(1L), OWNER_ID);

        assertEquals(2L, result.getVersion());
    }

    @Test
    void pagesThatDontSendAVersionStillSave() {
        saved.nextVersion();
        saved.nextVersion();

        assertEquals(3L, service.saveBudget(TRIP_ID, request(null), OWNER_ID).getVersion());
    }

    @Test
    void addToCategoryNeedsEditorAccess() {
        assertThrows(TripAccessDeniedException.class, () -> service.addToCategory(TRIP_ID, "Food",
            BigDecimal.TEN, SupportedCurrency.USD, null, VIEWER_ID));
        verify(repository, never()).save(any());
    }

    @Test
    void addToCategoryRefusesBudgetsThatWouldBeTooLarge() {
        saved.setTotalAmount(new BigDecimal("9999999999.00"));
        saved.getAllocations().put("Food", new BigDecimal("9999999999.00"));

        assertThrows(IllegalArgumentException.class, () -> service.addToCategory(TRIP_ID, "Food",
            new BigDecimal("5.00"), SupportedCurrency.USD, null, OWNER_ID));
        verify(repository, never()).save(any());
    }

    @Test
    void addToCategoryBumpsTheVersion() {
        var change = service.addToCategory(TRIP_ID, "Food", new BigDecimal("40.00"), SupportedCurrency.USD, null, EDITOR_ID);

        assertEquals(1L, change.budgetVersion());
        assertTrue(saved.getAllocations().containsKey("Food"));
    }

    private static BudgetRequest request(Long version) {
        return new BudgetRequest(new BigDecimal("1200.00"), "USD", Map.of("Food", new BigDecimal("300.00")), version);
    }
}
