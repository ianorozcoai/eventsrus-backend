package com.backend.eventsrus.controller;

import com.backend.eventsrus.dto.TermsStatusResponse;
import com.backend.eventsrus.service.TermsAcceptanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Terms acceptance for the signed-in person (any role); see TermsAcceptanceService. */
@RestController
@RequestMapping("/api/v1/users/me")
@RequiredArgsConstructor
public class TermsController {

    private final TermsAcceptanceService termsAcceptanceService;

    @GetMapping("/terms")
    public TermsStatusResponse status(Authentication authentication) {
        return termsAcceptanceService.status(authentication.getName());
    }

    @PostMapping("/terms-acceptance")
    public TermsStatusResponse accept(Authentication authentication) {
        return termsAcceptanceService.accept(authentication.getName());
    }
}
