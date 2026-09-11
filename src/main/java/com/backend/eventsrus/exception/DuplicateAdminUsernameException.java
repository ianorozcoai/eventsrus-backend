package com.backend.eventsrus.exception;

public class DuplicateAdminUsernameException extends RuntimeException {

    public DuplicateAdminUsernameException(String message) {
        super(message);
    }
}
