package com.backend.eventsrus.exception;

/**
 * Thrown when a planner tries to review a booking they can't - not theirs,
 * not eligible yet, or already reviewed (see ReviewService).
 */
public class ReviewNotAllowedException extends RuntimeException {

    public ReviewNotAllowedException(String message) {
        super(message);
    }
}
