
package com.tripbudgetplanner.backend.dashboard;

import com.tripbudgetplanner.backend.bank.BankAccount;
import com.tripbudgetplanner.backend.bank.BankAccountRepository;
import com.tripbudgetplanner.backend.budget.TripBudget;
import com.tripbudgetplanner.backend.budget.TripBudgetRepository;
import com.tripbudgetplanner.backend.trip.Trip;
import com.tripbudgetplanner.backend.trip.TripRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private TripRepository tripRepository;

    @Mock
    private TripBudgetRepository budgetRepository;

    @Mock
    private BankAccountRepository bankAccountRepository;

    @InjectMocks
    private DashboardService dashboardService;

    private final Long userId = 1L;

    // Test a user who has no trips or connected bank accounts
    @Test
    void shouldReturnEmptyDashboard() {
        when(tripRepository.findByUserIdOrderByStartDateAsc(userId))
            .thenReturn(List.of());

        when(bankAccountRepository.findByUserId(userId))
            .thenReturn(List.of());

        DashboardSummaryResponse result = dashboardService.getSummary(userId);

        assertEquals(0, result.totalTrips());
        assertEquals(0, result.upcomingTrips());
        assertTrue(result.budgetTotals().isEmpty());
        assertEquals(0, result.connectedBankAccounts());
    }

    // Test counting all of the user's trips
    @Test
    void shouldCountTotalTrips() {
        Trip trip1 = mock(Trip.class);
        Trip trip2 = mock(Trip.class);

        when(trip1.getEndDate()).thenReturn(java.time.LocalDate.now().plusDays(5));
        when(trip2.getEndDate()).thenReturn(java.time.LocalDate.now().minusDays(5));

        when(tripRepository.findByUserIdOrderByStartDateAsc(userId))
            .thenReturn(List.of(trip1, trip2));

        when(bankAccountRepository.findByUserId(userId))
            .thenReturn(List.of());

        DashboardSummaryResponse result = dashboardService.getSummary(userId);

        assertEquals(2, result.totalTrips());
        assertEquals(1, result.upcomingTrips());
    }

    // Test adding budgets together when they use the same currency
    @Test
    void shouldCalculateBudgetTotals() {
        Trip trip1 = mock(Trip.class);
        Trip trip2 = mock(Trip.class);

        when(trip1.getId()).thenReturn(1L);
        when(trip2.getId()).thenReturn(2L);
        when(trip1.getEndDate()).thenReturn(java.time.LocalDate.now().plusDays(5));
        when(trip2.getEndDate()).thenReturn(java.time.LocalDate.now().plusDays(10));

        TripBudget budget1 = mock(TripBudget.class);
        TripBudget budget2 = mock(TripBudget.class);

        when(budget1.getCurrency()).thenReturn("USD");
        when(budget2.getCurrency()).thenReturn("USD");
        when(budget1.getTotalAmount()).thenReturn(new BigDecimal("2000.00"));
        when(budget2.getTotalAmount()).thenReturn(new BigDecimal("3000.00"));

        when(tripRepository.findByUserIdOrderByStartDateAsc(userId))
            .thenReturn(List.of(trip1, trip2));

        when(budgetRepository.findByTripId(1L)).thenReturn(Optional.of(budget1));
        when(budgetRepository.findByTripId(2L)).thenReturn(Optional.of(budget2));

        when(bankAccountRepository.findByUserId(userId))
            .thenReturn(List.of());

        DashboardSummaryResponse result = dashboardService.getSummary(userId);

        assertEquals(new BigDecimal("5000.00"),
            result.budgetTotals().get("USD"));
    }

    // Test keeping budgets in different currencies separate
    @Test
    void shouldSeparateCurrencies() {
        Trip trip1 = mock(Trip.class);
        Trip trip2 = mock(Trip.class);

        when(trip1.getId()).thenReturn(1L);
        when(trip2.getId()).thenReturn(2L);
        when(trip1.getEndDate()).thenReturn(java.time.LocalDate.now().plusDays(5));
        when(trip2.getEndDate()).thenReturn(java.time.LocalDate.now().plusDays(10));

        TripBudget budget1 = mock(TripBudget.class);
        TripBudget budget2 = mock(TripBudget.class);

        when(budget1.getCurrency()).thenReturn("USD");
        when(budget2.getCurrency()).thenReturn("EUR");
        when(budget1.getTotalAmount()).thenReturn(new BigDecimal("3000.00"));
        when(budget2.getTotalAmount()).thenReturn(new BigDecimal("2000.00"));

        when(tripRepository.findByUserIdOrderByStartDateAsc(userId))
            .thenReturn(List.of(trip1, trip2));

        when(budgetRepository.findByTripId(1L)).thenReturn(Optional.of(budget1));
        when(budgetRepository.findByTripId(2L)).thenReturn(Optional.of(budget2));

        when(bankAccountRepository.findByUserId(userId))
            .thenReturn(List.of());

        DashboardSummaryResponse result = dashboardService.getSummary(userId);

        assertEquals(new BigDecimal("3000.00"),
            result.budgetTotals().get("USD"));
        assertEquals(new BigDecimal("2000.00"),
            result.budgetTotals().get("EUR"));
    }

    // Test counting connected bank accounts
    @Test
    void shouldCountConnectedBankAccounts() {
        BankAccount account1 = mock(BankAccount.class);
        BankAccount account2 = mock(BankAccount.class);

        when(tripRepository.findByUserIdOrderByStartDateAsc(userId))
            .thenReturn(List.of());

        when(bankAccountRepository.findByUserId(userId))
            .thenReturn(List.of(account1, account2));

        DashboardSummaryResponse result = dashboardService.getSummary(userId);

        assertEquals(2, result.connectedBankAccounts());
    }
}
