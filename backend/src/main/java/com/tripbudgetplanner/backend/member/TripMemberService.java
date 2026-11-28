package com.tripbudgetplanner.backend.member;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
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
import com.tripbudgetplanner.backend.trip.Trip;
import com.tripbudgetplanner.backend.trip.TripAccessService;
import com.tripbudgetplanner.backend.trip.TripAccessService.TripAccess;
import com.tripbudgetplanner.backend.trip.exception.TripAccessDeniedException;
import com.tripbudgetplanner.backend.user.User;
import com.tripbudgetplanner.backend.user.UserRepository;

// UC9: invite people to a trip, manage members and their roles.
// No emails or texts are sent: invitations show up in the app (GET /api/invitations)
// or are shared as a link by the owner.
@Service
public class TripMemberService {

    static final Duration INVITATION_LIFETIME = Duration.ofDays(7);
    static final Duration RESEND_COOLDOWN = Duration.ofMinutes(10);

    private final TripAccessService tripAccess;
    private final TripMemberRepository members;
    private final TripInvitationRepository invitations;
    private final UserRepository users;
    private final Clock clock;

    // Used by Spring
    @Autowired
    public TripMemberService(TripAccessService tripAccess, TripMemberRepository members,
            TripInvitationRepository invitations, UserRepository users) {
        this(tripAccess, members, invitations, users, Clock.systemUTC());
    }

    // Used by tests, which pass a fake clock to test expiry and the cooldown
    TripMemberService(TripAccessService tripAccess, TripMemberRepository members,
            TripInvitationRepository invitations, UserRepository users, Clock clock) {
        this.tripAccess = tripAccess;
        this.members = members;
        this.invitations = invitations;
        this.users = users;
        this.clock = clock;
    }

