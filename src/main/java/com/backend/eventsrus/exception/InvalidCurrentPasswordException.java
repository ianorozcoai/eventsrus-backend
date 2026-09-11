package com.backend.eventsrus.exception;

/** Thrown when an admin's change-password request supplies the wrong current password. */
public class InvalidCurrentPasswordException extends RuntimeException {

    public InvalidCurrentPasswordException(String message) {
        super(message);
    }
}
