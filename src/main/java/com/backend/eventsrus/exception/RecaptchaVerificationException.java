package com.backend.eventsrus.exception;

/** Thrown when a submitted reCAPTCHA token fails Google's verification or scores below the required threshold. */
public class RecaptchaVerificationException extends RuntimeException {

    public RecaptchaVerificationException(String message) {
        super(message);
    }
}