    // Same rule as AuthService and UserService: trim and lowercase
    static String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }

    // ---------- Members ----------

    // Everyone on the trip can see names and roles. Only the owner sees emails.
    @Transactional(readOnly = true)
    public List<TripMemberResponse> listMembers(Long tripId, Long userId) {
        TripAccess access = tripAccess.requireRole(tripId, userId, TripRole.VIEWER);
        boolean showEmails = access.role() == TripRole.OWNER;
        User owner = access.trip().getUser();

        List<TripMemberResponse> result = new ArrayList<>();
        result.add(new TripMemberResponse(owner.getId(), owner.getName(),
                showEmails ? owner.getEmail() : null, TripRole.OWNER.name(), null, owner.getId().equals(userId)));

        for (TripMember member : members.findByTripIdOrderByJoinedAtAsc(tripId)) {
            User user = member.getUser();
            result.add(new TripMemberResponse(user.getId(), user.getName(),
                    showEmails ? user.getEmail() : null, member.getRole().name(), member.getJoinedAt(),
                    user.getId().equals(userId)));
        }
        return result;
    }

    // Owner only. Switches a member between EDITOR and VIEWER.
    @Transactional
    public TripMemberResponse changeRole(Long tripId, Long memberUserId, String role, Long userId) {
        Trip trip = tripAccess.ownedTrip(tripId, userId);
        if (trip.getUser().getId().equals(memberUserId)) {
            throw new SharingConflictException("The trip owner's role can't be changed.");
        }

        TripMember member = members.findByTripIdAndUserId(tripId, memberUserId)
                .orElseThrow(() -> new SharingNotFoundException("Member not found"));
        member.setRole(memberRole(role));
        members.save(member);

        User user = member.getUser();
        return new TripMemberResponse(user.getId(), user.getName(), user.getEmail(),
                member.getRole().name(), member.getJoinedAt(), false);
    }

    // The owner can remove any member. A member can remove themselves (leave the
    // trip).
    // Nobody can remove the owner.
    @Transactional
    public void removeMember(Long tripId, Long memberUserId, Long userId) {
        TripAccess access = tripAccess.requireRole(tripId, userId, TripRole.VIEWER);
        if (access.trip().getUser().getId().equals(memberUserId)) {
            throw new SharingConflictException("The trip owner can't be removed.");
        }

        boolean leaving = memberUserId.equals(userId);
        if (!leaving && access.role() != TripRole.OWNER) {
            throw new TripAccessDeniedException("Only the trip owner can remove other members.");
        }

        TripMember member = members.findByTripIdAndUserId(tripId, memberUserId)
                .orElseThrow(() -> new SharingNotFoundException("Member not found"));
        members.delete(member);
    }

    // ---------- Invitations: owner side ----------

    // Owner only. With an email: an email invitation. Without: a single-use link.
    // The token is returned only here and when resending.
    @Transactional
    public InvitationResponse invite(Long tripId, InviteRequest request, Long userId) {
        Trip trip = tripAccess.ownedTrip(tripId, userId);
        TripRole role = memberRole(request.role());
        Instant now = clock.instant();

        String email = request.email() == null || request.email().isBlank()
                ? null
                : normalizeEmail(request.email());

        if (email != null) {
            checkCanInviteEmail(trip, email);

            // Expired invitations must not block a new one for the same person
            invitations.expireOldForEmail(tripId, email, now, InvitationStatus.PENDING, InvitationStatus.EXPIRED);
            if (invitations.existsByTripIdAndInvitedEmailAndStatus(tripId, email, InvitationStatus.PENDING)) {
                throw new SharingConflictException(
                        "An invitation is already pending for this email. Resend it instead.");
            }
        }

        TripInvitation invitation = new TripInvitation(trip, email, role, userId);
        invitation.setCreatedAt(now);
        String token = startSending(invitation, now);
        return toOwnerResponse(saveInvitation(invitation), token);
    }

    // Owner only. Pending invitations for the trip (tokens are never shown here).
    @Transactional
    public List<InvitationResponse> listTripInvitations(Long tripId, Long userId) {
        tripAccess.ownedTrip(tripId, userId);
        invitations.expireOldForTrip(tripId, clock.instant(), InvitationStatus.PENDING, InvitationStatus.EXPIRED);
        return invitations.findByTripIdAndStatusOrderByCreatedAtDesc(tripId, InvitationStatus.PENDING)
                .stream().map(i -> toOwnerResponse(i, null)).toList();
    }

    // Owner only. Makes a new token (the old link stops working) and a new 7-day
    // expiry.
    // Works for PENDING and EXPIRED invitations, at most once every 10 minutes.
    @Transactional
    public InvitationResponse resend(Long tripId, Long invitationId, Long userId) {
        Trip trip = tripAccess.ownedTrip(tripId, userId);
        TripInvitation invitation = invitations.findByIdAndTripId(invitationId, tripId)
                .orElseThrow(() -> new SharingNotFoundException("Invitation not found"));

        InvitationStatus status = invitation.getStatus();
        if (status != InvitationStatus.PENDING && status != InvitationStatus.EXPIRED) {
            throw new SharingConflictException("This invitation was already " + status.name().toLowerCase()
                    + " and can't be resent. Create a new invitation instead.");
        }

        Instant now = clock.instant();
        Instant resendAt = invitation.getLastSentAt().plus(RESEND_COOLDOWN);
        if (now.isBefore(resendAt)) {
            throw new InvitationCooldownException(Duration.between(now, resendAt).toSeconds());
        }

        if (invitation.isEmailInvite()) {
            checkCanInviteEmail(trip, invitation.getInvitedEmail());
            // Reviving an expired invitation: make sure there isn't already a newer pending
            // one
            if (status == InvitationStatus.EXPIRED && invitations.existsByTripIdAndInvitedEmailAndStatus(
                    tripId, invitation.getInvitedEmail(), InvitationStatus.PENDING)) {
                throw new SharingConflictException("A newer invitation is already pending for this email.");
            }
        }

        invitation.setStatus(InvitationStatus.PENDING);
        String token = startSending(invitation, now);
        return toOwnerResponse(saveInvitation(invitation), token);
    }

    // Owner only. A revoked invitation can't be accepted.
    @Transactional
    public void revoke(Long tripId, Long invitationId, Long userId) {
        tripAccess.ownedTrip(tripId, userId);
        TripInvitation invitation = invitations.findByIdAndTripId(invitationId, tripId)
                .orElseThrow(() -> new SharingNotFoundException("Invitation not found"));

        InvitationStatus status = invitation.getStatus();
        if (status != InvitationStatus.PENDING && status != InvitationStatus.EXPIRED) {
            throw new SharingConflictException("This invitation was already " + status.name().toLowerCase() + ".");
        }
        invitation.setStatus(InvitationStatus.REVOKED);
        invitation.setRespondedAt(clock.instant());
        invitations.save(invitation);
    }

    // ---------- Invitations: invited user's side ----------

    // Pending email invitations for the signed-in user
    @Transactional
    public List<ReceivedInvitationResponse> myInvitations(Long userId) {
        String email = normalizeEmail(currentUser(userId).getEmail());
        invitations.expireOldForRecipient(email, clock.instant(), InvitationStatus.PENDING, InvitationStatus.EXPIRED);

        return invitations.findByInvitedEmailAndStatusOrderByCreatedAtDesc(email, InvitationStatus.PENDING)
                .stream()
                .map(i -> {
                    Trip trip = i.getTrip();
                    return new ReceivedInvitationResponse(i.getId(), trip.getId(), trip.getName(),
                            trip.getDestination(), trip.getStartDate(), trip.getEndDate(), i.getRole().name(),
                            inviterName(i), i.getExpiresAt());
                })
                .toList();
    }

    // Accept from the in-app list. Only the invited email's user can see or accept
    // it.
    // noRollbackFor: when the invitation has expired, the EXPIRED status is still
    // saved
    // even though we answer with 410.
    @Transactional(noRollbackFor = InvitationGoneException.class)
    public JoinedTripResponse acceptById(Long invitationId, Long userId) {
        User user = currentUser(userId);
        return accept(myEmailInvitation(invitationId, user), user);
    }

    // Accept from a link. Email invitations still require the matching signed-in
    // user.
    @Transactional(noRollbackFor = InvitationGoneException.class)
    public JoinedTripResponse acceptByToken(String token, Long userId) {
        User user = currentUser(userId);
        return accept(byToken(token), user);
    }

    // Decline from the in-app list (email invitations only)
    @Transactional(noRollbackFor = InvitationGoneException.class)
    public void decline(Long invitationId, Long userId) {
        TripInvitation invitation = myEmailInvitation(invitationId, currentUser(userId));

        declinePendingInvitation(invitation);
    }

    // UC9: Decline an invitation using its link token
    @Transactional(noRollbackFor = InvitationGoneException.class)
    public void declineByToken(String token, Long userId) {
        User user = currentUser(userId);
        TripInvitation invitation = byToken(token);

        // Email invitations can only be declined by the intended recipient
        if (invitation.isEmailInvite()
                && !invitation.getInvitedEmail().equals(
                        normalizeEmail(user.getEmail()))) {
            throw new TripAccessDeniedException(
                    "This invitation was sent to a different email address. "
                            + "Sign in with that account to decline it.");
        }

        // Trip owners should revoke invitations rather than decline them
        if (invitation.getTrip().getUser().getId().equals(user.getId())) {
            throw new SharingConflictException(
                    "You own this trip. Revoke the invitation instead.");
        }

        declinePendingInvitation(invitation);
    }

    // Shared decline logic for both invitation methods
    private void declinePendingInvitation(TripInvitation invitation) {
        Instant now = clock.instant();

        checkUsable(invitation, now);

        int updated = invitations.markDeclinedIfPending(
                invitation.getId(),
                now,
                InvitationStatus.PENDING,
                InvitationStatus.DECLINED);

        if (updated == 0) {
            throw new InvitationGoneException(
                    "This invitation is no longer available.");
        }
    }

    // Public link preview (no login). Shows only the trip name, destination,
    // the inviter's first name, the role and when the link expires.
    @Transactional(noRollbackFor = InvitationGoneException.class)
    public InvitationPreviewResponse preview(String token) {
        TripInvitation invitation = byToken(token);
        checkUsable(invitation, clock.instant());

        Trip trip = invitation.getTrip();
        return new InvitationPreviewResponse(trip.getName(), trip.getDestination(),
                firstName(inviterName(invitation)), invitation.getRole().name(),
                invitation.getExpiresAt(), invitation.isEmailInvite());
    }

    // ---------- Helpers ----------

    // Accepts an invitation for this user. Safe when two people try at the same
    // time:
    // markAcceptedIfPending only succeeds for one of them, and the unique
    // (trip_id, user_id) key stops a second membership for the same user.
    private JoinedTripResponse accept(TripInvitation invitation, User user) {
        Instant now = clock.instant();
        checkUsable(invitation, now);

        if (invitation.isEmailInvite() && !invitation.getInvitedEmail().equals(normalizeEmail(user.getEmail()))) {
            throw new TripAccessDeniedException(
                    "This invitation was sent to a different email address. Sign in with that account to accept it.");
        }

        Trip trip = invitation.getTrip();
        if (trip.getUser().getId().equals(user.getId())) {
            throw new SharingConflictException("You already own this trip.");
        }
        if (members.existsByTripIdAndUserId(trip.getId(), user.getId())) {
            throw new SharingConflictException("You're already a member of this trip.");
        }

        if (invitations.markAcceptedIfPending(invitation.getId(), user.getId(), now,
                InvitationStatus.PENDING, InvitationStatus.ACCEPTED) == 0) {
            throw new InvitationGoneException("This invitation has already been used.");
        }

        try {
            members.saveAndFlush(
                    new TripMember(trip, user, invitation.getRole(), now, invitation.getInvitedByUserId()));
        } catch (DataIntegrityViolationException e) {
            // Someone added this user at the same moment; the whole accept is rolled back
            throw new SharingConflictException("You're already a member of this trip.");
        }
        return new JoinedTripResponse(trip.getId(), trip.getName(), invitation.getRole().name());
    }

    // Throws 410 if the invitation can't be used. An expired one is saved as
    // EXPIRED first.
    private void checkUsable(TripInvitation invitation, Instant now) {
        switch (invitation.getStatus()) {
            case ACCEPTED -> throw new InvitationGoneException("This invitation has already been used.");
            case DECLINED -> throw new InvitationGoneException("This invitation was declined.");
            case REVOKED -> throw new InvitationGoneException("This invitation was cancelled by the trip owner.");
            case EXPIRED -> throw expired();
            case PENDING -> {
                if (!now.isBefore(invitation.getExpiresAt())) {
                    invitation.setStatus(InvitationStatus.EXPIRED);
                    invitations.save(invitation);
                    throw expired();
                }
            }
        }
    }

    private static InvitationGoneException expired() {
        return new InvitationGoneException("This invitation has expired. Ask the trip owner to send a new one.");
    }

    // The owner can't invite themselves, and existing members can't be invited
    // again
    private void checkCanInviteEmail(Trip trip, String email) {
        if (normalizeEmail(trip.getUser().getEmail()).equals(email)) {
            throw new SharingConflictException("You're the owner of this trip, so you can't invite yourself.");
        }
        users.findByEmail(email).ifPresent(user -> {
            if (members.existsByTripIdAndUserId(trip.getId(), user.getId())) {
                throw new SharingConflictException("This person is already a member of this trip.");
            }
        });
    }

    // Gives the invitation a new token and a fresh 7-day expiry. Returns the plain
    // token.
    private String startSending(TripInvitation invitation, Instant now) {
        String token = InvitationTokens.newToken();
        invitation.setTokenHash(InvitationTokens.hash(token));
        invitation.setLastSentAt(now);
        invitation.setExpiresAt(now.plus(INVITATION_LIFETIME));
        return token;
    }

    // Saves now, so a database duplicate becomes a clear 409 instead of a raw SQL
    // error
    private TripInvitation saveInvitation(TripInvitation invitation) {
        try {
            return invitations.saveAndFlush(invitation);
        } catch (DataIntegrityViolationException e) {
            throw new SharingConflictException("An invitation is already pending for this email.");
        }
    }

    // An email invitation that belongs to this user. Anything else is "not found",
    // so users can't probe other people's invitations by ID.
    private TripInvitation myEmailInvitation(Long invitationId, User user) {
        return invitations.findById(invitationId)
                .filter(TripInvitation::isEmailInvite)
                .filter(i -> i.getInvitedEmail().equals(normalizeEmail(user.getEmail())))
                .orElseThrow(() -> new SharingNotFoundException("Invitation not found"));
    }

    private TripInvitation byToken(String token) {
        if (token == null || token.isBlank()) {
            throw new SharingNotFoundException("This invitation link is not valid.");
        }
        return invitations.findByTokenHash(InvitationTokens.hash(token.trim()))
                .orElseThrow(() -> new SharingNotFoundException(
                        "This invitation link is not valid. The trip may have been deleted."));
    }

    private User currentUser(Long userId) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in required");
        }
        return users.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in required"));
    }

    // Members can only be EDITOR or VIEWER (the owner is always the trip creator)
    private static TripRole memberRole(String role) {
        if ("EDITOR".equals(role)) {
            return TripRole.EDITOR;
        }
        if ("VIEWER".equals(role)) {
            return TripRole.VIEWER;
        }
        throw new IllegalArgumentException("Role must be EDITOR or VIEWER");
    }

    private String inviterName(TripInvitation invitation) {
        return users.findById(invitation.getInvitedByUserId()).map(User::getName).orElse("A trip owner");
    }

    private static String firstName(String name) {
        String trimmed = name == null ? "" : name.trim();
        int space = trimmed.indexOf(' ');
        return space > 0 ? trimmed.substring(0, space) : trimmed;
    }

    private InvitationResponse toOwnerResponse(TripInvitation i, String token) {
        return new InvitationResponse(
                i.getId(),
                i.isEmailInvite() ? "EMAIL" : "LINK",
                i.getInvitedEmail(),
                i.getRole().name(),
                i.getStatus().name(),
                i.getCreatedAt(),
                i.getLastSentAt(),
                i.getExpiresAt(),
                i.getLastSentAt().plus(RESEND_COOLDOWN),
                token,
                token == null ? null : "/invite/" + token);
    }
}
