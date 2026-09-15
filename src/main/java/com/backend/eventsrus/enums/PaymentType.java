package com.backend.eventsrus.enums;

/** How much of the quoted amount the planner paid at booking time - the vendor picks this when confirming (QuotationService#acceptBooking). */
public enum PaymentType {
    PARTIAL,
    FULL
}
