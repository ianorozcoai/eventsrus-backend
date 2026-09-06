package com.backend.eventsrus.enums;

/**
 * How a package's price is displayed on the storefront: a single fixed
 * amount, a min-max range, or no amount at all - just "Request for
 * Quotation" (the planner has to ask the vendor directly).
 */
public enum PackagePricingType {
    FIXED,
    RANGE,
    QUOTE
}
