package com.tripbudgetplanner.backend.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

// Validates the information submitted when updating a profile
public record UpdateProfileRequest(

        @NotBlank(message = "Name is required")
        String name,

        @NotBlank(message = "Email is required")
        @Email(message = "Enter a valid email address")
        String email

) {}
