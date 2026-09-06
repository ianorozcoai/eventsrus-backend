package com.backend.eventsrus.exception;

public class PayPalApiException extends RuntimeException {

    public PayPalApiException(String message) {
        super(message);
    }

    public PayPalApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
