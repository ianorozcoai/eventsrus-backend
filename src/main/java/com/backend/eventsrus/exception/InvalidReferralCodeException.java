package com.backend.eventsrus.exception;

/** Thrown when a non-blank referral code submitted at vendor onboarding doesn't match any vendor's code. */
public class InvalidReferralCodeException extends RuntimeException {

    public InvalidReferralCodeException(String message) {
        super(message);
    }
}
