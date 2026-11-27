package com.tripbudgetplanner.backend.auth;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.tripbudgetplanner.backend.auth.dto.LoginResponse;
import com.tripbudgetplanner.backend.auth.dto.ResendCodeRequest;
import com.tripbudgetplanner.backend.auth.dto.VerifyCodeRequest;
import com.tripbudgetplanner.backend.auth.twofactor.TwoFactorService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.tripbudgetplanner.backend.auth.dto.AuthResponse;
import com.tripbudgetplanner.backend.auth.dto.LoginRequest;
import com.tripbudgetplanner.backend.auth.dto.RegisterRequest;
import com.tripbudgetplanner.backend.auth.exception.EmailAlreadyExistsException;
import com.tripbudgetplanner.backend.auth.exception.InvalidCredentialsException;
import com.tripbudgetplanner.backend.common.security.JwtService;
import com.tripbudgetplanner.backend.user.User;
import com.tripbudgetplanner.backend.user.UserRepository;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    // Null in tests that only cover passwords: then sign-in issues the token straight away
    private final TwoFactorService twoFactor;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this(userRepository, passwordEncoder, jwtService, null);
    }

    @Autowired
    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService,
                       TwoFactorService twoFactor) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.twoFactor = twoFactor;
    }
    public void register(RegisterRequest req) {
        String email = req.email().trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyExistsException(email);
        }
        User user = new User();
        user.setName(req.name());
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setPhoneNumber(req.phoneNumber());
        user.setDateOfBirth(req.dateOfBirth());
        user.setAddress(req.address());
        userRepository.save(user);
    }

    // Checks the password and issues a token (no two-factor step)
    public AuthResponse login(LoginRequest req) {
        return issueToken(checkPassword(req));
    }

    // UC1 sign-in, step 1: check the password. With two-factor on, send a code instead of the token.
    public LoginResponse startLogin(LoginRequest req) {
        User user = checkPassword(req);
        if (twoFactor == null || !twoFactor.isEnabled()) {
            return LoginResponse.signedIn(issueToken(user));
        }
        return twoFactor.start(user);
    }

    // Step 2: the emailed code. Issues the token once it's right.
    public AuthResponse verifyCode(VerifyCodeRequest req) {
        if (twoFactor == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Two-factor sign-in is not turned on.");
        }
        Long userId = twoFactor.verify(req.challengeId(), req.code());
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.GONE, "This account no longer exists."));
        return issueToken(user);
    }

    public LoginResponse resendCode(ResendCodeRequest req) {
        if (twoFactor == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Two-factor sign-in is not turned on.");
        }
        return twoFactor.resend(req.challengeId());
    }

    private User checkPassword(LoginRequest req) {
        User user = userRepository.findByEmail(req.email().trim().toLowerCase())
                .orElseThrow(InvalidCredentialsException::new);
        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        return user;
    }

    private AuthResponse issueToken(User user) {
        return new AuthResponse(jwtService.generateToken(user.getId(), user.getEmail()), user.getName(), user.getEmail());
    }
}