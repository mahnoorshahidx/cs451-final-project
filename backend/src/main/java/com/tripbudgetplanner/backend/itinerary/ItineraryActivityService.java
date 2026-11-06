
package com.tripbudgetplanner.backend.itinerary;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.tripbudgetplanner.backend.itinerary.dto.ItineraryActivityRequest;
import com.tripbudgetplanner.backend.itinerary.dto.ItineraryActivityResponse;
import com.tripbudgetplanner.backend.trip.Trip;
import com.tripbudgetplanner.backend.trip.TripService;

@Service
@Transactional(readOnly = true)
public class ItineraryActivityService {

    private final ItineraryActivityRepository activityRepository;
    private final TripService tripService;

    public ItineraryActivityService(
            ItineraryActivityRepository activityRepository,
            TripService tripService) {

        this.activityRepository = activityRepository;
        this.tripService = tripService;
    }

    // Retrieve all activities for a trip.
    public List<ItineraryActivityResponse> listActivities(
            Long tripId, Long userId) {

        // Verify that the logged-in user owns the trip.
        tripService.getTrip(tripId, userId);

        return activityRepository
                .findByTripIdOrderByActivityDateAscStartTimeAsc(tripId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // Create and save a new activity.
    @Transactional
    public ItineraryActivityResponse createActivity(
            Long tripId,
            ItineraryActivityRequest request,
            Long userId) {

        Trip trip = tripService.getTrip(tripId, userId);

        validateActivity(request, trip);

        ItineraryActivity activity = new ItineraryActivity();
        activity.setTrip(trip);

        applyRequest(activity, request);

        return toResponse(activityRepository.save(activity));
    }

    // Update an existing activity.
    @Transactional
    public ItineraryActivityResponse updateActivity(
            Long tripId,
            Long activityId,
            ItineraryActivityRequest request,
            Long userId) {

        Trip trip = tripService.getTrip(tripId, userId);

        ItineraryActivity activity = findActivity(
                activityId, tripId);

        validateActivity(request, trip);

        applyRequest(activity, request);

        return toResponse(activityRepository.save(activity));
    }

    // Delete an activity.
    @Transactional
    public void deleteActivity(
            Long tripId,
            Long activityId,
            Long userId) {

        tripService.getTrip(tripId, userId);

        ItineraryActivity activity = findActivity(
                activityId, tripId);

        activityRepository.delete(activity);
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
            ItineraryActivityRequest request) {

        activity.setActivityDate(request.activityDate());
        activity.setTitle(request.title().trim());
        activity.setStartTime(request.startTime());
        activity.setEndTime(request.endTime());
        activity.setCategory(request.category().trim());
        activity.setLocation(request.location());
        activity.setNotes(request.notes());
    }

    // Convert an entity into a response for the frontend.
    private ItineraryActivityResponse toResponse(
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
                activity.getNotes()
        );
    }
}
