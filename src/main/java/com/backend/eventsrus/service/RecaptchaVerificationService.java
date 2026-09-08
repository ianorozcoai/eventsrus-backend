package com.backend.eventsrus.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

/**
 * Verifies vendor-onboarding reCAPTCHA v3 tokens against Google's siteverify
 * API. v3 is score-based (0.0 = very likely a bot, 1.0 = very likely human),
 * not a pass/fail challenge like v2 - success=true alone isn't enough, the
 * score also has to clear minScore. See UserService#becomeVendor for how
 * this gets called (verify-if-present, not yet required - see its Javadoc).
 *
 * Deliberately fails OPEN (allows the vendor through) when Google can't be
 * reached at all - a DNS/network blip on our side reaching
 * www.google.com is not evidence the submitter is a bot, and blocking every
 * vendor signup because of our own connectivity hiccup would be a far worse
 * outcome than occasionally skipping the check. One retry first (transient
 * blips are common) before giving up and failing open; only an actual
 * response from Google saying success=false or a low score is treated as a
 * real rejection.
 */
@Slf4j
@Service
public class RecaptchaVerificationService {

    private static final String VERIFY_URL = "https://www.google.com/recaptcha/api/siteverify";
    private static final int MAX_ATTEMPTS = 2;

    private final String secretKey;
    private final double minScore;
    private final RestClient restClient = RestClient.create();

    public RecaptchaVerificationService(
            @Value("${recaptcha.secret-key}") String secretKey,
            @Value("${recaptcha.min-score:0.5}") double minScore) {
        this.secretKey = secretKey;
        this.minScore = minScore;
    }

    /**
     * Returns false only when Google actually responded and said the token
     * failed or scored too low. Returns true both when the token genuinely
     * checks out AND when Google couldn't be reached at all after retrying
     * (see class Javadoc) - callers can't tell those two "true" cases apart,
     * which is intentional: neither should block onboarding.
     */
    public boolean verify(String token) {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("secret", secretKey);
        body.add("response", token);

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                JsonNode result = restClient.post()
                        .uri(VERIFY_URL)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .body(body)
                        .retrieve()
                        .body(JsonNode.class);

                boolean success = result != null && result.path("success").asBoolean(false);
                double score = result != null ? result.path("score").asDouble(0.0) : 0.0;
                if (!success || score < minScore) {
                    log.warn("reCAPTCHA verification rejected: success={} score={} minScore={}", success, score, minScore);
                    return false;
                }
                return true;
            } catch (Exception e) {
                if (attempt < MAX_ATTEMPTS) {
                    log.warn("reCAPTCHA verification call failed (attempt {}/{}), retrying: {}", attempt, MAX_ATTEMPTS, e.toString());
                    continue;
                }
                log.error("reCAPTCHA verification unreachable after {} attempts - failing open (not blocking onboarding)", MAX_ATTEMPTS, e);
                return true;
            }
        }
        return true;
    }
}
