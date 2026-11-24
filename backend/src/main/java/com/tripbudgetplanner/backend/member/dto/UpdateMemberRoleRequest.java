package com.tripbudgetplanner.backend.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

// Change a member's role. The owner's role can't be changed.
public record UpdateMemberRoleRequest(

    @NotBlank(message = "Role is required")
    @Pattern(regexp = "EDITOR|VIEWER", message = "Role must be EDITOR or VIEWER")
    String role
) {}
