package com.backend.eventsrus.enums;

/** What a report points at. contentId is a message id, review id, or (for STOREFRONT and USER) a user id. */
public enum ReportContentType {
    MESSAGE,
    REVIEW,
    STOREFRONT,
    USER
}
