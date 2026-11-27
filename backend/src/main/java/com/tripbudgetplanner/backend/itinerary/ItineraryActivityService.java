
package com.tripbudgetplanner.backend.itinerary;

import org.springframework.beans.factory.annotation.Autowired;

import com.tripbudgetplanner.backend.budget.TripBudgetService;
import com.tripbudgetplanner.backend.search.TripItemType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.tripbudgetplanner.backend.currency.SupportedCurrency;
import com.tripbudgetplanner.backend.itinerary.dto.ItineraryActivityRequest;
import com.tripbudgetplanner.backend.itinerary.dto.ItineraryActivityResponse;
import com.tripbudgetplanner.backend.trip.Trip;
import com.tripbudgetplanner.backend.trip.TripAccessService;

@Service
@Transactional(readOnly = true)
public class ItineraryActivityService {

    private final ItineraryActivityRepository activityRepository;
    private final TripAccessService tripAccess;

    public ItineraryActivityService(
            ItineraryActivityRepository activityRepository,
            TripAccessService tripAccess) {

        this.activityRepository = activityRepository;
        this.tripAccess = tripAccess;
    }

    // Used to take a search item's amount back out of the budget on delete (UC6).
    // Set by Spring; tests that build this service by hand leave it null.
    private TripBudgetService budgetService;

    @Autowired(required = false)
    public void setBudgetService(TripBudgetService budgetService) {
        this.budgetService = budgetService;
    }

    // Retrieve all activities for a trip.
    public List<ItineraryActivityResponse> listActivities(
            Long tripId, Long userId) {

        // Any trip member can view the itinerary.
        tripAccess.viewableTrip(tripId, userId);

        return activityRepository
                .findByTripIdOrderByActivityDateAscStartTimeAsc(tripId)
                .stream()
                .map(ItineraryActivityService::toResponse)
                .toList();
    }

    // Create and save a new activity.
    @Transactional
    public ItineraryActivityResponse createActivity(
            Long tripId,
            ItineraryActivityRequest request,
            Long userId) {

        // Owner or editor (403 for viewers)
        Trip trip = tripAccess.editableTrip(tripId, userId);

        validateActivity(request, trip);

        ItineraryActivity activity = new ItineraryActivity();
        activity.setTrip(trip);

        applyRequest(activity, request, trip);

        return toResponse(activityRepository.save(activity));
    }

    // Update an existing activity.
    @Transactional
    public ItineraryActivityResponse updateActivity(
            Long tripId,
            Long activityId,
            ItineraryActivityRequest request,
            Long userId) {

        // Owner or editor (403 for viewers)
        Trip trip = tripAccess.editableTrip(tripId, userId);

        ItineraryActivity activity = findActivity(
                activityId, tripId);

        validateActivity(request, trip);

        applyRequest(activity, request, trip);

        return toResponse(activityRepository.save(activity));
    }

    // Delete an activity. The budget is not changed.
    @Transactional
    public void deleteActivity(
            Long tripId,
            Long activityId,
            Long userId) {
        deleteActivity(tripId, activityId, userId, false);
    }

    // Delete an activity. adjustBudget = true also takes back what this item added to the budget
    // when it came from travel search with "update budget" (UC6). Returns the amount taken back
    // (zero when nothing was).
    @Transactional
    public BigDecimal deleteActivity(
            Long tripId,
            Long activityId,
            Long userId,
            boolean adjustBudget) {

        // Owner or editor (403 for viewers)
        tripAccess.editableTrip(tripId, userId);

        ItineraryActivity activity = findActivity(
                activityId, tripId);

        BigDecimal takenBack = BigDecimal.ZERO;
        if (adjustBudget && budgetService != null
                && activity.getBudgetAmountAdded() != null && activity.getSourceType() != null) {
            String category = budgetCategoryOf(activity.getSourceType());
            if (category != null) {
                takenBack = budgetService.takeBackFromCategory(tripId, category,
                        activity.getBudgetAmountAdded(), activity.getBudgetAmountCurrency());
            }
        }

        activityRepository.delete(activity);
        return takenBack;
    }

