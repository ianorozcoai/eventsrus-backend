package com.backend.eventsrus.controller;

import com.backend.eventsrus.dto.BookingFromQuotationRequest;
import com.backend.eventsrus.dto.BookingProposalRequest;
import com.backend.eventsrus.dto.BookingResponse;
import com.backend.eventsrus.dto.BookingStatusEventResponse;
import com.backend.eventsrus.dto.CancelBookingRequest;
import com.backend.eventsrus.dto.PaymentRejectionRequest;
import com.backend.eventsrus.service.BadgeService;
import com.backend.eventsrus.service.BookingService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;
    private final BadgeService badgeService;

    // See QuotationController#markSeen for why this is its own call rather
    // than a side effect of #listForVendor below.
    @PutMapping("/api/v1/vendors/me/bookings/mark-seen")
    public void markSeen(Authentication authentication) {
        badgeService.markVendorBookingsSeen(authentication.getName());
    }

    @PostMapping("/api/v1/events/{eventId}/vendors/me/bookings")
    public BookingResponse propose(
            @PathVariable Long eventId, @RequestBody BookingProposalRequest request, Authentication authentication) {
        return bookingService.propose(
                authentication.getName(), eventId, request.getPrice(), request.getEventDatetime(), request.getAgreementDetails());
    }

    @PutMapping("/api/v1/bookings/{bookingId}/approve")
    public BookingResponse approve(@PathVariable Long bookingId, Authentication authentication) {
        return bookingService.respond(authentication.getName(), bookingId, true);
    }

    @PutMapping("/api/v1/bookings/{bookingId}/decline")
    public BookingResponse decline(@PathVariable Long bookingId, Authentication authentication) {
        return bookingService.respond(authentication.getName(), bookingId, false);
    }

    @PostMapping("/api/v1/quotations/{quotationId}/book")
    public BookingResponse bookFromQuotation(
            @PathVariable Long quotationId, @Valid @RequestBody BookingFromQuotationRequest request, Authentication authentication) {
        return bookingService.bookFromQuotation(
                authentication.getName(), quotationId, request.getPrice(), request.getEventDatetime(), request.getAgreementDetails());
    }

    @PostMapping(path = "/api/v1/bookings/{bookingId}/payment-screenshot", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public BookingResponse submitPaymentScreenshot(
            @PathVariable Long bookingId, @RequestPart MultipartFile screenshot, Authentication authentication) {
        return bookingService.submitPaymentScreenshot(authentication.getName(), bookingId, screenshot);
    }

    @PostMapping(path = "/api/v1/bookings/{bookingId}/acknowledge-payment", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public BookingResponse acknowledgePayment(
            @PathVariable Long bookingId, @RequestPart MultipartFile invoice, Authentication authentication) {
        return bookingService.acknowledgePayment(authentication.getName(), bookingId, invoice);
    }

    @PutMapping("/api/v1/bookings/{bookingId}/reject-payment")
    public BookingResponse rejectPayment(
            @PathVariable Long bookingId, @Valid @RequestBody PaymentRejectionRequest request, Authentication authentication) {
        return bookingService.rejectPayment(authentication.getName(), bookingId, request.getReason());
    }

    @PutMapping("/api/v1/bookings/{bookingId}/cancel")
    public BookingResponse cancel(
            @PathVariable Long bookingId, @Valid @RequestBody CancelBookingRequest request, Authentication authentication) {
        return bookingService.cancel(authentication.getName(), bookingId, request.getReason());
    }

    @GetMapping("/api/v1/bookings/{bookingId}/history")
    public List<BookingStatusEventResponse> history(@PathVariable Long bookingId, Authentication authentication) {
        return bookingService.history(authentication.getName(), bookingId);
    }

    @GetMapping("/api/v1/events/{eventId}/bookings")
    public List<BookingResponse> listForEvent(@PathVariable Long eventId) {
        return bookingService.listForEvent(eventId);
    }

    @GetMapping("/api/v1/vendors/me/bookings")
    public List<BookingResponse> listForVendor(Authentication authentication) {
        return bookingService.listForVendor(authentication.getName());
    }

    @GetMapping("/api/v1/planners/me/bookings")
    public List<BookingResponse> listForPlanner(Authentication authentication) {
        return bookingService.listForPlanner(authentication.getName());
    }
}
