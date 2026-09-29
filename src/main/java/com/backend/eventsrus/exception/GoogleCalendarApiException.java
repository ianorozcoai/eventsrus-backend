package com.backend.eventsrus.exception;

/**
 * Never allowed to escape GoogleCalendarService - a Calendar API hiccup
 * (expired/revoked token, transient Google outage) must never block the
 * real booking action it's reacting to. Caught and logged at the call site.
 */
public class GoogleCalendarApiException extends RuntimeException {

    public GoogleCalendarApiException(String message) {
        super(message);
    }

    public GoogleCalendarApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
