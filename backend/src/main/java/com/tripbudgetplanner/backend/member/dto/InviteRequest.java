package com.tripbudgetplanner.backend.member.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// Create an invitation.
// With an email: an email invitation that only that user can accept.
// Without an email: a single-use shareable link.
public record InviteRequest(

    @Email(message = "Enter a valid email address")
    @Size(max = 255, message = "Email is too long")
    String email,

    @NotBlank(message = "Role is required")
    @Pattern(regexp = "EDITOR|VIEWER", message = "Role must be EDITOR or VIEWER")
    String role
) {}
