package com.tripbudgetplanner.backend.auth.twofactor;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LoginChallengeRepository extends JpaRepository<LoginChallenge, String> {

    // Open challenges for a user, closed when they start a new sign-in
    List<LoginChallenge> findByUserIdAndClosedAtIsNull(Long userId);
}
