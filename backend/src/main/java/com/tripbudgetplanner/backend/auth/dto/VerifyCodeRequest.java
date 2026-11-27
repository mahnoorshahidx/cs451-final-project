package com.tripbudgetplanner.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record VerifyCodeRequest(
    @NotBlank(message = "Sign-in session is missing. Please sign in again.") String challengeId,
    @NotBlank(message = "Enter the 6-digit code")
    @Pattern(regexp = "^\\s*\\d{6}\\s*$", message = "The code is 6 digits")
    String code
) {}
