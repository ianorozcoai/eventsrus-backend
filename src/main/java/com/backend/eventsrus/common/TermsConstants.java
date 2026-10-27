package com.backend.eventsrus.common;

/**
 * The version stamp recorded against a vendor's terms acceptance
 * (User#termsVersion). Bump this whenever the terms text at
 * templates/terms.html (eventsrus-web) changes materially, so past
 * acceptances stay tied to the version of the text the vendor actually saw.
 * It is also what the mobile app checks (TermsAcceptanceService): anyone whose accepted version differs is asked to
 * accept again once. 2026-10-27 = the Terms with the objectionable-content rules and reporting/blocking section.
 */
public final class TermsConstants {

    public static final String CURRENT_VERSION = "2026-10-27";

    private TermsConstants() {
    }
}
