package com.backend.eventsrus.exception;

/**
 * Thrown when a Google account's declared login intent (planner/vendor)
 * conflicts with the door it first signed up through - see
 * UserService#findOrCreateFromGoogle. Each email is locked to one identity
 * for good; the fix is a different email, not retrying.
 */
public class AccountIdentityConflictException extends RuntimeException {

    public AccountIdentityConflictException(String message) {
        super(message);
    }
}
