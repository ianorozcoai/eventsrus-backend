package com.backend.eventsrus.enums;

import java.util.Optional;

/**
 * The business-rule numbers an admin can tune from the admin module's
 * System Settings page without a code deploy - backed by the
 * system_settings table (see SystemSettingService, migration V39). Adding a
 * new tunable value is a two-step change: add the constant here with its
 * default, then read it via SystemSettingService from wherever it used to
 * be a hardcoded constant or an application.properties @Value.
 */
public enum SystemSettingKey {

    VENDOR_TRIAL_DAYS(
            "vendor-trial-days",
            "Vendor free trial length (days)",
            "How many days of full access a new vendor gets before needing to subscribe.",
            "180"),
    SUBSCRIPTION_WARNING_DAYS(
            "subscription-warning-days",
            "Subscription expiry warning window (days)",
            "How many days before a vendor's subscription expires the renewal prompt starts showing.",
            "30"),
    SUBSCRIPTION_GRACE_PERIOD_DAYS(
            "subscription-grace-period-days",
            "Subscription grace period (days)",
            "How many days after a vendor's subscription expires they keep full features and stay recommended to planners before being treated as fully expired.",
            "30"),
    REFERRAL_COMMISSION_AMOUNT(
            "referral-commission-amount",
            "Referral commission amount (PHP)",
            "Flat one-time commission paid to a vendor once someone they referred converts to a paid subscription.",
            "1500"),
    COORDINATOR_DAILY_QUESTION_LIMIT(
            "coordinator-daily-question-limit",
            "AI Coordinator daily question limit",
            "Maximum number of questions a planner can ask the AI Events Coordinator per day.",
            "20"),
    VENDOR_GALLERY_PHOTO_LIMIT(
            "vendor-gallery-photo-limit",
            "Vendor gallery photo limit",
            "Maximum number of standalone (not tied to a package) storefront gallery photos a vendor can upload.",
            "20"),
    VENDOR_PACKAGE_PHOTO_LIMIT(
            "vendor-package-photo-limit",
            "Vendor package photo limit",
            "Maximum number of photos a vendor can attach to a single package.",
            "10");

    private final String key;
    private final String label;
    private final String description;
    private final String defaultValue;

    SystemSettingKey(String key, String label, String description, String defaultValue) {
        this.key = key;
        this.label = label;
        this.description = description;
        this.defaultValue = defaultValue;
    }

    public String key() {
        return key;
    }

    public String label() {
        return label;
    }

    public String description() {
        return description;
    }

    public String defaultValue() {
        return defaultValue;
    }

    public static Optional<SystemSettingKey> fromKey(String key) {
        for (SystemSettingKey value : values()) {
            if (value.key.equals(key)) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }
}
