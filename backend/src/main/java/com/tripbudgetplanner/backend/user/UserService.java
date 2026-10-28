
package com.tripbudgetplanner.backend.user;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tripbudgetplanner.backend.auth.exception.EmailAlreadyExistsException;
import com.tripbudgetplanner.backend.user.dto.ChangePasswordRequest;
import com.tripbudgetplanner.backend.user.dto.ProfileResponse;
import com.tripbudgetplanner.backend.user.dto.UpdateProfileRequest;
import com.tripbudgetplanner.backend.user.dto.UpdatePreferencesRequest;

// Handles user profile and account security operations
@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // Gets the logged-in user's profile
    public ProfileResponse getProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        return toResponse(user);
    }

    // Updates the logged-in user's name and email
    @Transactional
    public ProfileResponse updateProfile(Long userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        String email = request.email().trim().toLowerCase();

        // Prevent using an email that belongs to another account
        if (!user.getEmail().equals(email) && userRepository.existsByEmail(email)) {
            throw new EmailAlreadyExistsException(email);
        }

        // Save the updated profile
        user.setName(request.name().trim());
        user.setEmail(email);
        userRepository.save(user);

        return toResponse(user);
    }

    // Changes the logged-in user's password
    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        // Check if the current password is correct
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }

        // Prevent reusing the current password
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("New password must be different from current password");
        }

        // Hash and save the new password
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
    }

    // Saves the user's trip reminder preference
    @Transactional
    public void updateNotificationPreference(Long userId, boolean enabled) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        user.setTripRemindersEnabled(enabled);
        userRepository.save(user);
    }

    // Saves the user's preferences
    @Transactional
    public ProfileResponse updatePreferences(Long userId, UpdatePreferencesRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        // Check supported preference values
        if (!java.util.List.of("USD", "EUR", "GBP", "MAD").contains(request.currency())
                || !java.util.List.of("MM/DD/YYYY", "DD/MM/YYYY").contains(request.dateFormat())
                || !java.util.List.of("light", "dark").contains(request.appearance())) {
            throw new IllegalArgumentException("Invalid preference selection");
        }

        // Save preferences
        user.setCurrency(request.currency());
        user.setDateFormat(request.dateFormat());
        user.setAppearance(request.appearance());

        userRepository.save(user);

        return toResponse(user);
    }

    // Converts the User entity into a safe response
    private ProfileResponse toResponse(User user) {
        return new ProfileResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.isTripRemindersEnabled(),
                user.getCurrency(),
                user.getDateFormat(),
                user.getAppearance()
        );
    }
}
