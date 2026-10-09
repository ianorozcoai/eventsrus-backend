package com.backend.eventsrus.controller;

import com.backend.eventsrus.dto.SendAdhocSmsRequest;
import com.backend.eventsrus.dto.SmsLogResponse;
import com.backend.eventsrus.service.SmsService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin ad-hoc SMS tool (send to any number) plus a send history - protected
 * by SecurityConfig's existing /api/v1/admin/** -> hasRole("ADMIN") rule.
 * Backed by eventsrus-web's admin/sms.html.
 */
@RestController
@RequestMapping("/api/v1/admin/sms")
@RequiredArgsConstructor
public class AdminSmsController {

    private final SmsService smsService;

    @PostMapping("/send")
    public SmsLogResponse send(Authentication authentication, @Valid @RequestBody SendAdhocSmsRequest request) {
        return smsService.sendAdhoc(authentication.getName(), request.getNumber(), request.getMessage());
    }

    @GetMapping("/history")
    public List<SmsLogResponse> history() {
        return smsService.recentHistory();
    }
}
