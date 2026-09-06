package com.backend.eventsrus.exception;

/** Thrown when a vendor whose subscription has lapsed tries to use a gated feature (quotations, chat, bookings). */
public class SubscriptionExpiredException extends RuntimeException {

    public SubscriptionExpiredException(String message) {
        super(message);
    }
}
