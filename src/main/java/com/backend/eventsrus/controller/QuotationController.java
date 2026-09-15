package com.backend.eventsrus.controller;

import com.backend.eventsrus.dto.PaymentRejectionRequest;
import com.backend.eventsrus.dto.QuotationRequest;
import com.backend.eventsrus.dto.QuotationResponse;
import com.backend.eventsrus.dto.QuotationRevisionRequest;
import com.backend.eventsrus.dto.QuotationStatusEventResponse;
import com.backend.eventsrus.enums.PaymentType;
import com.backend.eventsrus.service.QuotationService;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
public class QuotationController {

    private final QuotationService quotationService;

    @PostMapping("/api/v1/events/{eventId}/vendors/{vendorUserId}/quotations")
    public QuotationResponse requestQuotation(
            @PathVariable Long eventId,
            @PathVariable Long vendorUserId,
            @Valid @RequestBody QuotationRequest request,
            Authentication authentication) {
        return quotationService.requestQuotation(
                authentication.getName(), eventId, vendorUserId, request.getTargetDate(), request.getMessage(),
                request.getPackageIds());
    }

    // A vendor starting a brand-new quote directly from a chat thread - see
    // QuotationService#createFromChat. Parallel naming to the existing
    // ConversationController#sendVendorMessage's /vendor-messages endpoint.
    @PostMapping(path = "/api/v1/events/{eventId}/vendor-quotations", consumes = "multipart/form-data")
    public QuotationResponse createFromChat(
            @PathVariable Long eventId,
            @RequestParam(required = false) LocalDate targetDate,
            @RequestParam(required = false) String message,
            @RequestParam(required = false) List<Long> packageIds,
            @RequestPart MultipartFile pdf,
            @RequestParam BigDecimal quotedAmount,
            Authentication authentication) {
        return quotationService.createFromChat(
                authentication.getName(), eventId, targetDate, message, packageIds, pdf, quotedAmount);
    }

    @GetMapping("/api/v1/events/{eventId}/quotations")
    public List<QuotationResponse> listForEvent(@PathVariable Long eventId) {
        return quotationService.listForEvent(eventId);
    }

    @GetMapping("/api/v1/vendors/me/quotations")
    public List<QuotationResponse> listForVendor(Authentication authentication) {
        return quotationService.listForVendor(authentication.getName());
    }

    @GetMapping("/api/v1/planners/me/quotations")
    public List<QuotationResponse> listForPlanner(Authentication authentication) {
        return quotationService.listForPlanner(authentication.getName());
    }

    @PostMapping(path = "/api/v1/vendors/me/quotations/{quotationId}/respond", consumes = "multipart/form-data")
    public QuotationResponse respondWithPdf(
            @PathVariable Long quotationId, @RequestPart MultipartFile pdf,
            @RequestParam(required = false) String message, @RequestParam BigDecimal quotedAmount,
            Authentication authentication) {
        return quotationService.respondWithPdf(authentication.getName(), quotationId, pdf, message, quotedAmount);
    }

    @PutMapping("/api/v1/quotations/{quotationId}/decline")
    public QuotationResponse decline(@PathVariable Long quotationId, Authentication authentication) {
        return quotationService.declineQuotation(authentication.getName(), quotationId);
    }

    @PostMapping("/api/v1/quotations/{quotationId}/revise")
    public QuotationResponse revise(
            @PathVariable Long quotationId, @Valid @RequestBody QuotationRevisionRequest request, Authentication authentication) {
        return quotationService.requestRevision(
                authentication.getName(), quotationId, request.getTargetDate(), request.getMessage(), request.getPackageIds());
    }

    // Planner accepts a QUOTE_SENT/REVISION_SENT quote - screenshot is
    // optional here (same combined UX the old "Book This" modal had); see
    // QuotationService#acceptQuote for the auto-resolve-to-PENDING_DEPOSIT-
    // or-PAYMENT_REVIEW behavior.
    @PostMapping(path = "/api/v1/quotations/{quotationId}/accept", consumes = "multipart/form-data")
    public QuotationResponse accept(
            @PathVariable Long quotationId, @RequestPart(required = false) MultipartFile screenshot,
            Authentication authentication) {
        return quotationService.acceptQuote(authentication.getName(), quotationId, screenshot);
    }

    // Standalone upload - for a planner who accepted without a screenshot
    // and comes back once ready to pay, or resubmitting after a rejection.
    @PostMapping(path = "/api/v1/quotations/{quotationId}/payment-screenshot", consumes = "multipart/form-data")
    public QuotationResponse submitPaymentScreenshot(
            @PathVariable Long quotationId, @RequestPart MultipartFile screenshot, Authentication authentication) {
        return quotationService.submitPaymentScreenshot(authentication.getName(), quotationId, screenshot);
    }

    @PostMapping("/api/v1/quotations/{quotationId}/reject-payment")
    public QuotationResponse rejectPayment(
            @PathVariable Long quotationId, @Valid @RequestBody PaymentRejectionRequest request,
            Authentication authentication) {
        return quotationService.rejectPaymentScreenshot(authentication.getName(), quotationId, request.getReason());
    }

    // Vendor verifies the payment and confirms the booking - creates the
    // actual Booking row for the first time (see QuotationService#acceptBooking).
    @PostMapping(path = "/api/v1/quotations/{quotationId}/accept-booking", consumes = "multipart/form-data")
    public QuotationResponse acceptBooking(
            @PathVariable Long quotationId, @RequestParam(required = false) String confirmationMessage,
            @RequestParam PaymentType paymentType, @RequestPart MultipartFile invoice,
            Authentication authentication) {
        return quotationService.acceptBooking(
                authentication.getName(), quotationId, confirmationMessage, paymentType, invoice);
    }

    @GetMapping("/api/v1/quotations/{quotationId}/history")
    public List<QuotationStatusEventResponse> history(@PathVariable Long quotationId, Authentication authentication) {
        return quotationService.history(authentication.getName(), quotationId);
    }
}
