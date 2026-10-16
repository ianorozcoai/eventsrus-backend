package com.backend.eventsrus.exception;

/** Thrown when one user tries to message someone they are blocked with (either direction); mapped to HTTP 403. */
public class UserBlockedException extends RuntimeException {

    public UserBlockedException(String message) {
        super(message);
    }
}
