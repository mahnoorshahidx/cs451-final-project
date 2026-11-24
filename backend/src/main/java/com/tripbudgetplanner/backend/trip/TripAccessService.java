package com.tripbudgetplanner.backend.trip;

import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.tripbudgetplanner.backend.member.TripMember;
import com.tripbudgetplanner.backend.member.TripMemberRepository;
import com.tripbudgetplanner.backend.member.TripRole;
import com.tripbudgetplanner.backend.trip.exception.TripAccessDeniedException;
import com.tripbudgetplanner.backend.trip.exception.TripNotFoundException;

// The one place that decides who can do what on a trip.
//
// - Not signed in                         -> 401
// - Trip creator (trips.user_id)          -> OWNER
// - Row in trip_members                   -> that member's EDITOR or VIEWER role
// - Anyone else, or the trip doesn't exist -> 404 (so nobody can tell a trip exists)
// - Role too low for the action           -> 403
@Service
public class TripAccessService {

    private final TripRepository tripRepository;
    private final TripMemberRepository memberRepository;

    public TripAccessService(TripRepository tripRepository, TripMemberRepository memberRepository) {
        this.tripRepository = tripRepository;
        this.memberRepository = memberRepository;
    }

    // The trip plus the user's role on it
    public record TripAccess(Trip trip, TripRole role) {}

    // Checks the user has at least the given role and returns the trip and their role
    public TripAccess requireRole(Long tripId, Long userId, TripRole needed) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in required");
        }

        // Same owner query the app has always used, so owned trips work exactly as before
        Optional<Trip> owned = tripRepository.findByIdAndUserId(tripId, userId);
        TripAccess access = owned
            .map(trip -> new TripAccess(trip, TripRole.OWNER))
            .orElseGet(() -> memberRepository.findByTripIdAndUserId(tripId, userId)
                .map(member -> new TripAccess(member.getTrip(), member.getRole()))
                .orElseThrow(TripNotFoundException::new));

        if (!access.role().atLeast(needed)) {
            throw new TripAccessDeniedException(deniedMessage(needed));
        }
        return access;
    }

    // Any member (owner, editor or viewer) can read
    public Trip viewableTrip(Long tripId, Long userId) {
        return requireRole(tripId, userId, TripRole.VIEWER).trip();
    }

    // Owner or editor can change trip data
    public Trip editableTrip(Long tripId, Long userId) {
        return requireRole(tripId, userId, TripRole.EDITOR).trip();
    }

    // Only the owner (delete trip, manage members)
    public Trip ownedTrip(Long tripId, Long userId) {
        return requireRole(tripId, userId, TripRole.OWNER).trip();
    }

    // The user's role on a trip that was already loaded, or null if they have none
    public TripRole roleOf(Trip trip, Long userId) {
        if (userId == null) {
            return null;
        }
        if (trip.getUser() != null && userId.equals(trip.getUser().getId())) {
            return TripRole.OWNER;
        }
        return memberRepository.findByTripIdAndUserId(trip.getId(), userId)
            .map(TripMember::getRole)
            .orElse(null);
    }

    private static String deniedMessage(TripRole needed) {
        return needed == TripRole.OWNER
            ? "Only the trip owner can do this."
            : "You have view-only access to this trip.";
    }
}
