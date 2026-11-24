package com.tripbudgetplanner.backend.trip;

import static com.tripbudgetplanner.backend.member.SharedTripFixture.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.tripbudgetplanner.backend.member.SharedTripFixture;
import com.tripbudgetplanner.backend.member.TripMemberRepository;
import com.tripbudgetplanner.backend.member.TripRole;
import com.tripbudgetplanner.backend.trip.TripAccessService.TripAccess;
import com.tripbudgetplanner.backend.trip.exception.TripAccessDeniedException;
import com.tripbudgetplanner.backend.trip.exception.TripNotFoundException;

// UC9: the central trip permission check
class TripAccessServiceTest {

    private final SharedTripFixture f = new SharedTripFixture();

    @Test
    void ownerKeepsFullAccessWithTheExistingOwnerQuery() {
        TripAccess access = f.access.requireRole(TRIP_ID, OWNER_ID, TripRole.OWNER);

        assertSame(f.trip, access.trip());
        assertEquals(TripRole.OWNER, access.role());
    }

    @Test
    void ownerDoesNotNeedAMembershipRow() {
        SharedTripFixture fresh = new SharedTripFixture();
        TripAccessService access = new TripAccessService(fresh.tripRepository, mock(TripMemberRepository.class));

        assertEquals(TripRole.OWNER, access.requireRole(TRIP_ID, OWNER_ID, TripRole.VIEWER).role());
    }

    @Test
    void editorCanViewAndEdit() {
        assertEquals(TripRole.EDITOR, f.access.requireRole(TRIP_ID, EDITOR_ID, TripRole.VIEWER).role());
        assertSame(f.trip, f.access.editableTrip(TRIP_ID, EDITOR_ID));
    }

    @Test
    void editorCannotDoOwnerActions() {
        TripAccessDeniedException e = assertThrows(TripAccessDeniedException.class,
            () -> f.access.ownedTrip(TRIP_ID, EDITOR_ID));

        assertEquals("Only the trip owner can do this.", e.getMessage());
    }

    @Test
    void viewerCanOnlyView() {
        assertSame(f.trip, f.access.viewableTrip(TRIP_ID, VIEWER_ID));
        assertThrows(TripAccessDeniedException.class, () -> f.access.editableTrip(TRIP_ID, VIEWER_ID));
        assertThrows(TripAccessDeniedException.class, () -> f.access.ownedTrip(TRIP_ID, VIEWER_ID));
    }

    @Test
    void userWithoutAccessGets404() {
        assertThrows(TripNotFoundException.class, () -> f.access.viewableTrip(TRIP_ID, STRANGER_ID));
    }

    @Test
    void missingTripGets404() {
        assertThrows(TripNotFoundException.class, () -> f.access.viewableTrip(999L, OWNER_ID));
    }

    @Test
    void notSignedInGets401() {
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
            () -> f.access.viewableTrip(TRIP_ID, null));

        assertEquals(HttpStatus.UNAUTHORIZED, e.getStatusCode());
        verifyNoInteractions(f.tripRepository, f.memberRepository);
    }

    @Test
    void roleOfReportsEachUsersRole() {
        assertEquals(TripRole.OWNER, f.access.roleOf(f.trip, OWNER_ID));
        assertEquals(TripRole.EDITOR, f.access.roleOf(f.trip, EDITOR_ID));
        assertEquals(TripRole.VIEWER, f.access.roleOf(f.trip, VIEWER_ID));
        assertNull(f.access.roleOf(f.trip, STRANGER_ID));
        assertNull(f.access.roleOf(f.trip, null));
    }
}
