package com.tripbudgetplanner.backend.bank.exception;

public class BankAccountAlreadyConnectedException extends RuntimeException {
    public BankAccountAlreadyConnectedException() {
        super("This bank account is already connected to your profile");
    }
}