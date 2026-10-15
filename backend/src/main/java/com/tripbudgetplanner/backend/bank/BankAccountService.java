package com.tripbudgetplanner.backend.bank;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import java.util.List;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.tripbudgetplanner.backend.bank.dto.ConnectBankAccountRequest;
import com.tripbudgetplanner.backend.bank.exception.BankAccountAlreadyConnectedException;
import com.tripbudgetplanner.backend.user.User;
import com.tripbudgetplanner.backend.user.UserRepository;

@Service
public class BankAccountService {

    private final BankAccountRepository bankAccountRepository;
    private final UserRepository userRepository;
    private final String fingerprintSecret;

    public BankAccountService(BankAccountRepository bankAccountRepository,
                              UserRepository userRepository,
                              @Value("${app.bank.fingerprint-secret}") String fingerprintSecret) {
        this.bankAccountRepository = bankAccountRepository;
        this.userRepository = userRepository;
        this.fingerprintSecret = fingerprintSecret;
    }

    public BankAccount connect(ConnectBankAccountRequest request, Long userId) {
        // Format (9 digits, 4-17 digit account) was already checked by the DTO annotations.
        // Here we also check the routing number's built-in checksum.
        if (!hasValidRoutingChecksum(request.routingNumber())) {
            throw new IllegalArgumentException("Invalid routing number");
        }

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        String fingerprint = fingerprint(request.routingNumber(), request.accountNumber());
        if (bankAccountRepository.existsByUserIdAndAccountFingerprint(userId, fingerprint)) {
            throw new BankAccountAlreadyConnectedException();
        }

        // SIMULATED bank verification: a real bank/aggregator API call would go here.
        // For now, every well-formed account verifies successfully.

        String accountNumber = request.accountNumber();
        BankAccount account = new BankAccount();
        account.setUser(user);
        account.setAccountType(request.accountType());
        account.setRoutingNumber(request.routingNumber());
        account.setAccountLast4(accountNumber.substring(accountNumber.length() - 4));
        account.setAccountFingerprint(fingerprint);
        return bankAccountRepository.save(account);
    }

    public List<BankAccount> listForUser(Long userId) {
        return bankAccountRepository.findByUserId(userId);
    }

    // ABA routing number checksum: weights 3,7,1 repeated; total must be divisible by 10
    private boolean hasValidRoutingChecksum(String routing) {
        int[] weights = {3, 7, 1, 3, 7, 1, 3, 7, 1};
        int sum = 0;
        for (int i = 0; i < 9; i++) {
            sum += (routing.charAt(i) - '0') * weights[i];
        }
        return sum % 10 == 0;
    }

    private String fingerprint(String routing, String accountNumber) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(fingerprintSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal((routing + ":" + accountNumber).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not hash account number", e);
        }
    }
}