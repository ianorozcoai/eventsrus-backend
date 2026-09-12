package com.backend.eventsrus.service;

import com.backend.eventsrus.dto.QuotationResponse;
import com.backend.eventsrus.dto.QuotationStatusEventResponse;
import com.backend.eventsrus.enums.NotificationType;
import com.backend.eventsrus.enums.QuotationStatus;
import com.backend.eventsrus.model.Event;
import com.backend.eventsrus.model.Quotation;
import com.backend.eventsrus.model.QuotationStatusEvent;
import com.backend.eventsrus.model.User;
import com.backend.eventsrus.model.VendorPackage;
import com.backend.eventsrus.model.VendorProfile;
import com.backend.eventsrus.repository.BookingRepository;
import com.backend.eventsrus.repository.EventRepository;
import com.backend.eventsrus.repository.QuotationRepository;
import com.backend.eventsrus.repository.QuotationStatusEventRepository;
import com.backend.eventsrus.repository.UserRepository;
import com.backend.eventsrus.repository.VendorPackageRepository;
import com.backend.eventsrus.repository.VendorProfileRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class QuotationService {

    private static final Duration PDF_URL_TTL = Duration.ofMinutes(15);

    private final QuotationRepository quotationRepository;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final VendorProfileRepository vendorProfileRepository;
    private final VendorPackageRepository vendorPackageRepository;
    private final NotificationService notificationService;
    private final S3UploadService s3UploadService;
    private final QuotationStatusEventRepository quotationStatusEventRepository;
    private final BookingRepository bookingRepository;
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
                .status(QuotationStatus.REQUESTED)
                .packageIds(validPackageIds)
                .build());

        notificationService.notify(vendor, NotificationType.NEW_QUOTATION_REQUEST,
                "New quotation request",
                displayName(planner) + " requested a quotation for their event",
                "QUOTATION", quotation.getId());

        recordStatusChange(quotation, null, QuotationStatus.REQUESTED, planner, null);
        return toResponse(quotation);
    }

    @Transactional
    public QuotationResponse respondWithPdf(String vendorEmail, Long quotationId, MultipartFile pdf, String message) {
        User vendor = requireUser(vendorEmail);
        vendorPlanService.requireActiveSubscription(vendor.getId());
        Quotation quotation = quotationRepository.findById(quotationId)
                .orElseThrow(() -> new IllegalStateException("Quotation not found: " + quotationId));
        if (!quotation.getVendorUser().getId().equals(vendor.getId())) {
            throw new IllegalStateException("Quotation does not belong to the authenticated vendor");
        }

        QuotationStatus oldStatus = quotation.getStatus();
        String key = s3UploadService
                .upload(pdf, "quotations/" + quotation.getId(), S3UploadService.Visibility.PRIVATE)
                .key();
        quotation.setPdfKey(key);
        quotation.setStatus(QuotationStatus.RESPONDED);
        quotation.setRespondedAt(Instant.now());
        quotationRepository.save(quotation);

        notificationService.notify(quotation.getPlannerUser(), NotificationType.NEW_QUOTATION_RESPONSE,
                "Quotation received",
                displayName(vendor) + " sent you a quotation PDF",
                "QUOTATION", quotation.getId());

        // message and key both land on this transition's audit row (not
        // just the quotation itself) so this exact version - what was said,
        // what was sent - stays reachable even after a later response
        // overwrites Quotation#pdfKey. See #history.
        recordStatusChange(quotation, oldStatus, QuotationStatus.RESPONDED, vendor, message, key);
        return toResponse(quotation);
    }

    /**
     * A planner formally closing out a quote without booking it - either
     * they went with a different vendor or decided not to proceed. Refuses
     * a quotation that's already been booked (that's a live contract now;
     * cancel the booking itself instead - see BookingService#cancel).
     */
    @Transactional
    public QuotationResponse declineQuotation(String plannerEmail, Long quotationId) {
        User planner = requireUser(plannerEmail);
        Quotation quotation = requirePlannerOwnedQuotation(planner, quotationId);
        if (quotation.getStatus() == QuotationStatus.DECLINED) {
            throw new IllegalStateException("Quotation is already declined: " + quotationId);
        }
        if (bookingRepository.existsByQuotationId(quotationId)) {
            throw new IllegalStateException("Cannot decline a quotation that has already been booked: " + quotationId);
        }

        QuotationStatus oldStatus = quotation.getStatus();
        quotation.setStatus(QuotationStatus.DECLINED);
        quotation.setDeclinedAt(Instant.now());
        quotationRepository.save(quotation);

        notificationService.notify(quotation.getVendorUser(), NotificationType.QUOTATION_DECLINED,
                "Quotation declined",
                displayName(planner) + " won't be proceeding with this quotation",
                "QUOTATION", quotation.getId());

        recordStatusChange(quotation, oldStatus, QuotationStatus.DECLINED, planner, null);
        return toResponse(quotation);
    }

    /**
     * Sends a RESPONDED quotation back to the vendor with an updated ask
     * (message/date/packages) instead of the planner having to start a
     * whole new quotation thread - e.g. "actually, drop the photobooth
     * package and add catering." The vendor calls #respondWithPdf again on
     * this same quotation id. Only valid pre-booking; once booked, use a
     * BookingAmendment instead (see BookingAmendmentService) since that's
     * changing a live contract, not a still-open ask.
     */
    @Transactional
    public QuotationResponse requestRevision(
            String plannerEmail, Long quotationId, LocalDate targetDate, String message, List<Long> packageIds) {
        User planner = requireUser(plannerEmail);
        Quotation quotation = requirePlannerOwnedQuotation(planner, quotationId);
        if (quotation.getStatus() != QuotationStatus.RESPONDED) {
            throw new IllegalStateException("Only a responded quotation can be revised: " + quotationId);
        }
        if (bookingRepository.existsByQuotationId(quotationId)) {
            throw new IllegalStateException(
                    "Cannot revise a quotation that has already been booked - propose a booking amendment instead: " + quotationId);
        }

        String previousMessage = quotation.getRequestMessage();
        quotation.setRequestMessage(message);
        if (targetDate != null) {
            quotation.setTargetDate(targetDate);
        }
        if (packageIds != null) {
            quotation.setPackageIds(validatePackageIds(quotation.getVendorUser().getId(), packageIds));
        }
        // The old PDF response no longer answers the revised ask.
        quotation.setPdfKey(null);
        quotation.setRespondedAt(null);
        quotation.setStatus(QuotationStatus.REQUESTED);
        quotationRepository.save(quotation);

        notificationService.notify(quotation.getVendorUser(), NotificationType.NEW_QUOTATION_REQUEST,
                "Quotation revision requested",
                displayName(planner) + " asked for a revised quotation: " + message,
                "QUOTATION", quotation.getId());

        String reason = "Revision requested: " + message
                + (previousMessage != null ? " (previous ask: " + previousMessage + ")" : "");
        recordStatusChange(quotation, QuotationStatus.RESPONDED, QuotationStatus.REQUESTED, planner, reason);
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

        return quotationStatusEventRepository.findByQuotationIdOrderByCreatedAtAsc(quotationId).stream()
                .map(this::toStatusEventResponse)
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
            Quotation quotation, QuotationStatus fromStatus, QuotationStatus toStatus, User changedBy, String reason) {
        recordStatusChange(quotation, fromStatus, toStatus, changedBy, reason, null);
    }

    private void recordStatusChange(
            Quotation quotation, QuotationStatus fromStatus, QuotationStatus toStatus, User changedBy, String reason,
            String pdfKey) {
        quotationStatusEventRepository.save(QuotationStatusEvent.builder()
                .quotation(quotation)
                .fromStatus(fromStatus)
                .toStatus(toStatus)
                .changedBy(changedBy)
                .reason(reason)
                .pdfKey(pdfKey)
                .build());
    }

    private QuotationStatusEventResponse toStatusEventResponse(QuotationStatusEvent event) {
        return QuotationStatusEventResponse.builder()
                .id(event.getId())
                .fromStatus(event.getFromStatus())
                .toStatus(event.getToStatus())
                .changedByUserId(event.getChangedBy().getId())
                .changedByName(displayName(event.getChangedBy()))
                .reason(event.getReason())
                .pdfUrl(s3UploadService.presignedUrl(event.getPdfKey(), PDF_URL_TTL))
                .createdAt(event.getCreatedAt())
                .build();
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
                .pdfUrl(s3UploadService.presignedUrl(quotation.getPdfKey(), PDF_URL_TTL))
                .respondedAt(quotation.getRespondedAt())
                .createdAt(quotation.getCreatedAt())
                .packageIds(quotation.getPackageIds())
                .packageNames(packageNames)
                .declinedAt(quotation.getDeclinedAt())
                .build();
    }
}
