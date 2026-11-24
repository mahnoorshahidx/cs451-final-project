package com.tripbudgetplanner.backend.member;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import org.springframework.test.util.ReflectionTestUtils;

import com.tripbudgetplanner.backend.trip.Trip;
import com.tripbudgetplanner.backend.trip.TripAccessService;
import com.tripbudgetplanner.backend.trip.TripRepository;
import com.tripbudgetplanner.backend.user.User;

// Test setup shared by the UC9 tests:
// trip 10 is owned by Olivia (1). Eddie (2) is an EDITOR, Vera (3) is a VIEWER,
// and Sam (4) has no access. Repositories are mocks; TripAccessService is real.
public class SharedTripFixture {

    public static final Long TRIP_ID = 10L;
    public static final Long OWNER_ID = 1L;
    public static final Long EDITOR_ID = 2L;
    public static final Long VIEWER_ID = 3L;
    public static final Long STRANGER_ID = 4L;

    public final TripRepository tripRepository = mock(TripRepository.class);
    public final TripMemberRepository memberRepository = mock(TripMemberRepository.class);
    public final TripAccessService access = new TripAccessService(tripRepository, memberRepository);

    public final User owner = user(OWNER_ID, "Olivia Owner", "owner@example.com");
    public final User editor = user(EDITOR_ID, "Eddie Editor", "editor@example.com");
    public final User viewer = user(VIEWER_ID, "Vera Viewer", "viewer@example.com");
    public final User stranger = user(STRANGER_ID, "Sam Stranger", "sam@example.com");

    public final Trip trip = new Trip();
    public final TripMember editorMember;
    public final TripMember viewerMember;

    public SharedTripFixture() {
        ReflectionTestUtils.setField(trip, "id", TRIP_ID);
        trip.setName("Tokyo Trip");
        trip.setDestination("Tokyo");
        trip.setStartDate(LocalDate.of(2027, 6, 1));
        trip.setEndDate(LocalDate.of(2027, 6, 10));
        trip.setNumberOfTravelers(3);
        trip.setCurrency("USD");
        trip.setUser(owner);

        Instant joined = Instant.parse("2026-11-01T12:00:00Z");
        editorMember = new TripMember(trip, editor, TripRole.EDITOR, joined, OWNER_ID);
        viewerMember = new TripMember(trip, viewer, TripRole.VIEWER, joined, OWNER_ID);

        // The owner is found with the existing owner query; members through trip_members
        when(tripRepository.findByIdAndUserId(TRIP_ID, OWNER_ID)).thenReturn(Optional.of(trip));
        when(tripRepository.findById(TRIP_ID)).thenReturn(Optional.of(trip));
        when(memberRepository.findByTripIdAndUserId(TRIP_ID, EDITOR_ID)).thenReturn(Optional.of(editorMember));
        when(memberRepository.findByTripIdAndUserId(TRIP_ID, VIEWER_ID)).thenReturn(Optional.of(viewerMember));
        when(memberRepository.existsByTripIdAndUserId(TRIP_ID, EDITOR_ID)).thenReturn(true);
        when(memberRepository.existsByTripIdAndUserId(TRIP_ID, VIEWER_ID)).thenReturn(true);
    }

    public static User user(Long id, String name, String email) {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", id);
        user.setName(name);
        user.setEmail(email);
        return user;
    }
}
