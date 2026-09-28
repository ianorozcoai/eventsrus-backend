package com.backend.eventsrus.controller;

import com.backend.eventsrus.dto.ConversationMessageResponse;
import com.backend.eventsrus.dto.ConversationSummaryResponse;
import com.backend.eventsrus.dto.InquiryRequest;
import com.backend.eventsrus.dto.SendMessageRequest;
import com.backend.eventsrus.service.ConversationService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;

    // JSON-body version - kept exactly as it was for eventsrus-ui (Flutter),
    // which still POSTs a plain JSON body here. See sendInquiryWithAttachment
    // below for the web app's multipart version of this same URL - Spring
    // dispatches between the two by request Content-Type.
    @PostMapping(path = "/api/v1/events/{eventId}/vendors/{vendorUserId}/inquiries", consumes = "application/json")
    public ConversationMessageResponse sendInquiry(
            @PathVariable Long eventId,
            @PathVariable Long vendorUserId,
            @Valid @RequestBody InquiryRequest request,
            Authentication authentication) {
        return conversationService.sendInquiry(
                authentication.getName(), eventId, vendorUserId, request.getPlannerName(), request.getTargetDate(),
                request.getMessage(), null);
    }

    @PostMapping(path = "/api/v1/events/{eventId}/vendors/{vendorUserId}/inquiries",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ConversationMessageResponse sendInquiryWithAttachment(
            @PathVariable Long eventId,
            @PathVariable Long vendorUserId,
            @Valid @ModelAttribute InquiryRequest request,
            @RequestPart(required = false) MultipartFile attachment,
            Authentication authentication) {
        return conversationService.sendInquiry(
                authentication.getName(), eventId, vendorUserId, request.getPlannerName(), request.getTargetDate(),
                request.getMessage(), attachment);
    }

    // A vendor reaching out first to a lead (see ConversationService#sendVendorMessage) -
    // same find-or-create shape as sendInquiry above, just initiated from
    // the vendor's side instead of the planner's.
    // JSON-body version - kept exactly as it was for eventsrus-ui (Flutter).
    // See sendVendorMessageWithAttachment below for the web app's multipart version.
    @PostMapping(path = "/api/v1/events/{eventId}/vendor-messages", consumes = "application/json")
    public ConversationMessageResponse sendVendorMessage(
            @PathVariable Long eventId, @Valid @RequestBody SendMessageRequest request, Authentication authentication) {
        return conversationService.sendVendorMessage(authentication.getName(), eventId, request.getMessage(), null);
    }

    @PostMapping(path = "/api/v1/events/{eventId}/vendor-messages", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ConversationMessageResponse sendVendorMessageWithAttachment(
            @PathVariable Long eventId, @Valid @ModelAttribute SendMessageRequest request,
            @RequestPart(required = false) MultipartFile attachment, Authentication authentication) {
        return conversationService.sendVendorMessage(authentication.getName(), eventId, request.getMessage(), attachment);
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

    // JSON-body version - kept exactly as it was for eventsrus-ui (Flutter).
    // See sendMessageWithAttachment below for the web app's multipart version.
    @PostMapping(path = "/api/v1/conversations/{conversationId}/messages", consumes = "application/json")
    public ConversationMessageResponse sendMessage(
            @PathVariable Long conversationId,
            @Valid @RequestBody SendMessageRequest request,
            Authentication authentication) {
        return conversationService.sendMessage(authentication.getName(), conversationId, request.getMessage(), null);
    }

    @PostMapping(path = "/api/v1/conversations/{conversationId}/messages",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ConversationMessageResponse sendMessageWithAttachment(
            @PathVariable Long conversationId,
            @Valid @ModelAttribute SendMessageRequest request,
            @RequestPart(required = false) MultipartFile attachment,
            Authentication authentication) {
        return conversationService.sendMessage(authentication.getName(), conversationId, request.getMessage(), attachment);
    }
}
