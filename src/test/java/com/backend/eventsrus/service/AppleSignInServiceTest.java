package com.backend.eventsrus.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.backend.eventsrus.config.AppleProperties;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AppleSignInServiceTest {

    private AppleProperties props;
    private AppleHttpClient http;
    private TokenEncryptionService encryption;
    private AppleSignInService service;
    private KeyPair signingKey;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("EC");
        gen.initialize(new ECGenParameterSpec("secp256r1"));
        signingKey = gen.generateKeyPair();
        props = new AppleProperties();
        props.setBundleId("com.eventsrus.eventsrusui");
        http = mock(AppleHttpClient.class);
        encryption = mock(TokenEncryptionService.class);
        service = new AppleSignInService(props, http, encryption);
    }

    private void configure() {
        props.setTeamId("TEAM123456");
        props.setKeyId("KEY1234567");
        props.setPrivateKey("-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(signingKey.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----");
    }

    @Test
    void doesNothingAtAllWhileTheAppleKeyIsNotConfigured() {
        assertThat(service.isConfigured()).isFalse();

        assertThat(service.exchangeCodeForEncryptedRefreshToken("code")).isNull();
        service.revokeQuietly("encrypted");

        verifyNoInteractions(http, encryption);
    }

    @Test
    void clientSecretIsAShortLivedEs256JwtForOurApp() throws Exception {
        configure();

        Jws<Claims> jws = Jwts.parser().verifyWith(signingKey.getPublic()).build().parseSignedClaims(service.clientSecret());

        assertThat(jws.getHeader().get("kid")).isEqualTo("KEY1234567");
        assertThat(jws.getPayload().getIssuer()).isEqualTo("TEAM123456");
        assertThat(jws.getPayload().getSubject()).isEqualTo("com.eventsrus.eventsrusui");
        assertThat(jws.getPayload().getAudience()).containsExactly("https://appleid.apple.com");
        assertThat(jws.getPayload().getExpiration().getTime() - jws.getPayload().getIssuedAt().getTime()).isLessThanOrEqualTo(301_000L);
    }

    @Test
    void exchangesTheCodeAndKeepsOnlyAnEncryptedRefreshToken() throws Exception {
        configure();
        when(http.postForm(anyString(), any())).thenReturn(new AppleHttpClient.Result(200, "{\"refresh_token\":\"r-1\",\"access_token\":\"a\"}"));
        when(encryption.encrypt("r-1")).thenReturn("ENC(r-1)");

        assertThat(service.exchangeCodeForEncryptedRefreshToken("one-time-code")).isEqualTo("ENC(r-1)");
        verify(encryption).encrypt("r-1");
    }

    @Test
    void aRefusedExchangeGivesNullInsteadOfFailingTheSignIn() throws Exception {
        configure();
        when(http.postForm(anyString(), any())).thenReturn(new AppleHttpClient.Result(400, "{\"error\":\"invalid_grant\"}"));

        assertThat(service.exchangeCodeForEncryptedRefreshToken("bad")).isNull();
        verify(encryption, never()).encrypt(anyString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void revokeSendsTheDecryptedTokenToApple() throws Exception {
        configure();
        when(encryption.decrypt("ENC(r-1)")).thenReturn("r-1");
        when(http.postForm(anyString(), any())).thenReturn(new AppleHttpClient.Result(200, ""));

        service.revokeQuietly("ENC(r-1)");

        ArgumentCaptor<String> url = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map<String, String>> form = ArgumentCaptor.forClass(Map.class);
        verify(http).postForm(url.capture(), form.capture());
        assertThat(url.getValue()).isEqualTo("https://appleid.apple.com/auth/revoke");
        assertThat(form.getValue()).containsEntry("token", "r-1").containsEntry("token_type_hint", "refresh_token")
                .containsEntry("client_id", "com.eventsrus.eventsrusui");
    }

    @Test
    void revokeNeverThrowsEvenWhenAppleIsDown() throws Exception {
        configure();
        when(encryption.decrypt(anyString())).thenReturn("r-1");
        when(http.postForm(anyString(), any())).thenThrow(new java.io.IOException("down"));

        service.revokeQuietly("ENC(r-1)");
    }
}
