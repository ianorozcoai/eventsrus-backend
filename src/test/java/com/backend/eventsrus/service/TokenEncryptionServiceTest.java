package com.backend.eventsrus.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.SecureRandom;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class TokenEncryptionServiceTest {

    private String randomKey() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    @Test
    void decryptsExactlyWhatWasEncrypted() {
        TokenEncryptionService service = new TokenEncryptionService(randomKey());

        String encrypted = service.encrypt("a-refresh-token-value");

        assertThat(service.decrypt(encrypted)).isEqualTo("a-refresh-token-value");
    }

    @Test
    void twoEncryptionsOfTheSamePlaintextProduceDifferentStoredValues() {
        TokenEncryptionService service = new TokenEncryptionService(randomKey());

        String first = service.encrypt("same-value");
        String second = service.encrypt("same-value");

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void failsClosedRatherThanCrashingAtConstructionWhenKeyIsBlank() {
        TokenEncryptionService service = new TokenEncryptionService("");

        assertThatThrownBy(() -> service.encrypt("anything")).isInstanceOf(IllegalStateException.class);
    }
}
