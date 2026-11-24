package com.tripbudgetplanner.backend.member;

// A user's role on a trip.
// OWNER  - the user who created the trip (trips.user_id). Never stored in trip_members.
// EDITOR - can view and change the trip, but can't delete it or manage members.
// VIEWER - read-only.
public enum TripRole {

    VIEWER(1),
    EDITOR(2),
    OWNER(3);

    private final int level;

    TripRole(int level) {
        this.level = level;
    }

    // True if this role is the same as or higher than the one needed
    public boolean atLeast(TripRole needed) {
        return level >= needed.level;
    }
}
