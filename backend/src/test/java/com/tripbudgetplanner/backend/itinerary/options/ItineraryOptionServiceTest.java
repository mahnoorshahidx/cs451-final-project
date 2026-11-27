package com.tripbudgetplanner.backend.itinerary.options;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.tripbudgetplanner.backend.ai.AiClientException;
import com.tripbudgetplanner.backend.itinerary.ItineraryActivity;
import com.tripbudgetplanner.backend.itinerary.ItineraryActivityRepository;
import com.tripbudgetplanner.backend.itinerary.ItineraryGenerationService;
import com.tripbudgetplanner.backend.itinerary.ItineraryGenerationService.Plan;
import com.tripbudgetplanner.backend.itinerary.ItineraryGenerationService.PlanRange;
import com.tripbudgetplanner.backend.itinerary.dto.ItineraryActivityResponse;
import com.tripbudgetplanner.backend.itinerary.dto.ItineraryGenerationRequest;
import com.tripbudgetplanner.backend.trip.Trip;
import com.tripbudgetplanner.backend.trip.TripAccessService;

class ItineraryOptionServiceTest {

    private static final Long TRIP_ID = 5L;
    private static final Long USER_ID = 9L;
    private static final LocalDate DAY1 = LocalDate.of(2027, 6, 1);
    private static final LocalDate DAY2 = LocalDate.of(2027, 6, 2);

    private ItineraryGenerationService generator;
    private TripAccessService access;
    private ItineraryOptionRepository options;
    private ItineraryActivityRepository activities;
    private ItineraryOptionService service;
    private Trip trip;
    private final PlanRange range = new PlanRange(DAY1, DAY2, false);
    private final ItineraryGenerationRequest request = new ItineraryGenerationRequest(
        null, "USD", "balanced", "any", List.of("Food"), "balanced", List.of(), "", false);

    @BeforeEach
    void setUp() {
        generator = mock(ItineraryGenerationService.class);
        access = mock(TripAccessService.class);
        options = mock(ItineraryOptionRepository.class);
        activities = mock(ItineraryActivityRepository.class);
        service = new ItineraryOptionService(generator, access, options, activities);

        trip = mock(Trip.class);
        when(trip.getId()).thenReturn(TRIP_ID);
        when(trip.getStartDate()).thenReturn(DAY1);
        when(trip.getEndDate()).thenReturn(DAY2);
        when(access.editableTrip(TRIP_ID, USER_ID)).thenReturn(trip);
        when(generator.planRange(eq(trip), any())).thenReturn(range);
        when(options.findByTripIdOrderByIdAsc(TRIP_ID)).thenReturn(List.of());
        when(options.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        when(activities.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
    }

    private ItineraryActivity activity(LocalDate date, String title, String cost) {
        ItineraryActivity a = new ItineraryActivity();
        a.setTrip(trip);
        a.setActivityDate(date);
        a.setTitle(title);
        a.setStartTime(LocalTime.of(10, 0));
        a.setCategory("Food");
        if (cost != null) a.setEstimatedCost(new BigDecimal(cost), "USD");
        return a;
    }

    private Plan plan(String summary, String... costs) {
        List<ItineraryActivity> list = new ArrayList<>();
        for (int i = 0; i < costs.length; i++) {
            list.add(activity(i % 2 == 0 ? DAY1 : DAY2, summary + " stop " + i, costs[i]));
        }
        return new Plan(list, summary);
    }

    @Test
    void makesOneOptionPerStyleWithTotals() {
        when(generator.plan(eq(trip), any(), eq(range))).thenAnswer(inv -> {
            ItineraryGenerationRequest styled = inv.getArgument(1);
            return switch (styled.travelStyle()) {
                case "budget" -> plan("Cheap eats", "10", "20");
                case "balanced" -> plan("Mix", "40", "60");
                default -> plan("Fancy", "150", "250");
            };
        });

        List<ItineraryOptionResponse> result = service.generate(TRIP_ID, request, USER_ID);

        assertEquals(3, result.size());
        assertEquals(List.of("budget", "balanced", "luxury"), result.stream().map(ItineraryOptionResponse::style).toList());
        assertEquals(0, new BigDecimal("30").compareTo(result.get(0).totalCost()));
        assertEquals(0, new BigDecimal("400").compareTo(result.get(2).totalCost()));
        assertEquals("USD", result.get(1).currency());
        assertEquals(2, result.get(1).activityCount());
        verifyNoInteractions(activities);
    }

    @Test
    void keepsTheOptionsThatWorked() {
        when(generator.plan(eq(trip), any(), eq(range))).thenAnswer(inv -> {
            ItineraryGenerationRequest styled = inv.getArgument(1);
            if (styled.travelStyle().equals("luxury")) throw new AiClientException("AI reply was not valid JSON.");
            return plan("Ok", "10");
        });

        assertEquals(2, service.generate(TRIP_ID, request, USER_ID).size());
    }

    @Test
    void noOptionsMeans503() {
        when(generator.plan(eq(trip), any(), eq(range))).thenThrow(new AiClientException("down"));

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
            () -> service.generate(TRIP_ID, request, USER_ID));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE.value(), e.getStatusCode().value());
    }

    @Test
    void applyCopiesTheActivitiesAndClearsTheDrafts() {
        ItineraryOption option = ItineraryOptionService.toOption(trip, ItineraryOptionService.STYLES.get(1),
            plan("Mix", "40", null), range, USER_ID);
        when(options.findByIdAndTripId(3L, TRIP_ID)).thenReturn(Optional.of(option));
        when(options.findByTripIdOrderByIdAsc(TRIP_ID)).thenReturn(List.of(option));

        List<ItineraryActivityResponse> saved = service.apply(TRIP_ID, 3L, true, USER_ID);

        assertEquals(2, saved.size());
        assertEquals(0, new BigDecimal("40").compareTo(saved.get(0).estimatedCost()));
        assertNull(saved.get(1).estimatedCost());
        verify(generator).clearForReplace(eq(TRIP_ID), any(PlanRange.class));
        verify(options).deleteAll(List.of(option));
    }

    @Test
    void applyWithoutReplaceLeavesTheItineraryAlone() {
        ItineraryOption option = ItineraryOptionService.toOption(trip, ItineraryOptionService.STYLES.get(0),
            plan("Cheap", "5"), range, USER_ID);
        when(options.findByIdAndTripId(3L, TRIP_ID)).thenReturn(Optional.of(option));

        service.apply(TRIP_ID, 3L, false, USER_ID);

        verify(generator, never()).clearForReplace(any(), any());
    }

    @Test
    void applyingAMissingOptionIs404() {
        when(options.findByIdAndTripId(3L, TRIP_ID)).thenReturn(Optional.empty());

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
            () -> service.apply(TRIP_ID, 3L, false, USER_ID));
        assertEquals(HttpStatus.NOT_FOUND.value(), e.getStatusCode().value());
    }

    @Test
    void stylesKeepTheOtherAnswers() {
        ItineraryGenerationRequest styled = ItineraryOptionService.withStyle(request, "luxury");

        assertEquals("luxury", styled.travelStyle());
        assertEquals(request.interests(), styled.interests());
        assertEquals(request.pace(), styled.pace());
    }
}
