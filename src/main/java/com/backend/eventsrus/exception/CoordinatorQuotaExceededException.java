package com.backend.eventsrus.exception;

/** Thrown when a planner has hit today's Events Coordinator question limit (see CoordinatorService). */
public class CoordinatorQuotaExceededException extends RuntimeException {

    public CoordinatorQuotaExceededException(String message) {
        super(message);
    }
}
