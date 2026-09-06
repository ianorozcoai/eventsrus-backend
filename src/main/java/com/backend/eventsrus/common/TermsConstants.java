package com.backend.eventsrus.common;

/**
 * The version stamp recorded against a vendor's terms acceptance
 * (User#termsVersion). Bump this whenever the terms text at
 * templates/terms.html (eventsrus-web) changes materially, so past
 * acceptances stay tied to the version of the text the vendor actually saw.
 */
public final class TermsConstants {

    public static final String CURRENT_VERSION = "2026-09-03";

    private TermsConstants() {
    }
}
