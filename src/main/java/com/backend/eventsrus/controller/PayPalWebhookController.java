package com.backend.eventsrus.controller;

import com.backend.eventsrus.service.PayPalSubscriptionClient;
import com.backend.eventsrus.service.PayPalWebhookService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@RestController
@RequestMapping("/api/v1/webhooks/paypal")
@RequiredArgsConstructor
public class PayPalWebhookController {

    private final PayPalSubscriptionClient payPalSubscriptionClient;
    private final PayPalWebhookService payPalWebhookService;
    private final ObjectMapper objectMapper;

    @PostMapping
    public ResponseEntity<Void> receive(@RequestBody String rawBody, HttpServletRequest request) {
        String transmissionId = request.getHeader("PAYPAL-TRANSMISSION-ID");
        String transmissionTime = request.getHeader("PAYPAL-TRANSMISSION-TIME");
        String certUrl = request.getHeader("PAYPAL-CERT-URL");
        String authAlgo = request.getHeader("PAYPAL-AUTH-ALGO");
        String transmissionSig = request.getHeader("PAYPAL-TRANSMISSION-SIG");

        if (transmissionId == null
                || transmissionTime == null
                || certUrl == null
                || authAlgo == null
                || transmissionSig == null) {
            log.warn("Rejected PayPal webhook missing required signature headers");
            return ResponseEntity.badRequest().build();
        }

        boolean verified = payPalSubscriptionClient.verifyWebhookSignature(
                transmissionId, transmissionTime, certUrl, authAlgo, transmissionSig, rawBody);

        if (!verified) {
            log.warn("Rejected PayPal webhook with invalid signature");
            return ResponseEntity.badRequest().build();
        }

        payPalWebhookService.handle(objectMapper.readTree(rawBody));
        return ResponseEntity.ok().build();
    }
}
