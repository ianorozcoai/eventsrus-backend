package com.backend.eventsrus.service;

import com.backend.eventsrus.exception.InvalidGoogleTokenException;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import java.security.GeneralSecurityException;
import java.util.Collections;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class GoogleTokenVerifierService {

    private final GoogleIdTokenVerifier verifier;

    public GoogleTokenVerifierService(@Value("${google.oauth.client-id}") String clientId) {
        this.verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                .setAudience(Collections.singletonList(clientId))
                .build();
    }

    public GoogleUserInfo verify(String idToken) {
        GoogleIdToken googleIdToken;
        try {
            googleIdToken = verifier.verify(idToken);
        } catch (GeneralSecurityException | java.io.IOException | IllegalArgumentException ex) {
            throw new InvalidGoogleTokenException("Unable to verify Google ID token", ex);
        }

        if (googleIdToken == null) {
            throw new InvalidGoogleTokenException("Invalid Google ID token");
        }

        GoogleIdToken.Payload payload = googleIdToken.getPayload();
        return new GoogleUserInfo(
                payload.getSubject(),
                payload.getEmail(),
                (String) payload.get("given_name"),
                (String) payload.get("family_name"));
    }
}
