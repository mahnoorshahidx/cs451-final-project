package com.tripbudgetplanner.backend.auth;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import org.springframework.security.crypto.password.PasswordEncoder;

import com.tripbudgetplanner.backend.auth.dto.AuthResponse;
import com.tripbudgetplanner.backend.auth.dto.LoginRequest;
import com.tripbudgetplanner.backend.auth.dto.RegisterRequest;
import com.tripbudgetplanner.backend.auth.exception.EmailAlreadyExistsException;
import com.tripbudgetplanner.backend.auth.exception.InvalidCredentialsException;
import com.tripbudgetplanner.backend.common.security.JwtService;
import com.tripbudgetplanner.backend.user.User;
import com.tripbudgetplanner.backend.user.UserRepository;

class AuthServiceTest {

    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private JwtService jwtService;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        jwtService = mock(JwtService.class);

        authService = new AuthService(
            userRepository, passwordEncoder, jwtService
        );
    }

    // Test registering a new user
    @Test
    void registerUserSuccessfully() {
        RegisterRequest request = new RegisterRequest(
            "Test User", "TEST@example.com", "Password123",
            null, null, null
        );

        when(passwordEncoder.encode("Password123"))
            .thenReturn("hashedPassword");

        authService.register(request);

        ArgumentCaptor<User> captor =
            ArgumentCaptor.forClass(User.class);

        verify(userRepository).save(captor.capture());

        User savedUser = captor.getValue();

        assertEquals("Test User", savedUser.getName());
        assertEquals("test@example.com", savedUser.getEmail());
        assertEquals("hashedPassword", savedUser.getPasswordHash());
    }

    // Test rejecting duplicate emails
    @Test
    void rejectDuplicateEmail() {
        RegisterRequest request = new RegisterRequest(
            "Test User", "test@example.com", "Password123",
            null, null, null
        );

        when(userRepository.existsByEmail("test@example.com"))
            .thenReturn(true);

        assertThrows(EmailAlreadyExistsException.class,
            () -> authService.register(request));

        verify(userRepository, never()).save(any(User.class));
    }

    // Test logging in with valid credentials
    @Test
    void loginSuccessfully() {
        User user = mock(User.class);

        when(user.getId()).thenReturn(1L);
        when(user.getName()).thenReturn("Test User");
        when(user.getEmail()).thenReturn("test@example.com");
        when(user.getPasswordHash()).thenReturn("hashedPassword");

        when(userRepository.findByEmail("test@example.com"))
            .thenReturn(Optional.of(user));

        when(passwordEncoder.matches("Password123", "hashedPassword"))
            .thenReturn(true);

        when(jwtService.generateToken(1L, "test@example.com"))
            .thenReturn("test-jwt-token");

        AuthResponse response = authService.login(
            new LoginRequest("test@example.com", "Password123")
        );

        assertEquals("test-jwt-token", response.token());
        assertEquals("Test User", response.name());
        assertEquals("test@example.com", response.email());
    }

    // Test rejecting an incorrect password
    @Test
    void rejectIncorrectPassword() {
        User user = new User();
        user.setEmail("test@example.com");
        user.setPasswordHash("hashedPassword");

        when(userRepository.findByEmail("test@example.com"))
            .thenReturn(Optional.of(user));

        when(passwordEncoder.matches("WrongPassword", "hashedPassword"))
            .thenReturn(false);

        assertThrows(InvalidCredentialsException.class,
            () -> authService.login(
                new LoginRequest("test@example.com", "WrongPassword")
            ));

        verifyNoInteractions(jwtService);
    }

    // Test rejecting login for a nonexistent email
    @Test
    void rejectUnknownEmail() {
        when(userRepository.findByEmail("missing@example.com"))
            .thenReturn(Optional.empty());

        assertThrows(InvalidCredentialsException.class,
            () -> authService.login(
                new LoginRequest("missing@example.com", "Password123")
            ));

        verifyNoInteractions(jwtService);
    }

    // Test normalizing email before login
    @Test
    void normalizeEmailDuringLogin() {
        User user = mock(User.class);

        when(user.getId()).thenReturn(1L);
        when(user.getName()).thenReturn("Test User");
        when(user.getEmail()).thenReturn("test@example.com");
        when(user.getPasswordHash()).thenReturn("hashedPassword");

        when(userRepository.findByEmail("test@example.com"))
            .thenReturn(Optional.of(user));

        when(passwordEncoder.matches("Password123", "hashedPassword"))
            .thenReturn(true);

        when(jwtService.generateToken(1L, "test@example.com"))
            .thenReturn("test-jwt-token");

        authService.login(
            new LoginRequest("  TEST@EXAMPLE.COM  ", "Password123")
        );

        verify(userRepository).findByEmail("test@example.com");
    }
}
