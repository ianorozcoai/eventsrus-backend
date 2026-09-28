package com.backend.eventsrus.controller;

import com.backend.eventsrus.dto.PaymentRejectionRequest;
import com.backend.eventsrus.dto.QuotationRequest;
import com.backend.eventsrus.dto.QuotationResponse;
import com.backend.eventsrus.dto.QuotationStatusEventResponse;
import com.backend.eventsrus.enums.PaymentType;
import com.backend.eventsrus.service.BadgeService;
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
    private final BadgeService badgeService;

    // Loading the vendor's own Quotations page is exactly the "seen it"
    // moment for the nav badge (see BadgeService) - a distinct call the web
    // app's VendorController makes itself when rendering that page, not a
    // side effect of #listForVendor below (which other pages call too, in
    // passing, and must never silently clear a badge nobody actually looked
    // at).
    @PutMapping("/api/v1/vendors/me/quotations/mark-seen")
    public void markSeen(Authentication authentication) {
        badgeService.markVendorQuotationsSeen(authentication.getName());
    }

    // JSON-body version - kept exactly as it was for eventsrus-ui (Flutter),
    // which still POSTs a plain JSON body here (no multipart support added
    // there). Delegates with referenceImages=null. See requestQuotationWithImages
    // below for the web app's multipart version of this same URL - Spring
    // dispatches between the two by request Content-Type, so both clients
    // keep working unmodified.
    @PostMapping(path = "/api/v1/events/{eventId}/vendors/{vendorUserId}/quotations",
            consumes = "application/json")
    public QuotationResponse requestQuotation(
            @PathVariable Long eventId,
            @PathVariable Long vendorUserId,
            @Valid @RequestBody QuotationRequest request,
            Authentication authentication) {
        return quotationService.requestQuotation(
                authentication.getName(), eventId, vendorUserId, request.getTargetDate(), request.getMessage(),
                request.getPackageIds(), null);
    }

    // Web app's version of the same endpoint - optional reference images
    // alongside the same fields, sent as multipart since a file can't ride
    // inside a JSON body.
    @PostMapping(path = "/api/v1/events/{eventId}/vendors/{vendorUserId}/quotations",
            consumes = "multipart/form-data")
    public QuotationResponse requestQuotationWithImages(
            @PathVariable Long eventId,
            @PathVariable Long vendorUserId,
            @RequestParam(required = false) LocalDate targetDate,
            @RequestParam String message,
            @RequestParam(required = false) List<Long> packageIds,
            @RequestPart(required = false) List<MultipartFile> referenceImages,
            Authentication authentication) {
        return quotationService.requestQuotation(
                authentication.getName(), eventId, vendorUserId, targetDate, message, packageIds, referenceImages);
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
            @RequestPart(required = false) List<MultipartFile> images,
            Authentication authentication) {
        return quotationService.createFromChat(
                authentication.getName(), eventId, targetDate, message, packageIds, pdf, quotedAmount, images);
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
            @RequestPart(required = false) List<MultipartFile> images,
            Authentication authentication) {
        return quotationService.respondWithPdf(authentication.getName(), quotationId, pdf, message, quotedAmount, images);
    }

    @PutMapping("/api/v1/quotations/{quotationId}/decline")
    public QuotationResponse decline(@PathVariable Long quotationId, Authentication authentication) {
        return quotationService.declineQuotation(authentication.getName(), quotationId);
    }

    // Multipart, not JSON - eventsrus-ui (Flutter) has no caller for this
    // endpoint at all, so unlike requestQuotation/respondWithPdf there's no
    // existing JSON client to keep working; converting outright (rather than
    // adding a JSON+multipart sibling pair) is safe here.
    @PostMapping(path = "/api/v1/quotations/{quotationId}/revise", consumes = "multipart/form-data")
    public QuotationResponse revise(
            @PathVariable Long quotationId,
            @RequestParam(required = false) LocalDate targetDate,
            @RequestParam String message,
            @RequestParam(required = false) List<Long> packageIds,
            @RequestPart(required = false) List<MultipartFile> images,
            Authentication authentication) {
        return quotationService.requestRevision(
                authentication.getName(), quotationId, targetDate, message, packageIds, images);
    }

    // Planner accepts a QUOTE_SENT/REVISION_SENT quote - screenshot is
    // optional here (same combined UX the old "Book This" modal had);
    // acceptedVersion is optional too (null = the current/latest version),
    // since a planner can choose to lock in an earlier offer instead - see
    // QuotationService#acceptQuote for the auto-resolve-to-PENDING_DEPOSIT-
    // or-PAYMENT_REVIEW behavior.
    @PostMapping(path = "/api/v1/quotations/{quotationId}/accept", consumes = "multipart/form-data")
    public QuotationResponse accept(
            @PathVariable Long quotationId, @RequestParam(required = false) Integer acceptedVersion,
            @RequestParam(required = false) String message, @RequestPart(required = false) MultipartFile screenshot,
            Authentication authentication) {
        return quotationService.acceptQuote(authentication.getName(), quotationId, acceptedVersion, message, screenshot);
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

    // A free-standing image/PDF either side can send at any time, with no
    // status change - see QuotationService#addAttachment. Void response:
    // this doesn't affect the quotation's own state, so there's nothing to
    // hand back beyond the 200 itself; the web app just re-fetches history.
    @PostMapping(path = "/api/v1/quotations/{quotationId}/attachments", consumes = "multipart/form-data")
    public void addAttachment(
            @PathVariable Long quotationId, @RequestPart MultipartFile file,
            @RequestParam(required = false) String message, Authentication authentication) {
        quotationService.addAttachment(authentication.getName(), quotationId, file, message);
    }
}
