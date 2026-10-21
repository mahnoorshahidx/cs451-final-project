package com.tripbudgetplanner.backend.bank.exception;

public class BankAccountNotFoundException extends RuntimeException {
    public BankAccountNotFoundException() {
        super("Bank account not found");
    }
}