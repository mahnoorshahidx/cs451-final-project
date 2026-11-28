package com.tripbudgetplanner.backend.member;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tripbudgetplanner.backend.member.dto.AcceptInvitationRequest;
import com.tripbudgetplanner.backend.member.dto.InvitationPreviewResponse;
import com.tripbudgetplanner.backend.member.dto.JoinedTripResponse;
import com.tripbudgetplanner.backend.member.dto.ReceivedInvitationResponse;

import jakarta.validation.Valid;

// UC9: invitations from the invited person's side
@RestController
@RequestMapping("/api/invitations")
public class InvitationController {

    private final TripMemberService memberService;

    public InvitationController(TripMemberService memberService) {
        this.memberService = memberService;
    }

    // My pending email invitations
    @GetMapping
    public List<ReceivedInvitationResponse> myInvitations(@AuthenticationPrincipal Long userId) {
        return memberService.myInvitations(userId);
    }

    // Accept from a shareable link: { "token": "..." }
    @PostMapping("/accept")
    public JoinedTripResponse acceptByToken(@Valid @RequestBody AcceptInvitationRequest request,
            @AuthenticationPrincipal Long userId) {
        return memberService.acceptByToken(request.token(), userId);
    }

    // Accept from the in-app list
    @PostMapping("/{invitationId}/accept")
    public JoinedTripResponse accept(@PathVariable Long invitationId,
            @AuthenticationPrincipal Long userId) {
        return memberService.acceptById(invitationId, userId);
    }

    // Decline from the in-app list
    @PostMapping("/{invitationId}/decline")
    public ResponseEntity<Void> decline(@PathVariable Long invitationId,
            @AuthenticationPrincipal Long userId) {
        memberService.decline(invitationId, userId);
        return ResponseEntity.noContent().build();
    }

    // UC9: Decline an invitation from a shareable link
    @PostMapping("/decline")
    public ResponseEntity<Void> declineByToken(
            @Valid @RequestBody AcceptInvitationRequest request,
            @AuthenticationPrincipal Long userId) {

        memberService.declineByToken(request.token(), userId);

        return ResponseEntity.noContent().build();
    }

    // PUBLIC (no login, see SecurityConfig): minimal preview for the invitation
    // page
    @GetMapping("/preview/{token}")
    public InvitationPreviewResponse preview(@PathVariable String token) {
        return memberService.preview(token);
    }
}
