package com.backend.eventsrus.enums;

/**
 * PENDING - a new vendor onboarded using another vendor's referral code, but
 * hasn't paid for anything yet (still on the free trial, or hasn't
 * subscribed at all).
 * CONVERTED - that referred vendor's subscription is now backed by a real
 * PayPal payment (see PayPalWebhookService) - a commission is owed.
 * COMMISSION_PAID - set manually by an admin once the payout has actually
 * been sent; there's no automated disbursement.
 */
public enum ReferralStatus {
    PENDING, CONVERTED, COMMISSION_PAID
}
