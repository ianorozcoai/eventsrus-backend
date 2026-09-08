package com.backend.eventsrus.controller;

import com.backend.eventsrus.dto.CreateTicketRequest;
import com.backend.eventsrus.dto.ReplyToTicketRequest;
import com.backend.eventsrus.dto.SupportTicketMessageResponse;
import com.backend.eventsrus.dto.SupportTicketResponse;
import com.backend.eventsrus.dto.UpdateTicketStatusRequest;
import com.backend.eventsrus.service.SupportTicketService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Support tickets - symmetric for planners and vendors (any authenticated
 * user can raise one, reply to their own, and list their own). No
 * admin-facing UI exists yet, so #updateStatus is real and role-checked in
 * the service but has no frontend caller today.
 */
@RestController
@RequiredArgsConstructor
public class SupportTicketController {

    private final SupportTicketService supportTicketService;

    // multipart, not JSON, so a screenshot can ride along with the opening
    // complaint - see SupportTicketService#postMessage for the actual
    // upload/validation.
    @PostMapping(value = "/api/v1/support-tickets", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public SupportTicketResponse createTicket(
            @Valid @ModelAttribute CreateTicketRequest request,
            @RequestPart(required = false) MultipartFile attachment,
            Authentication authentication) {
        return supportTicketService.createTicket(
                authentication.getName(), request.getSubject(), request.getCategory(), request.getMessage(),
                request.getRelatedEventId(), request.getRelatedBookingId(), request.getRelatedQuotationId(), attachment);
    }

    @GetMapping("/api/v1/support-tickets/me")
    public List<SupportTicketResponse> listForUser(Authentication authentication) {
        return supportTicketService.listForUser(authentication.getName());
    }

    @GetMapping("/api/v1/support-tickets/{ticketId}")
    public SupportTicketResponse getTicket(@PathVariable Long ticketId, Authentication authentication) {
        return supportTicketService.getTicket(authentication.getName(), ticketId);
    }

    @GetMapping("/api/v1/support-tickets/{ticketId}/messages")
    public List<SupportTicketMessageResponse> getMessages(@PathVariable Long ticketId, Authentication authentication) {
        return supportTicketService.getMessages(authentication.getName(), ticketId);
    }

    // multipart, not JSON - same reasoning as createTicket above.
    @PostMapping(value = "/api/v1/support-tickets/{ticketId}/messages", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public SupportTicketResponse reply(
            @PathVariable Long ticketId,
            @Valid @ModelAttribute ReplyToTicketRequest request,
            @RequestPart(required = false) MultipartFile attachment,
            Authentication authentication) {
        return supportTicketService.reply(authentication.getName(), ticketId, request.getMessage(), attachment);
    }

    @PutMapping("/api/v1/support-tickets/{ticketId}/status")
    public SupportTicketResponse updateStatus(
            @PathVariable Long ticketId, @Valid @RequestBody UpdateTicketStatusRequest request, Authentication authentication) {
        return supportTicketService.updateStatus(authentication.getName(), ticketId, request.getStatus());
    }
}
