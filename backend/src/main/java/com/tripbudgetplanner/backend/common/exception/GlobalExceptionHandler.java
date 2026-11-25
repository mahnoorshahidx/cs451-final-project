package com.tripbudgetplanner.backend.common.exception;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.tripbudgetplanner.backend.auth.exception.EmailAlreadyExistsException;
import com.tripbudgetplanner.backend.auth.exception.InvalidCredentialsException;
import com.tripbudgetplanner.backend.bank.exception.BankAccountAlreadyConnectedException;
import com.tripbudgetplanner.backend.bank.exception.BankAccountNotFoundException;
import com.tripbudgetplanner.backend.budget.exception.BudgetConflictException;
import com.tripbudgetplanner.backend.member.exception.InvitationCooldownException;
import com.tripbudgetplanner.backend.search.exception.DuplicateTripItemException;
import com.tripbudgetplanner.backend.search.exception.SearchItemMismatchException;
import com.tripbudgetplanner.backend.search.exception.SearchItemNotFoundException;
import com.tripbudgetplanner.backend.member.exception.InvitationGoneException;
import com.tripbudgetplanner.backend.member.exception.SharingConflictException;
import com.tripbudgetplanner.backend.member.exception.SharingNotFoundException;
import com.tripbudgetplanner.backend.trip.exception.TripAccessDeniedException;
import com.tripbudgetplanner.backend.trip.exception.TripCurrencyChangeException;
import com.tripbudgetplanner.backend.trip.exception.TripNotFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(e -> fieldErrors.put(e.getField(), e.getDefaultMessage()));
        return ResponseEntity.badRequest()
                .body(Map.of("message", "Missing or invalid information", "errors", fieldErrors));
    }

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<Map<String, String>> handleDuplicate(EmailAlreadyExistsException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<Map<String, String>> handleBadCredentials(InvalidCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(BankAccountAlreadyConnectedException.class)
    public ResponseEntity<Map<String, String>> handleBankDuplicate(BankAccountAlreadyConnectedException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", ex.getMessage()));
    }

    // 409: the trip's currency can't change because it already has budget, expense or savings amounts
    @ExceptionHandler(TripCurrencyChangeException.class)
    public ResponseEntity<Map<String, String>> handleCurrencyChange(TripCurrencyChangeException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", ex.getMessage()));
    }

    // ---------- UC9: shared trips ----------

    // 403: the user can see the trip, but their role doesn't allow this
    @ExceptionHandler(TripAccessDeniedException.class)
    public ResponseEntity<Map<String, String>> handleTripAccessDenied(TripAccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", ex.getMessage()));
    }

    // 404: member or invitation not found
    @ExceptionHandler(SharingNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleSharingNotFound(SharingNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", ex.getMessage()));
    }

    // 409: duplicate member or invitation, or the owner can't be changed
    @ExceptionHandler(SharingConflictException.class)
    public ResponseEntity<Map<String, String>> handleSharingConflict(SharingConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", ex.getMessage()));
    }

    // 410: the invitation expired, was revoked or declined, or was already used
    @ExceptionHandler(InvitationGoneException.class)
    public ResponseEntity<Map<String, String>> handleInvitationGone(InvitationGoneException ex) {
        return ResponseEntity.status(HttpStatus.GONE).body(Map.of("message", ex.getMessage()));
    }

    // 429: resend cooldown. Also sets the standard Retry-After header.
    @ExceptionHandler(InvitationCooldownException.class)
    public ResponseEntity<Map<String, Object>> handleInvitationCooldown(InvitationCooldownException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .header("Retry-After", String.valueOf(ex.getRetryAfterSeconds()))
            .body(Map.of("message", ex.getMessage(), "retryAfterSeconds", ex.getRetryAfterSeconds()));
    }

    // ---------- UC6: travel search ----------

    // 404: the search result ID is unknown or no longer available
    @ExceptionHandler(SearchItemNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleSearchItemNotFound(SearchItemNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", ex.getMessage()));
    }

    // 400: the search result doesn't match the trip
    @ExceptionHandler(SearchItemMismatchException.class)
    public ResponseEntity<Map<String, String>> handleSearchItemMismatch(SearchItemMismatchException ex) {
        return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
    }

    // 409: the item is already in the itinerary
    @ExceptionHandler(DuplicateTripItemException.class)
    public ResponseEntity<Map<String, String>> handleDuplicateTripItem(DuplicateTripItemException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", ex.getMessage()));
    }

    // 409: the budget was changed by someone else, or its currency can't be converted
    @ExceptionHandler(BudgetConflictException.class)
    public ResponseEntity<Map<String, String>> handleBudgetConflict(BudgetConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(TripNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleTripNotFound(TripNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(BankAccountNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleBankNotFound(BankAccountNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", ex.getMessage()));
    }
}