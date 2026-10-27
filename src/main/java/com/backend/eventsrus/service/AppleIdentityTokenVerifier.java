package com.backend.eventsrus.service;

/** Seam so sign-in code can be tested with a fake verifier instead of real Apple tokens. */
public interface AppleIdentityTokenVerifier {

    /**
     * @param rawNonce the random value the app generated; the token must carry its SHA-256 hex digest
     * @throws com.backend.eventsrus.exception.InvalidAppleTokenException if anything about the token is wrong
     */
    AppleIdentity verify(String identityToken, String rawNonce);
}
