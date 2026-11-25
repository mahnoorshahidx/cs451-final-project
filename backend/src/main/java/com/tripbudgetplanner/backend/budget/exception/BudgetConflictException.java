package com.tripbudgetplanner.backend.budget.exception;

// 409: the budget can't be changed this way right now
// (someone else saved it first, or its currency can't be converted).
public class BudgetConflictException extends RuntimeException {

    public BudgetConflictException(String message) {
        super(message);
    }
}
