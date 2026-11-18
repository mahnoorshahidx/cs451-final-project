package com.tripbudgetplanner.backend.recommendation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import com.tripbudgetplanner.backend.ai.AiClientException;
import com.tripbudgetplanner.backend.ai.AnthropicClient;
import com.tripbudgetplanner.backend.recommendation.dto.BudgetRecommendation;
import com.tripbudgetplanner.backend.recommendation.dto.BudgetRecommendationRequest;
import com.tripbudgetplanner.backend.recommendation.dto.TimingRecommendation;
import com.tripbudgetplanner.backend.recommendation.dto.TimingRecommendationRequest;

import tools.jackson.databind.ObjectMapper;

// No real API calls: the AnthropicClient is mocked, except parseJsonObject which runs for real.
class AiRecommendationProviderTest {

    private AnthropicClient ai;
    private AiRecommendationProvider provider;

    private final LocalDate windowStart = LocalDate.now().plusMonths(2).withDayOfMonth(1);
    private final LocalDate windowEnd = windowStart.plusMonths(2);

    @BeforeEach
    void setUp() {
        ai = spy(new AnthropicClient(new ObjectMapper(), "test-key", "test-model", "https://example.invalid", 5, 512));
        provider = new AiRecommendationProvider(ai, new RuleBasedRecommendationProvider());
    }

    private TimingRecommendationRequest timingRequest() {
        return new TimingRecommendationRequest("Maui, Hawaii", "best", windowStart, windowEnd, 7);
    }

    private BudgetRecommendationRequest budgetRequest() {
        return new BudgetRecommendationRequest("Maui, Hawaii", windowStart, 7, 2, List.of("budgetHotels"), "USD");
    }

    // ---------------- Timing ----------------

    @Test
    void timingUsesAiAnswerWhenValid() {
        LocalDate pick = windowStart.plusDays(10);
        doReturn("```json\n{\"startDate\": \"" + pick + "\", \"reason\": \"Great weather and fewer crowds.\"}\n```")
            .when(ai).complete(anyString(), anyString());

        TimingRecommendation result = provider.recommendTiming(timingRequest());

        assertEquals("ai", result.source());
        assertEquals(pick, result.startDate());
        assertEquals(pick.plusDays(7), result.endDate());
        assertEquals("Great weather and fewer crowds.", result.reason());
    }

    @Test
    void timingFallsBackWhenAiPicksDateOutsideWindow() {
        doReturn("{\"startDate\": \"" + windowEnd.plusDays(30) + "\", \"reason\": \"Nice.\"}")
            .when(ai).complete(anyString(), anyString());

        TimingRecommendation result = provider.recommendTiming(timingRequest());

        assertEquals("rules", result.source());
    }

    @Test
    void timingFallsBackWhenAiCallFails() {
        doThrow(new AiClientException("HTTP 529")).when(ai).complete(anyString(), anyString());

        assertEquals("rules", provider.recommendTiming(timingRequest()).source());
    }

    @Test
    void timingFallsBackOnBadDate() {
        doReturn("{\"startDate\": \"sometime in spring\", \"reason\": \"Nice.\"}")
            .when(ai).complete(anyString(), anyString());

        assertEquals("rules", provider.recommendTiming(timingRequest()).source());
    }

    @Test
    void timingRejectsWindowShorterThanTrip() {
        TimingRecommendationRequest tooShort =
            new TimingRecommendationRequest("Maui", "best", windowStart, windowStart.plusDays(3), 7);

        assertThrows(IllegalArgumentException.class, () -> provider.recommendTiming(tooShort));
        verify(ai, never()).complete(anyString(), anyString());
    }

    // ---------------- Budget ----------------

    @Test
    void budgetUsesAiAmountsAndCalculatesTotals() {
        doReturn("""
            {"allocations": {"Flights": 1400, "Accommodation": 1750, "Food": 640, "Local transport": 420, "Activities": 300},
             "assumptions": ["Maui hotels are pricey year-round.", "Rental car included in local transport."]}
            """).when(ai).complete(anyString(), anyString());

        BudgetRecommendation result = provider.recommendBudget(budgetRequest());

        assertEquals("ai", result.source());
        assertEquals(AiRecommendationProvider.CATEGORIES, List.copyOf(result.allocations().keySet()));
        assertEquals(new BigDecimal("1400"), result.allocations().get("Flights"));

        // subtotal 4510: +10% buffer = 4961 -> 5000; low 3833.5 -> 3850; high 5863 -> 5900
        assertEquals(new BigDecimal("5000"), result.recommendedTotal());
        assertEquals(new BigDecimal("3850"), result.lowTotal());
        assertEquals(new BigDecimal("5900"), result.highTotal());
        assertTrue(result.assumptions().contains("Maui hotels are pricey year-round."));
    }

    @Test
    void budgetFallsBackOnNegativeAmount() {
        doReturn("""
            {"allocations": {"Flights": -5, "Accommodation": 1750, "Food": 640, "Local transport": 420, "Activities": 300}}
            """).when(ai).complete(anyString(), anyString());

        assertEquals("rules", provider.recommendBudget(budgetRequest()).source());
    }

    @Test
    void budgetFallsBackOnMissingCategory() {
        doReturn("{\"allocations\": {\"Flights\": 1400, \"Food\": 640}}")
            .when(ai).complete(anyString(), anyString());

        assertEquals("rules", provider.recommendBudget(budgetRequest()).source());
    }

    @Test
    void budgetFallsBackOnNonJsonReply() {
        doReturn("Sorry, I can't help with that.").when(ai).complete(anyString(), anyString());

        assertEquals("rules", provider.recommendBudget(budgetRequest()).source());
    }

    // ---------------- No key ----------------

    @Test
    void usesRulesWithoutCallingAiWhenNoKey() {
        AnthropicClient noKey = spy(new AnthropicClient(new ObjectMapper(), "", "test-model", "https://example.invalid", 5, 512));
        AiRecommendationProvider noKeyProvider = new AiRecommendationProvider(noKey, new RuleBasedRecommendationProvider());

        assertEquals("rules", noKeyProvider.recommendTiming(timingRequest()).source());
        assertEquals("rules", noKeyProvider.recommendBudget(budgetRequest()).source());
        verify(noKey, never()).complete(anyString(), anyString());
    }
}