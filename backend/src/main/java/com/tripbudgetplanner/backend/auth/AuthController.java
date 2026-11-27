package com.tripbudgetplanner.backend.auth;

import com.tripbudgetplanner.backend.auth.dto.LoginResponse;
import com.tripbudgetplanner.backend.auth.dto.ResendCodeRequest;
import com.tripbudgetplanner.backend.auth.dto.VerifyCodeRequest;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tripbudgetplanner.backend.auth.dto.AuthResponse;
import com.tripbudgetplanner.backend.auth.dto.LoginRequest;
import com.tripbudgetplanner.backend.auth.dto.RegisterRequest;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, String>> register(@Valid @RequestBody RegisterRequest req) {
        authService.register(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("message", "Registration successful"));
    }

    // Step 1: email + password. With two-factor on, returns a challenge instead of a token.
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest req) {
        return authService.startLogin(req);
    }

    // Step 2: the 6-digit code that was sent. Returns the token.
    @PostMapping("/verify")
    public AuthResponse verify(@Valid @RequestBody VerifyCodeRequest req) {
        return authService.verifyCode(req);
    }

    // Sends a new code for the same sign-in
    @PostMapping("/resend")
    public LoginResponse resend(@Valid @RequestBody ResendCodeRequest req) {
        return authService.resendCode(req);
    }
}