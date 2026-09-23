package com.backend.eventsrus.exception;

/** Thrown when a non-blank promo code submitted at vendor onboarding doesn't match the configured code. */
public class InvalidPromoCodeException extends RuntimeException {

    public InvalidPromoCodeException(String message) {
        super(message);
    }
}
