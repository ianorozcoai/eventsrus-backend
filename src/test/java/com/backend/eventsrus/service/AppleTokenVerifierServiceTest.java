package com.backend.eventsrus.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.backend.eventsrus.config.AppleProperties;
import com.backend.eventsrus.exception.InvalidAppleTokenException;
import io.jsonwebtoken.Jwts;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Uses tokens signed with a throwaway RSA key and a fake key source: no network, no real Apple token. */
class AppleTokenVerifierServiceTest {

    private static final String BUNDLE_ID = "com.eventsrus.eventsrusui";
    private static final String RAW_NONCE = "raw-nonce-123";

    private KeyPair apple;
    private KeyPair stranger;
    private AppleTokenVerifierService verifier;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        apple = gen.generateKeyPair();
        stranger = gen.generateKeyPair();
        AppleProperties props = new AppleProperties();
        props.setBundleId(BUNDLE_ID);
        verifier = new AppleTokenVerifierService(kid -> "kid-1".equals(kid) ? apple.getPublic() : null, props);
    }

    private String token(KeyPair signWith, String kid, String issuer, String audience, Instant expires, String nonce) {
        var builder = Jwts.builder()
                .header().keyId(kid).and()
                .issuer(issuer)
                .audience().add(audience).and()
                .subject("001234.abcd")
                .issuedAt(new Date())
                .expiration(Date.from(expires))
                .claim("email", "person@example.com")
                .claim("email_verified", "true")
                .claim("is_private_email", "false");
        if (nonce != null) {
            builder.claim("nonce", AppleTokenVerifierService.sha256Hex(nonce));
        }
        return builder.signWith(signWith.getPrivate(), Jwts.SIG.RS256).compact();
    }

    private String valid() {
        return token(apple, "kid-1", "https://appleid.apple.com", BUNDLE_ID, Instant.now().plusSeconds(600), RAW_NONCE);
    }

    @Test
    void aValidTokenGivesTheIdentity() {
        AppleIdentity identity = verifier.verify(valid(), RAW_NONCE);

        assertThat(identity.subject()).isEqualTo("001234.abcd");
        assertThat(identity.email()).isEqualTo("person@example.com");
        assertThat(identity.emailVerified()).isTrue();
        assertThat(identity.privateRelayEmail()).isFalse();
    }

    @Test
    void aTokenSignedWithSomeoneElsesKeyIsRejected() {
        String forged = token(stranger, "kid-1", "https://appleid.apple.com", BUNDLE_ID, Instant.now().plusSeconds(600), RAW_NONCE);

        assertThatThrownBy(() -> verifier.verify(forged, RAW_NONCE)).isInstanceOf(InvalidAppleTokenException.class);
    }

    @Test
    void anUnknownKeyIdIsRejected() {
        String t = token(apple, "kid-other", "https://appleid.apple.com", BUNDLE_ID, Instant.now().plusSeconds(600), RAW_NONCE);

        assertThatThrownBy(() -> verifier.verify(t, RAW_NONCE)).isInstanceOf(InvalidAppleTokenException.class);
    }

    @Test
    void aWrongIssuerIsRejected() {
        String t = token(apple, "kid-1", "https://evil.example.com", BUNDLE_ID, Instant.now().plusSeconds(600), RAW_NONCE);

        assertThatThrownBy(() -> verifier.verify(t, RAW_NONCE)).isInstanceOf(InvalidAppleTokenException.class);
    }

    @Test
    void aTokenForAnotherAppIsRejected() {
        String t = token(apple, "kid-1", "https://appleid.apple.com", "com.someone.else", Instant.now().plusSeconds(600), RAW_NONCE);

        assertThatThrownBy(() -> verifier.verify(t, RAW_NONCE)).isInstanceOf(InvalidAppleTokenException.class);
    }

    @Test
    void anExpiredTokenIsRejected() {
        String t = token(apple, "kid-1", "https://appleid.apple.com", BUNDLE_ID, Instant.now().minusSeconds(60), RAW_NONCE);

        assertThatThrownBy(() -> verifier.verify(t, RAW_NONCE)).isInstanceOf(InvalidAppleTokenException.class);
    }

    @Test
    void aWrongOrMissingNonceIsRejected() {
        assertThatThrownBy(() -> verifier.verify(valid(), "some-other-nonce")).isInstanceOf(InvalidAppleTokenException.class);
        String noNonce = token(apple, "kid-1", "https://appleid.apple.com", BUNDLE_ID, Instant.now().plusSeconds(600), null);
        assertThatThrownBy(() -> verifier.verify(noNonce, RAW_NONCE)).isInstanceOf(InvalidAppleTokenException.class);
    }

    @Test
    void garbageAndBlankInputAreRejected() {
        assertThatThrownBy(() -> verifier.verify("not-a-jwt", RAW_NONCE)).isInstanceOf(InvalidAppleTokenException.class);
        assertThatThrownBy(() -> verifier.verify("", RAW_NONCE)).isInstanceOf(InvalidAppleTokenException.class);
        assertThatThrownBy(() -> verifier.verify(valid(), " ")).isInstanceOf(InvalidAppleTokenException.class);
    }
}
