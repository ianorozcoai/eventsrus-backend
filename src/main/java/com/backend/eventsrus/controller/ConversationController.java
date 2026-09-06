package com.backend.eventsrus.controller;

import com.backend.eventsrus.dto.ConversationMessageResponse;
import com.backend.eventsrus.dto.ConversationSummaryResponse;
import com.backend.eventsrus.dto.InquiryRequest;
import com.backend.eventsrus.dto.SendMessageRequest;
import com.backend.eventsrus.service.ConversationService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;

    @PostMapping("/api/v1/events/{eventId}/vendors/{vendorUserId}/inquiries")
    public ConversationMessageResponse sendInquiry(
            @PathVariable Long eventId,
            @PathVariable Long vendorUserId,
            @Valid @RequestBody InquiryRequest request,
            Authentication authentication) {
        return conversationService.sendInquiry(
                authentication.getName(), eventId, vendorUserId, request.getTargetDate(), request.getMessage());
    }

    // A vendor reaching out first to a lead (see ConversationService#sendVendorMessage) -
    // same find-or-create shape as sendInquiry above, just initiated from
    // the vendor's side instead of the planner's.
    @PostMapping("/api/v1/events/{eventId}/vendor-messages")
    public ConversationMessageResponse sendVendorMessage(
            @PathVariable Long eventId, @Valid @RequestBody SendMessageRequest request, Authentication authentication) {
        return conversationService.sendVendorMessage(authentication.getName(), eventId, request.getMessage());
    }

    @GetMapping("/api/v1/conversations")
    public List<ConversationSummaryResponse> listConversations(Authentication authentication) {
        return conversationService.listConversations(authentication.getName());
    }

    @GetMapping("/api/v1/conversations/{conversationId}/messages")
    public List<ConversationMessageResponse> getMessages(
            @PathVariable Long conversationId, Authentication authentication) {
        return conversationService.getMessages(authentication.getName(), conversationId);
    }

    @PostMapping("/api/v1/conversations/{conversationId}/messages")
    public ConversationMessageResponse sendMessage(
            @PathVariable Long conversationId,
            @Valid @RequestBody SendMessageRequest request,
            Authentication authentication) {
        return conversationService.sendMessage(authentication.getName(), conversationId, request.getMessage());
    }
}
