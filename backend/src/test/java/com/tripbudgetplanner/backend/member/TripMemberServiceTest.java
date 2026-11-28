package com.tripbudgetplanner.backend.member;

import static com.tripbudgetplanner.backend.member.SharedTripFixture.*;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.tripbudgetplanner.backend.member.dto.InvitationPreviewResponse;
import com.tripbudgetplanner.backend.member.dto.InvitationResponse;
import com.tripbudgetplanner.backend.member.dto.InviteRequest;
import com.tripbudgetplanner.backend.member.dto.JoinedTripResponse;
import com.tripbudgetplanner.backend.member.dto.ReceivedInvitationResponse;
import com.tripbudgetplanner.backend.member.dto.TripMemberResponse;
import com.tripbudgetplanner.backend.member.exception.InvitationCooldownException;
import com.tripbudgetplanner.backend.member.exception.InvitationGoneException;
import com.tripbudgetplanner.backend.member.exception.SharingConflictException;
import com.tripbudgetplanner.backend.member.exception.SharingNotFoundException;
import com.tripbudgetplanner.backend.trip.exception.TripAccessDeniedException;
import com.tripbudgetplanner.backend.trip.exception.TripNotFoundException;
import com.tripbudgetplanner.backend.user.User;
import com.tripbudgetplanner.backend.user.UserRepository;

