package com.tripbudgetplanner.backend.user;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tripbudgetplanner.backend.user.dto.ChangePasswordRequest;
import com.tripbudgetplanner.backend.user.dto.ProfileResponse;
import com.tripbudgetplanner.backend.user.dto.UpdateProfileRequest;

import jakarta.validation.Valid;

// Handles API requests for user profiles and account security
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    // Gets the logged-in user's profile
    @GetMapping("/me")
    public ProfileResponse getProfile(
            @AuthenticationPrincipal Long userId) {
        return userService.getProfile(userId);
    }

    // Updates the logged-in user's profile
    @PutMapping("/me")
    public ProfileResponse updateProfile(
            @Valid @RequestBody UpdateProfileRequest request,
            @AuthenticationPrincipal Long userId) {
        return userService.updateProfile(userId, request);
    }

    // Changes the logged-in user's password
    @PutMapping("/me/password")
    public ResponseEntity<Void> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            @AuthenticationPrincipal Long userId) {

        userService.changePassword(userId, request);
        return ResponseEntity.noContent().build();
    }

    // Updates the user's trip reminder preference
    @PutMapping("/me/notifications")
    public ResponseEntity<Void> updateNotificationPreference(
            @RequestBody NotificationPreferenceRequest request,
            @AuthenticationPrincipal Long userId) {

        userService.updateNotificationPreference(
            userId,
            request.tripRemindersEnabled()
        );

        return ResponseEntity.noContent().build();
    }
}
