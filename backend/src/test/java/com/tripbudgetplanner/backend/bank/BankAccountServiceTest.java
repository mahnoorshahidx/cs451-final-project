package com.tripbudgetplanner.backend.bank;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.tripbudgetplanner.backend.bank.dto.ConnectBankAccountRequest;
import com.tripbudgetplanner.backend.bank.exception.BankAccountAlreadyConnectedException;
import com.tripbudgetplanner.backend.bank.exception.BankAccountNotFoundException;
import com.tripbudgetplanner.backend.user.User;
import com.tripbudgetplanner.backend.user.UserRepository;

class BankAccountServiceTest {

    @Mock
    private BankAccountRepository bankAccountRepository;

    @Mock
    private UserRepository userRepository;

    private BankAccountService bankAccountService;

    private final Long userId = 1L;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        bankAccountService = new BankAccountService(
            bankAccountRepository,
            userRepository,
            "test-secret"
        );
    }

    // Test connecting a valid bank account
    @Test
    void connectBankAccountSuccessfully() {
        ConnectBankAccountRequest request =
            new ConnectBankAccountRequest(
                AccountType.CHECKING, "000000000", "123456789012"
            );

        User user = new User();

        when(userRepository.findById(userId))
            .thenReturn(Optional.of(user));

        when(bankAccountRepository.save(any(BankAccount.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        BankAccount result = bankAccountService.connect(request, userId);

        assertEquals(AccountType.CHECKING, result.getAccountType());
        assertEquals("000000000", result.getRoutingNumber());
        assertEquals("9012", result.getAccountLast4());
        assertNotNull(result.getAccountFingerprint());

        verify(bankAccountRepository).save(any(BankAccount.class));
    }

    // Test rejecting an invalid routing number
    @Test
    void rejectInvalidRoutingNumber() {
        ConnectBankAccountRequest request =
            new ConnectBankAccountRequest(
                AccountType.CHECKING, "123456789", "123456789012"
            );

        assertThrows(IllegalArgumentException.class,
            () -> bankAccountService.connect(request, userId));

        verify(bankAccountRepository, never())
            .save(any(BankAccount.class));
    }

    // Test preventing duplicate bank accounts
    @Test
    void rejectDuplicateBankAccount() {
        ConnectBankAccountRequest request =
            new ConnectBankAccountRequest(
                AccountType.CHECKING, "000000000", "123456789012"
            );

        when(userRepository.findById(userId))
            .thenReturn(Optional.of(new User()));

        when(bankAccountRepository.existsByUserIdAndAccountFingerprint(
            eq(userId), anyString()
        )).thenReturn(true);

        assertThrows(BankAccountAlreadyConnectedException.class,
            () -> bankAccountService.connect(request, userId));

        verify(bankAccountRepository, never())
            .save(any(BankAccount.class));
    }

    // Test retrieving connected bank accounts
    @Test
    void listConnectedBankAccounts() {
        BankAccount account = new BankAccount();

        when(bankAccountRepository.findByUserId(userId))
            .thenReturn(List.of(account));

        List<BankAccount> result =
            bankAccountService.listForUser(userId);

        assertEquals(1, result.size());
    }

    // Test disconnecting an account
    @Test
    void disconnectBankAccountSuccessfully() {
        BankAccount account = new BankAccount();

        when(bankAccountRepository.findByIdAndUserId(1L, userId))
            .thenReturn(Optional.of(account));

        bankAccountService.disconnect(1L, userId);

        verify(bankAccountRepository).delete(account);
    }

    // Test preventing a user from disconnecting an account they don't own
    @Test
    void rejectDisconnectingAnotherUsersAccount() {
        when(bankAccountRepository.findByIdAndUserId(2L, userId))
            .thenReturn(Optional.empty());

        assertThrows(BankAccountNotFoundException.class,
            () -> bankAccountService.disconnect(2L, userId));

        verify(bankAccountRepository, never())
            .delete(any(BankAccount.class));
    }
}
