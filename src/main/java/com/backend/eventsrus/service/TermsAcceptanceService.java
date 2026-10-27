package com.backend.eventsrus.service;

import com.backend.eventsrus.common.TermsConstants;
import com.backend.eventsrus.dto.TermsStatusResponse;
import com.backend.eventsrus.model.User;
import com.backend.eventsrus.repository.UserRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "Accept the Terms" in the mobile app. Google Play's user-generated-content policy expects users to agree to terms
 * that forbid objectionable content before they can post, so a signed-in person who has not accepted the current
 * version (TermsConstants.CURRENT_VERSION, the same stamp a supplier records at onboarding) is shown the acceptance
 * screen once (see the app's TermsGate). The text itself lives on the website (/terms).
 */
@Service
@RequiredArgsConstructor
public class TermsAcceptanceService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public TermsStatusResponse status(String email) {
        User user = requireUser(email);
        return new TermsStatusResponse(
                TermsConstants.CURRENT_VERSION.equals(user.getTermsVersion()), TermsConstants.CURRENT_VERSION);
    }

    @Transactional
    public TermsStatusResponse accept(String email) {
        User user = requireUser(email);
        user.setTermsVersion(TermsConstants.CURRENT_VERSION);
        user.setTermsAcceptedAt(Instant.now());
        userRepository.save(user);
        return new TermsStatusResponse(true, TermsConstants.CURRENT_VERSION);
    }

    private User requireUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + email));
    }
}
