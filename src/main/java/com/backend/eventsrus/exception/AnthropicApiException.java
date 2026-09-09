package com.backend.eventsrus.exception;

/** Thrown when the Claude API call behind the Events Coordinator fails or isn't configured yet. */
public class AnthropicApiException extends RuntimeException {

    public AnthropicApiException(String message) {
        super(message);
    }

    public AnthropicApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
