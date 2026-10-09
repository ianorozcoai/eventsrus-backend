package com.backend.eventsrus.exception;

public class SmsApiException extends RuntimeException {

    public SmsApiException(String message) {
        super(message);
    }

    public SmsApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