    // The budget category a search item was added to ("HOTEL" -> "Accommodation"), or null
    private static String budgetCategoryOf(String sourceType) {
        try {
            return TripItemType.valueOf(sourceType).getBudgetCategory();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // Delete every activity in a trip's itinerary ("Delete itinerary"). The trip itself stays.
    // Like deleteActivity, budget amounts added from search results are not taken back.
    @Transactional
    public int deleteAllActivities(Long tripId, Long userId) {

        // Owner or editor (403 for viewers)
        tripAccess.editableTrip(tripId, userId);

        List<ItineraryActivity> activities =
                activityRepository.findByTripIdOrderByActivityDateAscStartTimeAsc(tripId);
        activityRepository.deleteAll(activities);
        return activities.size();
    }

    // Find an activity belonging to a specific trip.
    private ItineraryActivity findActivity(
            Long activityId, Long tripId) {

        return activityRepository
                .findByIdAndTripId(activityId, tripId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Activity not found"));
    }

    // Validate the date and time information.
    private void validateActivity(
            ItineraryActivityRequest request,
            Trip trip) {

        // Activity must occur during the trip.
        if (request.activityDate().isBefore(trip.getStartDate())
                || request.activityDate().isAfter(trip.getEndDate())) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Activity date must be within the trip dates");
        }

        // End time cannot exist without a start time.
        if (request.startTime() == null
                && request.endTime() != null) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Start time is required when an end time is provided");
        }

        // End time must be later than start time.
        if (request.startTime() != null
                && request.endTime() != null
                && !request.endTime().isAfter(request.startTime())) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "End time must be after start time");
        }
    }

    // Copy request information into the database entity.
    private void applyRequest(
            ItineraryActivity activity,
            ItineraryActivityRequest request,
            Trip trip) {

        activity.setActivityDate(request.activityDate());
        activity.setTitle(request.title().trim());
        activity.setStartTime(request.startTime());
        activity.setEndTime(request.endTime());
        activity.setCategory(request.category().trim());
        activity.setLocation(request.location());
        activity.setNotes(request.notes());

        // A cost is saved in the trip's currency. Leaving it out keeps the saved cost.
        if (request.estimatedCost() != null) {
            SupportedCurrency currency = SupportedCurrency.fromCode(trip.getCurrency());
            activity.setEstimatedCost(costInTripCurrency(request.estimatedCost(), currency), currency.name());
        }
    }

    // JPY and KRW have no decimals, so 1500.50 JPY is rejected
    private static BigDecimal costInTripCurrency(BigDecimal cost, SupportedCurrency currency) {
        if (cost.stripTrailingZeros().scale() > currency.getDecimalPlaces()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                currency + " amounts can have at most " + currency.getDecimalPlaces() + " decimal places");
        }
        return cost.setScale(currency.getDecimalPlaces(), RoundingMode.UNNECESSARY);
    }

    // Convert an entity into a response for the frontend.
    // Public so the travel search feature (UC6) returns activities in the same shape.
    public static ItineraryActivityResponse toResponse(
            ItineraryActivity activity) {

        return new ItineraryActivityResponse(
                activity.getId(),
                activity.getTrip().getId(),
                activity.getActivityDate(),
                activity.getTitle(),
                activity.getStartTime(),
                activity.getEndTime(),
                activity.getCategory(),
                activity.getLocation(),
                activity.getNotes(),
                activity.getEstimatedCost(),
                activity.getCostCurrency(),
                activity.getSourceType(),
                activity.getSourceProvider(),
                activity.getSourceSegment(),
                activity.getBudgetAmountAdded(),
                activity.getBudgetAmountCurrency()
        );
    }
}
