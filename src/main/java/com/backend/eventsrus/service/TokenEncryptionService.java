package com.backend.eventsrus.service;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * AES-256-GCM encryption at rest for secrets that must be stored (not just
 * hashed) because we need the plaintext back later - currently just a
 * vendor's Google Calendar refresh token (see GoogleCalendarConnection).
 * Each call to encrypt() generates a fresh random 12-byte IV, prepended to
 * the ciphertext before base64-encoding the whole thing, so decrypt() never
 * needs the IV passed separately and two encryptions of the same plaintext
 * never produce the same stored value.
 */
@Service
public class TokenEncryptionService {

    private static final int IV_LENGTH_BYTES = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;

    // Blank until configured (see application.properties) - kept as the raw
    // string rather than eagerly building a SecretKeySpec in the
    // constructor, since an empty/malformed key would throw right there and
    // take down the whole app at startup over one unconfigured integration.
    // Deferred to key() instead, called only when encrypt()/decrypt() is
    // actually invoked.
    private final String base64Key;

    public TokenEncryptionService(@Value("${security.token-encryption-key}") String base64Key) {
        this.base64Key = base64Key;
    }

    private SecretKeySpec key() {
        if (base64Key == null || base64Key.isBlank()) {
            throw new IllegalStateException("Google Calendar sync is not configured (security.token-encryption-key is blank)");
        }
        return new SecretKeySpec(Base64.getDecoder().decode(base64Key), "AES");
    }

    public String encrypt(String plaintext) {
        try {
            SecretKeySpec key = key();
            byte[] iv = new byte[IV_LENGTH_BYTES];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes());

            ByteBuffer buffer = ByteBuffer.allocate(iv.length + ciphertext.length);
            buffer.put(iv).put(ciphertext);
            return Base64.getEncoder().encodeToString(buffer.array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to encrypt token", e);
        }
    }

    public String decrypt(String encoded) {
        try {
            byte[] combined = Base64.getDecoder().decode(encoded);
            ByteBuffer buffer = ByteBuffer.wrap(combined);
            byte[] iv = new byte[IV_LENGTH_BYTES];
            buffer.get(iv);
            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(ciphertext));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to decrypt token", e);
        }
    }
}