// UC9: members, invitations, roles
class TripMemberServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-24T12:00:00Z");

    private SharedTripFixture f;
    private TripInvitationRepository invitations;
    private UserRepository users;
    private FixedClock clock;
    private TripMemberService service;

    // What the service saved, for checking afterwards
    private final List<TripInvitation> savedInvitations = new ArrayList<>();
    private final List<TripMember> savedMembers = new ArrayList<>();

    @BeforeEach
    void setUp() {
        f = new SharedTripFixture();
        invitations = mock(TripInvitationRepository.class);
        users = mock(UserRepository.class);
        clock = new FixedClock(NOW);
        service = new TripMemberService(f.access, f.memberRepository, invitations, users, clock);

        for (User u : List.of(f.owner, f.editor, f.viewer, f.stranger)) {
            when(users.findById(u.getId())).thenReturn(Optional.of(u));
            when(users.findByEmail(u.getEmail())).thenReturn(Optional.of(u));
        }
        when(f.memberRepository.findByTripIdOrderByJoinedAtAsc(TRIP_ID))
                .thenReturn(List.of(f.editorMember, f.viewerMember));

        // Null-safe because a test that re-stubs with when(...saveAndFlush(any()))
        // calls this with null
        when(invitations.saveAndFlush(any())).thenAnswer(call -> {
            TripInvitation invitation = call.getArgument(0);
            if (invitation == null) {
                return null;
            }
            if (invitation.getId() == null) {
                ReflectionTestUtils.setField(invitation, "id", 70L + savedInvitations.size());
            }
            savedInvitations.add(invitation);
            return invitation;
        });
        when(f.memberRepository.saveAndFlush(any())).thenAnswer(call -> {
            TripMember member = call.getArgument(0);
            if (member != null) {
                savedMembers.add(member);
            }
            return member;
        });
        when(invitations.markAcceptedIfPending(any(), any(), any(), any(), any())).thenReturn(1);
    }

    // ---------- Members ----------

    @Test
    void ownerSeesEveryoneWithEmails() {
        List<TripMemberResponse> list = service.listMembers(TRIP_ID, OWNER_ID);

        assertEquals(3, list.size());
        assertEquals("OWNER", list.get(0).role());
        assertEquals("owner@example.com", list.get(0).email());
        assertTrue(list.get(0).you());
        assertNull(list.get(0).joinedAt());
        assertEquals("editor@example.com", list.get(1).email());
        assertEquals("EDITOR", list.get(1).role());
        assertEquals("VIEWER", list.get(2).role());
    }

    @Test
    void membersSeeNamesAndRolesButNoEmails() {
        List<TripMemberResponse> list = service.listMembers(TRIP_ID, VIEWER_ID);

        assertEquals(3, list.size());
        assertEquals("Olivia Owner", list.get(0).name());
        list.forEach(m -> assertNull(m.email(), "no emails for non-owners"));
        assertTrue(list.get(2).you());
    }

    @Test
    void strangerCannotListMembers() {
        assertThrows(TripNotFoundException.class, () -> service.listMembers(TRIP_ID, STRANGER_ID));
    }

    @Test
    void ownerChangesAMembersRole() {
        TripMemberResponse result = service.changeRole(TRIP_ID, EDITOR_ID, "VIEWER", OWNER_ID);

        assertEquals("VIEWER", result.role());
        assertEquals(TripRole.VIEWER, f.editorMember.getRole());
        verify(f.memberRepository).save(f.editorMember);
    }

    @Test
    void ownersRoleCannotBeChanged() {
        assertThrows(SharingConflictException.class,
                () -> service.changeRole(TRIP_ID, OWNER_ID, "VIEWER", OWNER_ID));
    }

    @Test
    void onlyTheOwnerCanChangeRoles() {
        assertThrows(TripAccessDeniedException.class,
                () -> service.changeRole(TRIP_ID, VIEWER_ID, "EDITOR", EDITOR_ID));
        assertThrows(TripAccessDeniedException.class,
                () -> service.changeRole(TRIP_ID, EDITOR_ID, "VIEWER", VIEWER_ID));
        assertEquals(TripRole.EDITOR, f.editorMember.getRole());
    }

    @Test
    void changingAnUnknownMemberIs404() {
        assertThrows(SharingNotFoundException.class,
                () -> service.changeRole(TRIP_ID, STRANGER_ID, "EDITOR", OWNER_ID));
    }

    @Test
    void roleMustBeEditorOrViewer() {
        assertThrows(IllegalArgumentException.class,
                () -> service.changeRole(TRIP_ID, EDITOR_ID, "OWNER", OWNER_ID));
    }

    @Test
    void ownerRemovesAMember() {
        service.removeMember(TRIP_ID, VIEWER_ID, OWNER_ID);

        verify(f.memberRepository).delete(f.viewerMember);
    }

    @Test
    void ownerCannotBeRemovedByAnyone() {
        assertThrows(SharingConflictException.class, () -> service.removeMember(TRIP_ID, OWNER_ID, OWNER_ID));
        assertThrows(SharingConflictException.class, () -> service.removeMember(TRIP_ID, OWNER_ID, EDITOR_ID));
        verify(f.memberRepository, never()).delete(any());
    }

    @Test
    void memberCanLeave() {
        service.removeMember(TRIP_ID, VIEWER_ID, VIEWER_ID);

        verify(f.memberRepository).delete(f.viewerMember);
    }

    @Test
    void editorCannotRemoveOthers() {
        assertThrows(TripAccessDeniedException.class, () -> service.removeMember(TRIP_ID, VIEWER_ID, EDITOR_ID));
        verify(f.memberRepository, never()).delete(any());
    }

    @Test
    void strangerCannotRemoveAnyone() {
        assertThrows(TripNotFoundException.class, () -> service.removeMember(TRIP_ID, VIEWER_ID, STRANGER_ID));
    }

    // ---------- Creating invitations ----------

    @Test
    void ownerInvitesByEmail() {
        InvitationResponse result = service.invite(TRIP_ID, new InviteRequest("  New.Person@Example.COM ", "EDITOR"),
                OWNER_ID);

        assertEquals("EMAIL", result.type());
        assertEquals("new.person@example.com", result.email(), "normalized like AuthService");
        assertEquals("EDITOR", result.role());
        assertEquals("PENDING", result.status());
        assertEquals(NOW.plus(Duration.ofDays(7)), result.expiresAt());
        assertEquals(NOW.plus(Duration.ofMinutes(10)), result.resendAvailableAt());

        // The token is returned once; only its hash is stored
        assertNotNull(result.token());
        assertEquals("/invite/" + result.token(), result.invitePath());
        TripInvitation saved = savedInvitations.get(0);
        assertEquals(InvitationTokens.hash(result.token()), saved.getTokenHash());
        assertNotEquals(result.token(), saved.getTokenHash());
        assertEquals(OWNER_ID, saved.getInvitedByUserId());
    }

    @Test
    void ownerCreatesAShareableLink() {
        InvitationResponse result = service.invite(TRIP_ID, new InviteRequest(null, "VIEWER"), OWNER_ID);

        assertEquals("LINK", result.type());
        assertNull(result.email());
        assertNotNull(result.token());
    }

    @Test
    void expiredInvitationsAreClearedBeforeCheckingForDuplicates() {
        service.invite(TRIP_ID, new InviteRequest("new@example.com", "VIEWER"), OWNER_ID);

        verify(invitations).expireOldForEmail(TRIP_ID, "new@example.com", NOW,
                InvitationStatus.PENDING, InvitationStatus.EXPIRED);
        assertEquals(1, savedInvitations.size());
    }

    @Test
    void duplicatePendingInvitationIs409() {
        when(invitations.existsByTripIdAndInvitedEmailAndStatus(TRIP_ID, "new@example.com", InvitationStatus.PENDING))
                .thenReturn(true);

        assertThrows(SharingConflictException.class,
                () -> service.invite(TRIP_ID, new InviteRequest("NEW@example.com", "VIEWER"), OWNER_ID));
        assertTrue(savedInvitations.isEmpty());
    }

    @Test
    void databaseDuplicateBecomes409NotARawError() {
        when(invitations.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("uq_trip_invitations_pending_email"));

        assertThrows(SharingConflictException.class,
                () -> service.invite(TRIP_ID, new InviteRequest("new@example.com", "VIEWER"), OWNER_ID));
    }

    @Test
    void cannotInviteAnExistingMember() {
        assertThrows(SharingConflictException.class,
                () -> service.invite(TRIP_ID, new InviteRequest("Editor@Example.com", "VIEWER"), OWNER_ID));
    }

    @Test
    void ownerCannotInviteThemselves() {
        assertThrows(SharingConflictException.class,
                () -> service.invite(TRIP_ID, new InviteRequest("owner@example.com", "EDITOR"), OWNER_ID));
    }

    @Test
    void onlyTheOwnerCanInvite() {
        InviteRequest request = new InviteRequest("new@example.com", "VIEWER");

        assertThrows(TripAccessDeniedException.class, () -> service.invite(TRIP_ID, request, EDITOR_ID));
        assertThrows(TripAccessDeniedException.class, () -> service.invite(TRIP_ID, request, VIEWER_ID));
        assertThrows(TripNotFoundException.class, () -> service.invite(TRIP_ID, request, STRANGER_ID));
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.invite(TRIP_ID, request, null));
        assertEquals(HttpStatus.UNAUTHORIZED, e.getStatusCode());
        assertTrue(savedInvitations.isEmpty());
    }

    // ---------- Listing, resending, revoking ----------

    @Test
    void ownerListsPendingInvitationsWithoutTokens() {
        TripInvitation pending = pendingInvitation("a@example.com", NOW);
        when(invitations.findByTripIdAndStatusOrderByCreatedAtDesc(TRIP_ID, InvitationStatus.PENDING))
                .thenReturn(List.of(pending));

        List<InvitationResponse> list = service.listTripInvitations(TRIP_ID, OWNER_ID);

        verify(invitations).expireOldForTrip(TRIP_ID, NOW, InvitationStatus.PENDING, InvitationStatus.EXPIRED);
        assertEquals(1, list.size());
        assertNull(list.get(0).token());
        assertNull(list.get(0).invitePath());
    }

    @Test
    void resendWithinTenMinutesIs429() {
        TripInvitation invitation = pendingInvitation("a@example.com", NOW.minus(Duration.ofMinutes(4)));

        InvitationCooldownException e = assertThrows(InvitationCooldownException.class,
                () -> service.resend(TRIP_ID, invitation.getId(), OWNER_ID));

        assertEquals(360L, e.getRetryAfterSeconds());
        assertTrue(savedInvitations.isEmpty());
    }

    @Test
    void resendAfterCooldownMakesANewLinkAndExpiry() {
        TripInvitation invitation = pendingInvitation("a@example.com", NOW.minus(Duration.ofMinutes(11)));
        String oldHash = invitation.getTokenHash();

        InvitationResponse result = service.resend(TRIP_ID, invitation.getId(), OWNER_ID);

        assertNotEquals(oldHash, invitation.getTokenHash(), "the old link stops working");
        assertEquals(InvitationTokens.hash(result.token()), invitation.getTokenHash());
        assertEquals(NOW, invitation.getLastSentAt());
        assertEquals(NOW.plus(Duration.ofDays(7)), invitation.getExpiresAt());
    }

    @Test
    void resendRevivesAnExpiredInvitation() {
        TripInvitation invitation = pendingInvitation("a@example.com", NOW.minus(Duration.ofDays(8)));
        invitation.setStatus(InvitationStatus.EXPIRED);

        service.resend(TRIP_ID, invitation.getId(), OWNER_ID);

        assertEquals(InvitationStatus.PENDING, invitation.getStatus());
        assertEquals(NOW.plus(Duration.ofDays(7)), invitation.getExpiresAt());
    }

    @Test
    void cannotReviveWhenANewerInvitationIsPending() {
        TripInvitation invitation = pendingInvitation("a@example.com", NOW.minus(Duration.ofDays(8)));
        invitation.setStatus(InvitationStatus.EXPIRED);
        when(invitations.existsByTripIdAndInvitedEmailAndStatus(TRIP_ID, "a@example.com", InvitationStatus.PENDING))
                .thenReturn(true);

        assertThrows(SharingConflictException.class, () -> service.resend(TRIP_ID, invitation.getId(), OWNER_ID));
        assertEquals(InvitationStatus.EXPIRED, invitation.getStatus());
    }

    @Test
    void cannotResendAnAcceptedInvitation() {
        TripInvitation invitation = pendingInvitation("a@example.com", NOW.minus(Duration.ofHours(1)));
        invitation.setStatus(InvitationStatus.ACCEPTED);

        assertThrows(SharingConflictException.class, () -> service.resend(TRIP_ID, invitation.getId(), OWNER_ID));
    }

    @Test
    void ownerRevokesAnInvitation() {
        TripInvitation invitation = pendingInvitation("a@example.com", NOW);

        service.revoke(TRIP_ID, invitation.getId(), OWNER_ID);

        assertEquals(InvitationStatus.REVOKED, invitation.getStatus());
        assertEquals(NOW, invitation.getRespondedAt());
        verify(invitations).save(invitation);
    }

    @Test
    void cannotRevokeAnAcceptedInvitation() {
        TripInvitation invitation = pendingInvitation("a@example.com", NOW);
        invitation.setStatus(InvitationStatus.ACCEPTED);

        assertThrows(SharingConflictException.class, () -> service.revoke(TRIP_ID, invitation.getId(), OWNER_ID));
    }

    @Test
    void editorCannotResendOrRevoke() {
        TripInvitation invitation = pendingInvitation("a@example.com", NOW.minus(Duration.ofHours(1)));

        assertThrows(TripAccessDeniedException.class, () -> service.resend(TRIP_ID, invitation.getId(), EDITOR_ID));
        assertThrows(TripAccessDeniedException.class, () -> service.revoke(TRIP_ID, invitation.getId(), EDITOR_ID));
        assertEquals(InvitationStatus.PENDING, invitation.getStatus());
    }

    @Test
    void invitationFromAnotherTripIs404() {
        assertThrows(SharingNotFoundException.class, () -> service.revoke(TRIP_ID, 999L, OWNER_ID));
    }

    // ---------- Accepting ----------

    @Test
    void anySignedInUserCanAcceptALinkOnce() {
        String token = linkInvitation(TripRole.VIEWER, NOW);

        JoinedTripResponse joined = service.acceptByToken(token, STRANGER_ID);

        assertEquals(TRIP_ID, joined.tripId());
        assertEquals("VIEWER", joined.role());
        assertEquals(1, savedMembers.size());
        assertEquals(STRANGER_ID, savedMembers.get(0).getUser().getId());
        assertEquals(TripRole.VIEWER, savedMembers.get(0).getRole());
    }

    @Test
    void emailInvitationIsAcceptedByTheMatchingUser() {
        TripInvitation invitation = pendingInvitation("sam@example.com", NOW);
        f.stranger.setEmail("Sam@Example.com"); // compared after normalizing

        JoinedTripResponse joined = service.acceptById(invitation.getId(), STRANGER_ID);

        assertEquals("EDITOR", joined.role());
        assertEquals(1, savedMembers.size());
    }

    @Test
    void wrongUserCannotAcceptAnEmailInvitationLink() {
        String token = "email-invite-token";
        TripInvitation invitation = pendingInvitation("someone.else@example.com", NOW);
        invitation.setTokenHash(InvitationTokens.hash(token));
        when(invitations.findByTokenHash(InvitationTokens.hash(token))).thenReturn(Optional.of(invitation));

        assertThrows(TripAccessDeniedException.class, () -> service.acceptByToken(token, STRANGER_ID));
        assertTrue(savedMembers.isEmpty());
        assertEquals(InvitationStatus.PENDING, invitation.getStatus());
    }

    @Test
    void wrongUserCannotSeeOrAcceptSomeoneElsesInvitationById() {
        TripInvitation invitation = pendingInvitation("someone.else@example.com", NOW);

        assertThrows(SharingNotFoundException.class, () -> service.acceptById(invitation.getId(), STRANGER_ID));
        assertThrows(SharingNotFoundException.class, () -> service.decline(invitation.getId(), STRANGER_ID));
    }

    @Test
    void expiredInvitationIs410AndTheStatusIsSaved() {
        String token = linkInvitation(TripRole.VIEWER, NOW.minus(Duration.ofDays(7))); // expires exactly now
        TripInvitation invitation = lastLink;

        assertThrows(InvitationGoneException.class, () -> service.acceptByToken(token, STRANGER_ID));

        assertEquals(InvitationStatus.EXPIRED, invitation.getStatus());
        verify(invitations).save(invitation);
        assertTrue(savedMembers.isEmpty());
    }

    @Test
    void the410ForExpiredInvitationsDoesNotRollBackTheStatusChange() throws Exception {
        // These methods save EXPIRED and then answer 410, so that exception must not
        // roll back
        for (String method : List.of("acceptById", "acceptByToken", "decline", "preview")) {
            Transactional tx = java.util.Arrays.stream(TripMemberService.class.getMethods())
                    .filter(m -> m.getName().equals(method))
                    .findFirst().orElseThrow()
                    .getAnnotation(Transactional.class);
            assertNotNull(tx, method + " is transactional");
            assertArrayEquals(new Class<?>[] { InvitationGoneException.class }, tx.noRollbackFor(), method);
        }
    }

    @Test
    void revokedAndDeclinedInvitationsAre410() {
        String revoked = linkInvitation(TripRole.VIEWER, NOW);
        lastLink.setStatus(InvitationStatus.REVOKED);
        assertThrows(InvitationGoneException.class, () -> service.acceptByToken(revoked, STRANGER_ID));

        TripInvitation declined = pendingInvitation("sam@example.com", NOW);
        declined.setStatus(InvitationStatus.DECLINED);
        assertThrows(InvitationGoneException.class, () -> service.acceptById(declined.getId(), STRANGER_ID));
        assertTrue(savedMembers.isEmpty());
    }

    @Test
    void secondPersonUsingTheSameLinkGets410() {
        // Another request accepted it first: the conditional UPDATE changes 0 rows
        String token = linkInvitation(TripRole.VIEWER, NOW);
        when(invitations.markAcceptedIfPending(any(), any(), any(), any(), any())).thenReturn(0);

        assertThrows(InvitationGoneException.class, () -> service.acceptByToken(token, STRANGER_ID));
        assertTrue(savedMembers.isEmpty(), "no second membership");
    }

    @Test
    void existingMemberCannotAcceptAgain() {
        String token = linkInvitation(TripRole.EDITOR, NOW);

        assertThrows(SharingConflictException.class, () -> service.acceptByToken(token, VIEWER_ID));
        verify(invitations, never()).markAcceptedIfPending(any(), any(), any(), any(), any());
    }

    @Test
    void ownerCannotAcceptTheirOwnLink() {
        String token = linkInvitation(TripRole.EDITOR, NOW);

        assertThrows(SharingConflictException.class, () -> service.acceptByToken(token, OWNER_ID));
    }

    @Test
    void membershipRaceBecomes409NotARawError() {
        String token = linkInvitation(TripRole.VIEWER, NOW);
        when(f.memberRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("uq_trip_members_trip_user"));

        assertThrows(SharingConflictException.class, () -> service.acceptByToken(token, STRANGER_ID));
    }

    @Test
    void unknownTokenIs404() {
        assertThrows(SharingNotFoundException.class, () -> service.acceptByToken("not-a-real-token", STRANGER_ID));
        assertThrows(SharingNotFoundException.class, () -> service.preview("not-a-real-token"));
    }

    @Test
    void acceptingRequiresSignIn() {
        String token = linkInvitation(TripRole.VIEWER, NOW);

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.acceptByToken(token, null));
        assertEquals(HttpStatus.UNAUTHORIZED, e.getStatusCode());
    }

    // ---------- Declining and the invited user's list ----------

    @Test
    void invitedUserDeclines() {
        TripInvitation invitation = pendingInvitation("sam@example.com", NOW);

        when(invitations.markDeclinedIfPending(
                invitation.getId(),
                NOW,
                InvitationStatus.PENDING,
                InvitationStatus.DECLINED)).thenReturn(1);

        service.decline(invitation.getId(), STRANGER_ID);

        verify(invitations).markDeclinedIfPending(
                invitation.getId(),
                NOW,
                InvitationStatus.PENDING,
                InvitationStatus.DECLINED);

        verify(invitations, never()).save(invitation);
    }

    @Test
    void decliningAnExpiredInvitationIs410AndSavesExpired() {
        TripInvitation invitation = pendingInvitation("sam@example.com", NOW.minus(Duration.ofDays(8)));

        assertThrows(InvitationGoneException.class, () -> service.decline(invitation.getId(), STRANGER_ID));
        assertEquals(InvitationStatus.EXPIRED, invitation.getStatus());
        verify(invitations).save(invitation);
    }

    @Test
    void userSeesTheirPendingInvitations() {
        f.stranger.setEmail("Sam@Example.com");
        TripInvitation invitation = pendingInvitation("sam@example.com", NOW);
        when(invitations.findByInvitedEmailAndStatusOrderByCreatedAtDesc("sam@example.com", InvitationStatus.PENDING))
                .thenReturn(List.of(invitation));

        List<ReceivedInvitationResponse> list = service.myInvitations(STRANGER_ID);

        verify(invitations).expireOldForRecipient("sam@example.com", NOW,
                InvitationStatus.PENDING, InvitationStatus.EXPIRED);
        assertEquals(1, list.size());
        assertEquals("Tokyo Trip", list.get(0).tripName());
        assertEquals("Olivia Owner", list.get(0).invitedBy());
    }

    // ---------- Public preview ----------

    @Test
    void previewShowsOnlyMinimalInformation() {
        String token = linkInvitation(TripRole.EDITOR, NOW);

        InvitationPreviewResponse preview = service.preview(token);

        assertEquals("Tokyo Trip", preview.tripName());
        assertEquals("Tokyo", preview.destination());
        assertEquals("Olivia", preview.invitedBy(), "first name only");
        assertEquals("EDITOR", preview.role());
        assertFalse(preview.emailInvite());
        // The record has no fields for dates, emails, IDs or money
        assertEquals(6, InvitationPreviewResponse.class.getRecordComponents().length);
    }

    @Test
    void previewOfAnExpiredLinkIs410AndSavesExpired() {
        String token = linkInvitation(TripRole.EDITOR, NOW.minus(Duration.ofDays(9)));

        assertThrows(InvitationGoneException.class, () -> service.preview(token));
        assertEquals(InvitationStatus.EXPIRED, lastLink.getStatus());
        verify(invitations).save(lastLink);
    }

    // ---------- Helpers ----------

    private TripInvitation lastLink;
    private long nextId = 500;

    // A pending email invitation (EDITOR) sent at the given time, findable by ID
    private TripInvitation pendingInvitation(String email, Instant sentAt) {
        TripInvitation invitation = new TripInvitation(f.trip, email, TripRole.EDITOR, OWNER_ID);
        ReflectionTestUtils.setField(invitation, "id", nextId++);
        invitation.setTokenHash(InvitationTokens.hash(InvitationTokens.newToken()));
        invitation.setCreatedAt(sentAt);
        invitation.setLastSentAt(sentAt);
        invitation.setExpiresAt(sentAt.plus(TripMemberService.INVITATION_LIFETIME));
        when(invitations.findById(invitation.getId())).thenReturn(Optional.of(invitation));
        when(invitations.findByIdAndTripId(invitation.getId(), TRIP_ID)).thenReturn(Optional.of(invitation));
        return invitation;
    }

    // A pending shareable link sent at the given time. Returns its token.
    private String linkInvitation(TripRole role, Instant sentAt) {
        String token = InvitationTokens.newToken();
        TripInvitation invitation = new TripInvitation(f.trip, null, role, OWNER_ID);
        ReflectionTestUtils.setField(invitation, "id", nextId++);
        invitation.setTokenHash(InvitationTokens.hash(token));
        invitation.setCreatedAt(sentAt);
        invitation.setLastSentAt(sentAt);
        invitation.setExpiresAt(sentAt.plus(TripMemberService.INVITATION_LIFETIME));
        when(invitations.findByTokenHash(InvitationTokens.hash(token))).thenReturn(Optional.of(invitation));
        lastLink = invitation;
        return token;
    }

    // A clock fixed at NOW, so expiry and cooldown checks are predictable
    private static class FixedClock extends Clock {
        private final Instant now;

        FixedClock(Instant now) {
            this.now = now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
