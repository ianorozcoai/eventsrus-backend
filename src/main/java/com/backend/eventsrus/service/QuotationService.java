package com.backend.eventsrus.service;

import com.backend.eventsrus.dto.QuotationResponse;
import com.backend.eventsrus.dto.QuotationStatusEventResponse;
import com.backend.eventsrus.enums.BookingStatus;
import com.backend.eventsrus.enums.NotificationType;
import com.backend.eventsrus.enums.PaymentType;
import com.backend.eventsrus.enums.QuotationStatus;
import com.backend.eventsrus.exception.InvalidFileTypeException;
import com.backend.eventsrus.exception.QuotationConflictException;
import com.backend.eventsrus.model.Booking;
import com.backend.eventsrus.model.BookingStatusEvent;
import com.backend.eventsrus.model.Event;
import com.backend.eventsrus.model.Quotation;
import com.backend.eventsrus.model.QuotationStatusEvent;
import com.backend.eventsrus.model.User;
import com.backend.eventsrus.model.VendorPackage;
import com.backend.eventsrus.model.VendorProfile;
import com.backend.eventsrus.repository.BookingRepository;
import com.backend.eventsrus.repository.BookingStatusEventRepository;
import com.backend.eventsrus.repository.EventRepository;
import com.backend.eventsrus.repository.QuotationRepository;
import com.backend.eventsrus.repository.QuotationStatusEventRepository;
import com.backend.eventsrus.repository.UserRepository;
import com.backend.eventsrus.repository.VendorPackageRepository;
import com.backend.eventsrus.repository.VendorProfileRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class QuotationService {

    private static final Duration FILE_URL_TTL = Duration.ofMinutes(15);
    private static final ZoneId MANILA = ZoneId.of("Asia/Manila");

    // Once a quote leaves this set, it's frozen - no more revisions or
    // decline (see QuotationStatus javadoc / the Booking Conversion rule).
    private static final Set<QuotationStatus> OPEN_NEGOTIATION_STATUSES = Set.of(
            QuotationStatus.REQUEST_FOR_QUOTE, QuotationStatus.QUOTE_SENT,
            QuotationStatus.REVISION_REQUESTED, QuotationStatus.REVISION_SENT);

    private final QuotationRepository quotationRepository;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final VendorProfileRepository vendorProfileRepository;
    private final VendorPackageRepository vendorPackageRepository;
    private final NotificationService notificationService;
    private final S3UploadService s3UploadService;
    private final QuotationStatusEventRepository quotationStatusEventRepository;
    private final BookingRepository bookingRepository;
    private final BookingStatusEventRepository bookingStatusEventRepository;
    private final VendorPlanService vendorPlanService;

    @Transactional
    public QuotationResponse requestQuotation(
            String plannerEmail, Long eventId, Long vendorUserId, LocalDate targetDate, String message,
            List<Long> packageIds) {
        User planner = requireUser(plannerEmail);
        User vendor = userRepository.findById(vendorUserId)
                .orElseThrow(() -> new IllegalStateException("Vendor not found: " + vendorUserId));
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalStateException("Event not found: " + eventId));

        List<Long> validPackageIds = validatePackageIds(vendorUserId, packageIds);

        Quotation quotation = quotationRepository.save(Quotation.builder()
                .event(event)
                .vendorUser(vendor)
                .plannerUser(planner)
                .targetDate(targetDate)
                .requestMessage(message)
                .status(QuotationStatus.REQUEST_FOR_QUOTE)
                .version(1)
                .packageIds(validPackageIds)
                .build());

        notificationService.notify(vendor, NotificationType.NEW_QUOTATION_REQUEST,
                "New quotation request",
                displayName(planner) + " requested a quotation for their event",
                "QUOTATION", quotation.getId());

        recordStatusChange(quotation, null, QuotationStatus.REQUEST_FOR_QUOTE, planner, null, null, null,
                targetDate, validPackageIds);
        return toResponse(quotation);
    }

    @Transactional
    public QuotationResponse respondWithPdf(
            String vendorEmail, Long quotationId, MultipartFile pdf, String message, BigDecimal quotedAmount) {
        User vendor = requireUser(vendorEmail);
        vendorPlanService.requireActiveSubscription(vendor.getId());
        Quotation quotation = quotationRepository.findById(quotationId)
                .orElseThrow(() -> new IllegalStateException("Quotation not found: " + quotationId));
        if (!quotation.getVendorUser().getId().equals(vendor.getId())) {
            throw new IllegalStateException("Quotation does not belong to the authenticated vendor");
        }
        QuotationStatus oldStatus = quotation.getStatus();
        QuotationStatus newStatus = switch (oldStatus) {
            case REQUEST_FOR_QUOTE -> QuotationStatus.QUOTE_SENT;
            case REVISION_REQUESTED -> QuotationStatus.REVISION_SENT;
            default -> throw new IllegalStateException("Quotation is not awaiting a vendor response: " + quotationId);
        };

        String key = s3UploadService
                .upload(pdf, "quotations/" + quotation.getId(), S3UploadService.Visibility.PRIVATE)
                .key();
        quotation.setPdfKey(key);
        quotation.setQuotedAmount(quotedAmount);
        quotation.setStatus(newStatus);
        quotation.setRespondedAt(Instant.now());
        quotation.setVersion(quotation.getVersion() + 1);
        quotationRepository.save(quotation);

        notificationService.notify(quotation.getPlannerUser(), NotificationType.NEW_QUOTATION_RESPONSE,
                "Quotation received",
                displayName(vendor) + " sent you a quotation PDF",
                "QUOTATION", quotation.getId());

        // message, key and quotedAmount all land on this transition's audit
        // row (not just the quotation itself) so this exact version - what
        // was said, what was sent, how much - stays reachable even after a
        // later response supersedes it. See #history.
        recordStatusChange(quotation, oldStatus, newStatus, vendor, message, key, quotedAmount,
                quotation.getTargetDate(), quotation.getPackageIds());
        return toResponse(quotation);
    }

    /**
     * A vendor starting a brand-new quote directly from a chat thread - for
     * when negotiation happened in conversation with no prior
     * REQUEST_FOR_QUOTE from the storefront. Refuses if any quotation
     * already exists for this (event, vendor) pair - that history already
     * has a thread of its own (see #requestRevision), and a second
     * independent thread for the same event+vendor would just be confusing
     * for both sides to track.
     */
    @Transactional
    public QuotationResponse createFromChat(
            String vendorEmail, Long eventId, LocalDate targetDate, String message, List<Long> packageIds,
            MultipartFile pdf, BigDecimal quotedAmount) {
        User vendor = requireUser(vendorEmail);
        vendorPlanService.requireActiveSubscription(vendor.getId());
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalStateException("Event not found: " + eventId));
        if (quotationRepository.existsByEventIdAndVendorUserId(eventId, vendor.getId())) {
            throw new QuotationConflictException(
                    "A quotation already exists for this event - use the existing quote's revision flow instead");
        }

        List<Long> validPackageIds = validatePackageIds(vendor.getId(), packageIds);
        Quotation quotation = quotationRepository.save(Quotation.builder()
                .event(event)
                .vendorUser(vendor)
                .plannerUser(event.getPlanner())
                .targetDate(targetDate)
                .requestMessage(message)
                .status(QuotationStatus.QUOTE_SENT)
                .version(1)
                .quotedAmount(quotedAmount)
                .respondedAt(Instant.now())
                .packageIds(validPackageIds)
                .build());

        String key = s3UploadService
                .upload(pdf, "quotations/" + quotation.getId(), S3UploadService.Visibility.PRIVATE)
                .key();
        quotation.setPdfKey(key);
        quotationRepository.save(quotation);

        notificationService.notify(event.getPlanner(), NotificationType.NEW_QUOTATION_RESPONSE,
                "New quotation",
                displayName(vendor) + " sent you a quotation from your chat",
                "QUOTATION", quotation.getId());

        recordStatusChange(quotation, null, QuotationStatus.QUOTE_SENT, vendor, message, key, quotedAmount,
                targetDate, validPackageIds);
        return toResponse(quotation);
    }

    /**
     * A planner formally closing out a quote without booking it - either
     * they went with a different vendor or decided not to proceed. Only
     * possible pre-acceptance (the Booking Conversion rule) - once
     * QUOTE_ACCEPTED or later, the quote is frozen; cancel the booking
     * itself instead (see BookingService#cancel) once one exists.
     */
    @Transactional
    public QuotationResponse declineQuotation(String plannerEmail, Long quotationId) {
        User planner = requireUser(plannerEmail);
        Quotation quotation = requirePlannerOwnedQuotation(planner, quotationId);
        if (!OPEN_NEGOTIATION_STATUSES.contains(quotation.getStatus())) {
            throw new IllegalStateException(
                    "Quotation can no longer be declined once accepted (or is already declined): " + quotationId);
        }

        QuotationStatus oldStatus = quotation.getStatus();
        quotation.setStatus(QuotationStatus.DECLINED);
        quotation.setDeclinedAt(Instant.now());
        quotationRepository.save(quotation);

        notificationService.notify(quotation.getVendorUser(), NotificationType.QUOTATION_DECLINED,
                "Quotation declined",
                displayName(planner) + " won't be proceeding with this quotation",
                "QUOTATION", quotation.getId());

        recordStatusChange(quotation, oldStatus, QuotationStatus.DECLINED, planner, null, null, null, null, null);
        return toResponse(quotation);
    }

    /**
     * Sends a sent quotation back to the vendor with an updated ask
     * (message/date/packages) instead of the planner having to start a
     * whole new quotation thread - e.g. "actually, drop the photobooth
     * package and add catering." The vendor calls #respondWithPdf again on
     * this same quotation id. Only valid pre-acceptance; once accepted, use
     * a BookingAmendment instead once a booking exists (see
     * BookingAmendmentService) since that's changing a live contract, not a
     * still-open ask.
     */
    @Transactional
    public QuotationResponse requestRevision(
            String plannerEmail, Long quotationId, LocalDate targetDate, String message, List<Long> packageIds) {
        User planner = requireUser(plannerEmail);
        Quotation quotation = requirePlannerOwnedQuotation(planner, quotationId);
        if (quotation.getStatus() != QuotationStatus.QUOTE_SENT && quotation.getStatus() != QuotationStatus.REVISION_SENT) {
            throw new IllegalStateException("Only a sent quotation can be revised: " + quotationId);
        }

        String previousMessage = quotation.getRequestMessage();
        quotation.setRequestMessage(message);
        if (targetDate != null) {
            quotation.setTargetDate(targetDate);
        }
        List<Long> newPackageIds = quotation.getPackageIds();
        if (packageIds != null) {
            newPackageIds = validatePackageIds(quotation.getVendorUser().getId(), packageIds);
            quotation.setPackageIds(newPackageIds);
        }
        // The old PDF response no longer answers the revised ask.
        quotation.setPdfKey(null);
        quotation.setRespondedAt(null);
        QuotationStatus oldStatus = quotation.getStatus();
        quotation.setStatus(QuotationStatus.REVISION_REQUESTED);
        quotation.setVersion(quotation.getVersion() + 1);
        quotationRepository.save(quotation);

        notificationService.notify(quotation.getVendorUser(), NotificationType.NEW_QUOTATION_REQUEST,
                "Quotation revision requested",
                displayName(planner) + " asked for a revised quotation: " + message,
                "QUOTATION", quotation.getId());

        String reason = "Revision requested: " + message
                + (previousMessage != null ? " (previous ask: " + previousMessage + ")" : "");
        recordStatusChange(quotation, oldStatus, QuotationStatus.REVISION_REQUESTED, planner, reason, null, null,
                quotation.getTargetDate(), newPackageIds);
        return toResponse(quotation);
    }

    /**
     * Planner accepts a QUOTE_SENT/REVISION_SENT quote - the moment the
     * quote freezes (Booking Conversion rule). Not necessarily the LATEST
     * version: a planner who went back and forth on a revision might still
     * prefer an earlier offer (a lower price, different inclusions) over
     * whatever the vendor sent most recently, so acceptedVersion lets her
     * pick which sent version's terms actually get locked in - null means
     * "the current/latest one", preserving the old behavior. Locking in an
     * older version overwrites the quotation's live amount/date/packages/
     * PDF with that version's own snapshot (see QuotationStatusEvent),
     * exactly as if the vendor had sent that offer last.
     * A payment screenshot can be attached right here (same UX the old
     * "Book This" modal always had); per the spec this is one action that
     * auto-resolves to either PENDING_DEPOSIT (no screenshot yet) or
     * PAYMENT_REVIEW (one attached) - QUOTE_ACCEPTED itself is recorded in
     * the history but never a resting status (see QuotationStatus javadoc).
     */
    @Transactional
    public QuotationResponse acceptQuote(
            String plannerEmail, Long quotationId, Integer acceptedVersion, String message, MultipartFile screenshot) {
        User planner = requireUser(plannerEmail);
        Quotation quotation = requirePlannerOwnedQuotation(planner, quotationId);
        if (quotation.getStatus() != QuotationStatus.QUOTE_SENT && quotation.getStatus() != QuotationStatus.REVISION_SENT) {
            throw new IllegalStateException("Only a sent quotation can be accepted: " + quotationId);
        }

        int versionToAccept = acceptedVersion != null ? acceptedVersion : quotation.getVersion();
        QuotationStatusEvent acceptedOffer = quotationStatusEventRepository
                .findFirstByQuotationIdAndVersionAndToStatusIn(
                        quotationId, versionToAccept, List.of(QuotationStatus.QUOTE_SENT, QuotationStatus.REVISION_SENT))
                .orElseThrow(() -> new IllegalStateException("No sent quote found for version " + versionToAccept));

        QuotationStatus oldStatus = quotation.getStatus();
        // Lock in the CHOSEN version's terms, not necessarily whatever the
        // quotation's live fields currently hold - if she picked an earlier
        // offer, this is what makes that the one that's actually accepted.
        quotation.setQuotedAmount(acceptedOffer.getQuotedAmount());
        quotation.setTargetDate(acceptedOffer.getTargetDate());
        quotation.setPackageIds(new ArrayList<>(acceptedOffer.getPackageIds()));
        quotation.setPdfKey(acceptedOffer.getPdfKey());
        quotation.setAcceptedAt(Instant.now());
        quotationRepository.save(quotation);
        recordStatusChange(quotation, oldStatus, QuotationStatus.QUOTE_ACCEPTED, planner, message,
                acceptedOffer.getPdfKey(), quotation.getQuotedAmount(), quotation.getTargetDate(), quotation.getPackageIds());

        notificationService.notify(quotation.getVendorUser(), NotificationType.QUOTATION_ACCEPTED,
                "Quotation accepted",
                displayName(planner) + " accepted your quotation",
                "QUOTATION", quotation.getId());

        if (screenshot != null && !screenshot.isEmpty()) {
            return doSubmitPaymentScreenshot(quotation, planner, screenshot);
        }

        quotation.setStatus(QuotationStatus.PENDING_DEPOSIT);
        quotationRepository.save(quotation);
        recordStatusChange(quotation, QuotationStatus.QUOTE_ACCEPTED, QuotationStatus.PENDING_DEPOSIT, planner, null,
                null, null, null, null);
        return toResponse(quotation);
    }

    /** Standalone upload, for a planner who accepted without a screenshot and comes back once they're ready to pay - or resubmitting after a rejection. */
    @Transactional
    public QuotationResponse submitPaymentScreenshot(String plannerEmail, Long quotationId, MultipartFile screenshot) {
        if (screenshot == null || screenshot.isEmpty()) {
            throw new InvalidFileTypeException("A payment screenshot is required");
        }
        User planner = requireUser(plannerEmail);
        Quotation quotation = requirePlannerOwnedQuotation(planner, quotationId);
        if (quotation.getStatus() != QuotationStatus.PENDING_DEPOSIT && quotation.getStatus() != QuotationStatus.PAYMENT_REJECTED) {
            throw new IllegalStateException("Quotation is not awaiting a payment screenshot: " + quotationId);
        }
        return doSubmitPaymentScreenshot(quotation, planner, screenshot);
    }

    private QuotationResponse doSubmitPaymentScreenshot(Quotation quotation, User planner, MultipartFile screenshot) {
        QuotationStatus oldStatus = quotation.getStatus();
        String key = s3UploadService
                .upload(screenshot, "quotations/" + quotation.getId() + "/payment-screenshot", S3UploadService.Visibility.PRIVATE)
                .key();
        quotation.setPaymentScreenshotKey(key);
        quotation.setPaymentScreenshotUploadedAt(Instant.now());
        quotation.setPaymentRejectionReason(null);
        quotation.setStatus(QuotationStatus.PAYMENT_REVIEW);
        quotationRepository.save(quotation);

        notificationService.notify(quotation.getVendorUser(), NotificationType.PAYMENT_SUBMITTED,
                "Payment screenshot submitted",
                displayName(planner) + " uploaded a payment screenshot for your review",
                "QUOTATION", quotation.getId());

        recordStatusChange(quotation, oldStatus, QuotationStatus.PAYMENT_REVIEW, planner, null, null, null, null,
                null, key, null);
        return toResponse(quotation);
    }

    @Transactional
    public QuotationResponse rejectPaymentScreenshot(String vendorEmail, Long quotationId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalStateException("A rejection reason is required");
        }
        User vendor = requireUser(vendorEmail);
        Quotation quotation = requireVendorOwnedQuotationInPaymentReview(vendor, quotationId);

        quotation.setStatus(QuotationStatus.PAYMENT_REJECTED);
        quotation.setPaymentRejectionReason(reason);
        quotationRepository.save(quotation);

        notificationService.notify(quotation.getPlannerUser(), NotificationType.PAYMENT_REJECTED,
                "Payment screenshot rejected",
                displayName(vendor) + " rejected your payment screenshot: " + reason,
                "QUOTATION", quotation.getId());

        recordStatusChange(quotation, QuotationStatus.PAYMENT_REVIEW, QuotationStatus.PAYMENT_REJECTED, vendor, reason,
                null, null, null, null);
        return toResponse(quotation);
    }

    /**
     * Vendor verifies the payment and confirms the booking - creates the
     * actual Booking row for the first time here (not any earlier in this
     * flow). The booking starts directly at BOOKED since payment's already
     * been verified - no AWAITING_PAYMENT/PAYMENT_SUBMITTED detour like the
     * old BookingService#bookFromQuotation path (left untouched for
     * whatever already went through it before this rework).
     */
    @Transactional
    public QuotationResponse acceptBooking(
            String vendorEmail, Long quotationId, String confirmationMessage, PaymentType paymentType,
            MultipartFile invoice) {
        if (invoice == null || invoice.isEmpty()) {
            throw new InvalidFileTypeException("An invoice or receipt document is required to confirm this booking");
        }
        if (paymentType == null) {
            throw new IllegalStateException("Payment type (PARTIAL or FULL) is required");
        }
        User vendor = requireUser(vendorEmail);
        vendorPlanService.requireActiveSubscription(vendor.getId());
        Quotation quotation = requireVendorOwnedQuotationInPaymentReview(vendor, quotationId);
        if (bookingRepository.existsByQuotationId(quotationId)) {
            throw new IllegalStateException("A booking already exists for quotation: " + quotationId);
        }

        String invoiceKey = s3UploadService
                .upload(invoice, "quotations/" + quotation.getId() + "/invoice", S3UploadService.Visibility.PRIVATE)
                .key();
        Instant eventDatetime = quotation.getTargetDate() != null
                ? quotation.getTargetDate().atStartOfDay(MANILA).toInstant()
                : null;

        Booking booking = bookingRepository.save(Booking.builder()
                .event(quotation.getEvent())
                .vendorUser(quotation.getVendorUser())
                .plannerUser(quotation.getPlannerUser())
                .quotation(quotation)
                .price(quotation.getQuotedAmount())
                .eventDatetime(eventDatetime)
                .agreementDetails(quotation.getRequestMessage())
                .status(BookingStatus.BOOKED)
                .proposedAt(Instant.now())
                .respondedAt(Instant.now())
                .invoiceKey(invoiceKey)
                .invoiceUploadedAt(Instant.now())
                .paymentAcknowledgedAt(Instant.now())
                .paymentType(paymentType)
                .confirmationMessage(confirmationMessage)
                .build());
        bookingStatusEventRepository.save(BookingStatusEvent.builder()
                .booking(booking)
                .fromStatus(null)
                .toStatus(BookingStatus.BOOKED)
                .changedBy(vendor)
                .reason(confirmationMessage)
                .build());

        quotation.setStatus(QuotationStatus.BOOKED);
        quotationRepository.save(quotation);

        notificationService.notify(quotation.getPlannerUser(), NotificationType.BOOKING_CONFIRMED,
                "Booking confirmed",
                displayName(vendor) + " confirmed your booking"
                        + (confirmationMessage != null ? ": " + confirmationMessage : ""),
                "BOOKING", booking.getId());

        recordStatusChange(quotation, QuotationStatus.PAYMENT_REVIEW, QuotationStatus.BOOKED, vendor,
                confirmationMessage, null, quotation.getQuotedAmount(), quotation.getTargetDate(),
                quotation.getPackageIds(), null, invoiceKey);
        return toResponse(quotation);
    }

    @Transactional(readOnly = true)
    public List<QuotationStatusEventResponse> history(String requesterEmail, Long quotationId) {
        User requester = requireUser(requesterEmail);
        Quotation quotation = quotationRepository.findById(quotationId)
                .orElseThrow(() -> new IllegalStateException("Quotation not found: " + quotationId));
        boolean isParticipant = quotation.getVendorUser().getId().equals(requester.getId())
                || quotation.getPlannerUser().getId().equals(requester.getId());
        if (!isParticipant) {
            throw new IllegalStateException("Quotation does not belong to the authenticated user");
        }

        Long vendorUserId = quotation.getVendorUser().getId();
        return quotationStatusEventRepository.findByQuotationIdOrderByCreatedAtAsc(quotationId).stream()
                .map(event -> toStatusEventResponse(event, vendorUserId))
                .toList();
    }

    private Quotation requirePlannerOwnedQuotation(User planner, Long quotationId) {
        Quotation quotation = quotationRepository.findById(quotationId)
                .orElseThrow(() -> new IllegalStateException("Quotation not found: " + quotationId));
        if (!quotation.getPlannerUser().getId().equals(planner.getId())) {
            throw new IllegalStateException("Quotation does not belong to the authenticated planner");
        }
        return quotation;
    }

    private Quotation requireVendorOwnedQuotationInPaymentReview(User vendor, Long quotationId) {
        Quotation quotation = quotationRepository.findById(quotationId)
                .orElseThrow(() -> new IllegalStateException("Quotation not found: " + quotationId));
        if (!quotation.getVendorUser().getId().equals(vendor.getId())) {
            throw new IllegalStateException("Quotation does not belong to the authenticated vendor");
        }
        if (quotation.getStatus() != QuotationStatus.PAYMENT_REVIEW) {
            throw new IllegalStateException("Quotation has no payment screenshot awaiting review: " + quotationId);
        }
        return quotation;
    }

    // Only keep package ids that are real packages belonging to this vendor -
    // guards against a planner (or a stale form) submitting an id that
    // doesn't match who they're actually requesting a quote from.
    private List<Long> validatePackageIds(Long vendorUserId, List<Long> packageIds) {
        return packageIds == null || packageIds.isEmpty()
                ? Collections.emptyList()
                : vendorPackageRepository.findAllById(packageIds).stream()
                        .filter(pkg -> pkg.getVendorProfile().getUser().getId().equals(vendorUserId))
                        .map(VendorPackage::getId)
                        .toList();
    }

    private void recordStatusChange(
            Quotation quotation, QuotationStatus fromStatus, QuotationStatus toStatus, User changedBy, String reason,
            String pdfKey, BigDecimal quotedAmount, LocalDate targetDate, List<Long> packageIds) {
        recordStatusChange(quotation, fromStatus, toStatus, changedBy, reason, pdfKey, quotedAmount, targetDate,
                packageIds, null, null);
    }

    private void recordStatusChange(
            Quotation quotation, QuotationStatus fromStatus, QuotationStatus toStatus, User changedBy, String reason,
            String pdfKey, BigDecimal quotedAmount, LocalDate targetDate, List<Long> packageIds,
            String paymentScreenshotKey, String invoiceKey) {
        // A fresh ArrayList, never the same List instance backing
        // Quotation#packageIds - passing that reference straight through
        // makes Hibernate see one Java collection object attached to two
        // different entities' collection roles ("Found shared references
        // to a collection"), which blows up on the next flush.
        quotationStatusEventRepository.save(QuotationStatusEvent.builder()
                .quotation(quotation)
                .fromStatus(fromStatus)
                .toStatus(toStatus)
                .changedBy(changedBy)
                .reason(reason)
                .pdfKey(pdfKey)
                .version(quotation.getVersion())
                .quotedAmount(quotedAmount)
                .targetDate(targetDate)
                .packageIds(packageIds == null ? new ArrayList<>() : new ArrayList<>(packageIds))
                .paymentScreenshotKey(paymentScreenshotKey)
                .invoiceKey(invoiceKey)
                .build());
    }

    private QuotationStatusEventResponse toStatusEventResponse(QuotationStatusEvent event, Long vendorUserId) {
        List<String> packageNames = event.getPackageIds().isEmpty()
                ? List.of()
                : vendorPackageRepository.findAllById(event.getPackageIds()).stream()
                        .map(VendorPackage::getName)
                        .toList();
        return QuotationStatusEventResponse.builder()
                .id(event.getId())
                .fromStatus(event.getFromStatus())
                .toStatus(event.getToStatus())
                .changedByUserId(event.getChangedBy().getId())
                .changedByName(historyChangedByName(event.getChangedBy(), vendorUserId))
                .reason(event.getReason())
                .pdfUrl(s3UploadService.presignedUrl(event.getPdfKey(), FILE_URL_TTL))
                .version(event.getVersion())
                .quotedAmount(event.getQuotedAmount())
                .targetDate(event.getTargetDate())
                .packageNames(packageNames)
                .paymentScreenshotUrl(s3UploadService.presignedUrl(event.getPaymentScreenshotKey(), FILE_URL_TTL))
                .invoiceUrl(s3UploadService.presignedUrl(event.getInvoiceKey(), FILE_URL_TTL))
                .createdAt(event.getCreatedAt())
                .build();
    }

    /**
     * The vendor's own business name reads far better in a negotiation
     * history than their personal Google account name/email - "Golden
     * Frame Photo & Films sent a quotation" means something to the
     * planner reading it, their raw account name doesn't. Only applies
     * when the change was made by the vendor side of this quotation; a
     * planner's own entries still show their personal name as before.
     * Falls back to displayName if the vendor somehow has no profile yet.
     */
    private String historyChangedByName(User changedBy, Long vendorUserId) {
        if (!changedBy.getId().equals(vendorUserId)) {
            return displayName(changedBy);
        }
        return vendorProfileRepository.findByUserId(vendorUserId)
                .map(VendorProfile::getBusinessName)
                .orElseGet(() -> displayName(changedBy));
    }

    @Transactional(readOnly = true)
    public List<QuotationResponse> listForEvent(Long eventId) {
        return quotationRepository.findByEventIdOrderByCreatedAtDesc(eventId).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<QuotationResponse> listForVendor(String vendorEmail) {
        User vendor = requireUser(vendorEmail);
        return quotationRepository.findByVendorUserIdOrderByCreatedAtDesc(vendor.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<QuotationResponse> listForPlanner(String plannerEmail) {
        User planner = requireUser(plannerEmail);
        return quotationRepository.findByPlannerUserIdOrderByCreatedAtDesc(planner.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    private User requireUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + email));
    }

    private String displayName(User user) {
        return user.getFirstName() != null ? user.getFirstName() : user.getEmail();
    }

    private QuotationResponse toResponse(Quotation quotation) {
        VendorProfile vendorProfile = vendorProfileRepository.findByUserId(quotation.getVendorUser().getId()).orElse(null);
        List<String> packageNames = quotation.getPackageIds().isEmpty()
                ? List.of()
                : vendorPackageRepository.findAllById(quotation.getPackageIds()).stream()
                        .map(VendorPackage::getName)
                        .toList();
        return QuotationResponse.builder()
                .id(quotation.getId())
                .eventId(quotation.getEvent().getId())
                .eventName(quotation.getEvent().getName())
                .eventType(quotation.getEvent().getEventType())
                .vendorUserId(quotation.getVendorUser().getId())
                .vendorBusinessName(vendorProfile != null ? vendorProfile.getBusinessName() : null)
                .vendorSlug(vendorProfile != null ? vendorProfile.getSlug() : null)
                .plannerUserId(quotation.getPlannerUser().getId())
                .targetDate(quotation.getTargetDate())
                .requestMessage(quotation.getRequestMessage())
                .status(quotation.getStatus())
                .version(quotation.getVersion())
                .quotedAmount(quotation.getQuotedAmount())
                .pdfUrl(s3UploadService.presignedUrl(quotation.getPdfKey(), FILE_URL_TTL))
                .respondedAt(quotation.getRespondedAt())
                .acceptedAt(quotation.getAcceptedAt())
                .paymentScreenshotUrl(s3UploadService.presignedUrl(quotation.getPaymentScreenshotKey(), FILE_URL_TTL))
                .paymentRejectionReason(quotation.getPaymentRejectionReason())
                .createdAt(quotation.getCreatedAt())
                .packageIds(quotation.getPackageIds())
                .packageNames(packageNames)
                .declinedAt(quotation.getDeclinedAt())
                .build();
    }
}
