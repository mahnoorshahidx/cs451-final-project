package com.tripbudgetplanner.backend.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Accept an invitation from a link: { "token": "..." }
public record AcceptInvitationRequest(

    @NotBlank(message = "Invitation token is required")
    @Size(max = 100, message = "Invitation token is too long")
    String token
) {}
