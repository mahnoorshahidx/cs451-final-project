package com.tripbudgetplanner.backend.bank;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tripbudgetplanner.backend.bank.dto.BankAccountResponse;
import com.tripbudgetplanner.backend.bank.dto.ConnectBankAccountRequest;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/bank-accounts")
public class BankAccountController {

    private final BankAccountService bankAccountService;

    public BankAccountController(BankAccountService bankAccountService) {
        this.bankAccountService = bankAccountService;
    }

    // Connect a bank account to the logged-in user
    @PostMapping
    public ResponseEntity<BankAccountResponse> connect(
            @Valid @RequestBody ConnectBankAccountRequest request,
            @AuthenticationPrincipal Long userId) {
        BankAccount account = bankAccountService.connect(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(account));
    }

    // List the logged-in user's connected accounts
    @GetMapping
    public List<BankAccountResponse> list(@AuthenticationPrincipal Long userId) {
        return bankAccountService.listForUser(userId).stream().map(this::toResponse).toList();
    }
    
    // Disconnect one of the logged-in user's accounts
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> disconnect(@PathVariable Long id, @AuthenticationPrincipal Long userId) {
        bankAccountService.disconnect(id, userId);
        return ResponseEntity.noContent().build();
    }

    private BankAccountResponse toResponse(BankAccount a) {
        return new BankAccountResponse(a.getId(), a.getAccountType(), a.getRoutingNumber(), a.getAccountLast4());
    }
}