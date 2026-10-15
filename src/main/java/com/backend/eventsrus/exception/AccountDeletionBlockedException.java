package com.backend.eventsrus.exception;

/** Thrown when an account can't be deleted yet (e.g. active subscription); mapped to HTTP 409. */
public class AccountDeletionBlockedException extends RuntimeException {

    public AccountDeletionBlockedException(String message) {
        super(message);
    }
}
