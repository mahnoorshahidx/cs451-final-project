package com.tripbudgetplanner.backend.auth;

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

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
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

    public AuthResponse login(LoginRequest req) {
        User user = userRepository.findByEmail(req.email().trim().toLowerCase())
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        return new AuthResponse(jwtService.generateToken(user.getId(), user.getEmail()),
        user.getName(), user.getEmail());
    }
}