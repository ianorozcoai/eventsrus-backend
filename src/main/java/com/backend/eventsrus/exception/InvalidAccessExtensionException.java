package com.backend.eventsrus.exception;

/** An admin's "extend access" request that cannot be carried out; the message is shown to the admin. */
public class InvalidAccessExtensionException extends RuntimeException {

    public InvalidAccessExtensionException(String message) {
        super(message);
    }
}
