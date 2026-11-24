package com.tripbudgetplanner.backend.member;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tripbudgetplanner.backend.member.dto.InvitationResponse;
import com.tripbudgetplanner.backend.member.dto.InviteRequest;
import com.tripbudgetplanner.backend.member.dto.TripMemberResponse;
import com.tripbudgetplanner.backend.member.dto.UpdateMemberRoleRequest;

import jakarta.validation.Valid;

// UC9: members and invitations of one trip
@RestController
@RequestMapping("/api/trips/{tripId}")
public class TripMemberController {

    private final TripMemberService memberService;

    public TripMemberController(TripMemberService memberService) {
        this.memberService = memberService;
    }

    // Everyone on the trip (owner first). Emails are only shown to the owner.
    @GetMapping("/members")
    public List<TripMemberResponse> listMembers(@PathVariable Long tripId,
                                                @AuthenticationPrincipal Long userId) {
        return memberService.listMembers(tripId, userId);
    }

    // Owner only: switch a member between EDITOR and VIEWER
    @PutMapping("/members/{memberUserId}")
    public TripMemberResponse changeRole(@PathVariable Long tripId,
                                         @PathVariable Long memberUserId,
                                         @Valid @RequestBody UpdateMemberRoleRequest request,
                                         @AuthenticationPrincipal Long userId) {
        return memberService.changeRole(tripId, memberUserId, request.role(), userId);
    }

    // Owner removes a member, or a member removes themselves (leave the trip)
    @DeleteMapping("/members/{memberUserId}")
    public ResponseEntity<Void> removeMember(@PathVariable Long tripId,
                                             @PathVariable Long memberUserId,
                                             @AuthenticationPrincipal Long userId) {
        memberService.removeMember(tripId, memberUserId, userId);
        return ResponseEntity.noContent().build();
    }

    // Owner only: invite by email, or create a shareable link (no email)
    @PostMapping("/invitations")
    public ResponseEntity<InvitationResponse> invite(@PathVariable Long tripId,
                                                     @Valid @RequestBody InviteRequest request,
                                                     @AuthenticationPrincipal Long userId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(memberService.invite(tripId, request, userId));
    }

    // Owner only: pending invitations
    @GetMapping("/invitations")
    public List<InvitationResponse> listInvitations(@PathVariable Long tripId,
                                                    @AuthenticationPrincipal Long userId) {
        return memberService.listTripInvitations(tripId, userId);
    }

    // Owner only: new link and expiry (at most every 10 minutes)
    @PostMapping("/invitations/{invitationId}/resend")
    public InvitationResponse resend(@PathVariable Long tripId,
                                     @PathVariable Long invitationId,
                                     @AuthenticationPrincipal Long userId) {
        return memberService.resend(tripId, invitationId, userId);
    }

    // Owner only: cancel an invitation
    @DeleteMapping("/invitations/{invitationId}")
    public ResponseEntity<Void> revoke(@PathVariable Long tripId,
                                       @PathVariable Long invitationId,
                                       @AuthenticationPrincipal Long userId) {
        memberService.revoke(tripId, invitationId, userId);
        return ResponseEntity.noContent().build();
    }
}
