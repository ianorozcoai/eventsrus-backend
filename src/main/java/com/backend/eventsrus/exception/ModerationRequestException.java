package com.backend.eventsrus.exception;

/** Thrown for an invalid report or block request (e.g. blocking yourself, unknown target); mapped to HTTP 400. */
public class ModerationRequestException extends RuntimeException {

    public ModerationRequestException(String message) {
        super(message);
    }
}
