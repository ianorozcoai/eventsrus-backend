package com.backend.eventsrus.service;

import com.backend.eventsrus.dto.BookingResponse;
import com.backend.eventsrus.dto.BookingStatusEventResponse;
import com.backend.eventsrus.enums.BookingStatus;
import com.backend.eventsrus.enums.NotificationType;
import com.backend.eventsrus.enums.QuotationStatus;
import com.backend.eventsrus.exception.InvalidFileTypeException;
import com.backend.eventsrus.model.Booking;
import com.backend.eventsrus.model.BookingStatusEvent;
import com.backend.eventsrus.model.Event;
import com.backend.eventsrus.model.Quotation;
import com.backend.eventsrus.model.User;
import com.backend.eventsrus.model.VendorProfile;
import com.backend.eventsrus.repository.BookingRepository;
import com.backend.eventsrus.repository.BookingStatusEventRepository;
import com.backend.eventsrus.repository.EventRepository;
import com.backend.eventsrus.repository.QuotationRepository;
import com.backend.eventsrus.repository.UserRepository;
import com.backend.eventsrus.repository.VendorProfileRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class BookingService {

    private static final Duration PAYMENT_SCREENSHOT_URL_TTL = Duration.ofMinutes(15);
    private static final Duration INVOICE_URL_TTL = Duration.ofMinutes(15);

    // Statuses a booking can never move on from - cancel() and any future
    // terminal-state guard should check against this rather than an
    // ever-growing list of individual != comparisons.
    private static final Set<BookingStatus> TERMINAL_STATUSES =
            Set.of(BookingStatus.DECLINED, BookingStatus.CANCELLED);

    private final BookingRepository bookingRepository;
    private final EventRepository eventRepository;
    private final QuotationRepository quotationRepository;
    private final UserRepository userRepository;
    private final VendorProfileRepository vendorProfileRepository;
    private final NotificationService notificationService;
    private final S3UploadService s3UploadService;
    private final BookingStatusEventRepository bookingStatusEventRepository;
    private final VendorPlanService vendorPlanService;
    private final ReviewService reviewService;

    @Transactional
    public BookingResponse propose(
            String vendorEmail, Long eventId, BigDecimal price, Instant eventDatetime, String agreementDetails) {
        User vendor = requireUser(vendorEmail);
        vendorPlanService.requireActiveSubscription(vendor.getId());
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalStateException("Event not found: " + eventId));

        Booking booking = bookingRepository.save(Booking.builder()
                .event(event)
                .vendorUser(vendor)
                .plannerUser(event.getPlanner())
                .price(price)
                .eventDatetime(eventDatetime)
                .agreementDetails(agreementDetails)
                .status(BookingStatus.PROPOSED)
                .proposedAt(Instant.now())
                .build());

        notificationService.notify(event.getPlanner(), NotificationType.NEW_BOOKING_PROPOSAL,
                "New booking proposal",
                displayName(vendor) + " sent a booking proposal for " + (event.getName() != null ? event.getName() : "your event"),
                "BOOKING", booking.getId());

        recordStatusChange(booking, null, BookingStatus.PROPOSED, vendor, null);
        return toResponse(booking);
    }

    @Transactional
    public BookingResponse respond(String plannerEmail, Long bookingId, boolean approve) {
        User planner = requireUser(plannerEmail);
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalStateException("Booking not found: " + bookingId));
        if (!booking.getPlannerUser().getId().equals(planner.getId())) {
            throw new IllegalStateException("Booking does not belong to the authenticated user");
        }

        BookingStatus oldStatus = booking.getStatus();
        booking.setStatus(approve ? BookingStatus.APPROVED : BookingStatus.DECLINED);
        booking.setRespondedAt(Instant.now());
        bookingRepository.save(booking);

        notificationService.notify(booking.getVendorUser(),
                approve ? NotificationType.BOOKING_APPROVED : NotificationType.BOOKING_DECLINED,
                approve ? "Booking approved" : "Booking declined",
                displayName(planner) + (approve ? " approved" : " declined") + " your booking proposal",
                "BOOKING", booking.getId());

        recordStatusChange(booking, oldStatus, booking.getStatus(), planner, null);
        return toResponse(booking);
    }

    @Transactional
    public BookingResponse bookFromQuotation(
            String plannerEmail, Long quotationId, BigDecimal price, Instant eventDatetime, String agreementDetails) {
        User planner = requireUser(plannerEmail);
        Quotation quotation = quotationRepository.findById(quotationId)
                .orElseThrow(() -> new IllegalStateException("Quotation not found: " + quotationId));
        if (!quotation.getPlannerUser().getId().equals(planner.getId())) {
            throw new IllegalStateException("Quotation does not belong to the authenticated planner");
        }
        if (quotation.getStatus() != QuotationStatus.RESPONDED) {
            throw new IllegalStateException("Quotation has not been responded to yet: " + quotationId);
        }
        if (bookingRepository.existsByQuotationId(quotationId)) {
            throw new IllegalStateException("A booking already exists for quotation: " + quotationId);
        }

        Booking booking = bookingRepository.save(Booking.builder()
                .event(quotation.getEvent())
                .vendorUser(quotation.getVendorUser())
                .plannerUser(planner)
                .quotation(quotation)
                .price(price)
                .eventDatetime(eventDatetime)
                .agreementDetails(agreementDetails)
                .status(BookingStatus.AWAITING_PAYMENT)
                .proposedAt(Instant.now())
                .build());

        recordStatusChange(booking, null, BookingStatus.AWAITING_PAYMENT, planner, null);
        return toResponse(booking);
    }

    @Transactional
    public BookingResponse submitPaymentScreenshot(String plannerEmail, Long bookingId, MultipartFile screenshot) {
        if (screenshot == null || screenshot.isEmpty()) {
            throw new InvalidFileTypeException("A payment screenshot is required");
        }
        User planner = requireUser(plannerEmail);
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalStateException("Booking not found: " + bookingId));
        if (!booking.getPlannerUser().getId().equals(planner.getId())) {
            throw new IllegalStateException("Booking does not belong to the authenticated planner");
        }
        if (booking.getStatus() != BookingStatus.AWAITING_PAYMENT && booking.getStatus() != BookingStatus.PAYMENT_REJECTED) {
            throw new IllegalStateException("Booking is not awaiting a payment screenshot: " + bookingId);
        }

        BookingStatus oldStatus = booking.getStatus();
        String key = s3UploadService
                .upload(screenshot, "bookings/" + booking.getId() + "/payment-screenshot", S3UploadService.Visibility.PRIVATE)
                .key();
        booking.setPaymentScreenshotKey(key);
        booking.setPaymentScreenshotUploadedAt(Instant.now());
        booking.setPaymentRejectionReason(null);
        booking.setStatus(BookingStatus.PAYMENT_SUBMITTED);
        bookingRepository.save(booking);

        notificationService.notify(booking.getVendorUser(), NotificationType.PAYMENT_SUBMITTED,
                "Payment screenshot submitted",
                displayName(planner) + " uploaded a payment screenshot for your review",
                "BOOKING", booking.getId());

        recordStatusChange(booking, oldStatus, BookingStatus.PAYMENT_SUBMITTED, planner, null);
        return toResponse(booking);
    }

    @Transactional
    public BookingResponse acknowledgePayment(String vendorEmail, Long bookingId, MultipartFile invoice) {
        if (invoice == null || invoice.isEmpty()) {
            throw new InvalidFileTypeException("An invoice or receipt document is required to acknowledge this payment");
        }
        User vendor = requireUser(vendorEmail);
        vendorPlanService.requireActiveSubscription(vendor.getId());
        Booking booking = requireVendorOwnedSubmittedBooking(vendor, bookingId);

        String key = s3UploadService
                .upload(invoice, "bookings/" + booking.getId() + "/invoice", S3UploadService.Visibility.PRIVATE)
                .key();
        booking.setInvoiceKey(key);
        booking.setInvoiceUploadedAt(Instant.now());
        booking.setStatus(BookingStatus.BOOKED);
        booking.setPaymentAcknowledgedAt(Instant.now());
        bookingRepository.save(booking);

        notificationService.notify(booking.getPlannerUser(), NotificationType.BOOKING_CONFIRMED,
                "Booking confirmed",
                displayName(vendor) + " confirmed receipt of your payment and attached an invoice - your booking is confirmed",
                "BOOKING", booking.getId());

        recordStatusChange(booking, BookingStatus.PAYMENT_SUBMITTED, BookingStatus.BOOKED, vendor, null);
        return toResponse(booking);
    }

    @Transactional
    public BookingResponse rejectPayment(String vendorEmail, Long bookingId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalStateException("A rejection reason is required");
        }
        User vendor = requireUser(vendorEmail);
        Booking booking = requireVendorOwnedSubmittedBooking(vendor, bookingId);

        booking.setStatus(BookingStatus.PAYMENT_REJECTED);
        booking.setPaymentRejectionReason(reason);
        bookingRepository.save(booking);

        notificationService.notify(booking.getPlannerUser(), NotificationType.PAYMENT_REJECTED,
                "Payment screenshot rejected",
                displayName(vendor) + " rejected your payment screenshot: " + reason,
                "BOOKING", booking.getId());

        recordStatusChange(booking, BookingStatus.PAYMENT_SUBMITTED, BookingStatus.PAYMENT_REJECTED, vendor, reason);
        return toResponse(booking);
    }

    /**
     * Either participant can cancel a booking from any non-terminal status,
     * with a mandatory reason - fixes BookingStatus.CANCELLED having existed
     * with no code path that ever set it. Once BOOKED, this doesn't handle
     * refunds (payment here is manual/screenshot-based, same as everywhere
     * else in this flow) - the reason and the vendor's own posted
     * cancellation/refund policy (VendorProfile#cancellationPolicyUrl/
     * refundTermsUrl) are what the two parties settle it against off-platform.
     */
    @Transactional
    public BookingResponse cancel(String requesterEmail, Long bookingId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalStateException("A cancellation reason is required");
        }
        User requester = requireUser(requesterEmail);
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalStateException("Booking not found: " + bookingId));
        boolean isParticipant = booking.getVendorUser().getId().equals(requester.getId())
                || booking.getPlannerUser().getId().equals(requester.getId());
        if (!isParticipant) {
            throw new IllegalStateException("Booking does not belong to the authenticated user");
        }
        if (TERMINAL_STATUSES.contains(booking.getStatus())) {
            throw new IllegalStateException("Booking is already in a terminal state: " + booking.getStatus());
        }

        BookingStatus oldStatus = booking.getStatus();
        booking.setStatus(BookingStatus.CANCELLED);
        booking.setCancelledAt(Instant.now());
        booking.setCancellationReason(reason);
        booking.setCancelledBy(requester);
        bookingRepository.save(booking);

        User otherParty = booking.getVendorUser().getId().equals(requester.getId())
                ? booking.getPlannerUser()
                : booking.getVendorUser();
        notificationService.notify(otherParty, NotificationType.BOOKING_CANCELLED,
                "Booking cancelled",
                displayName(requester) + " cancelled the booking: " + reason,
                "BOOKING", booking.getId());

        recordStatusChange(booking, oldStatus, BookingStatus.CANCELLED, requester, reason);
        return toResponse(booking);
    }

    @Transactional(readOnly = true)
    public List<BookingStatusEventResponse> history(String requesterEmail, Long bookingId) {
        User requester = requireUser(requesterEmail);
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalStateException("Booking not found: " + bookingId));
        boolean isParticipant = booking.getVendorUser().getId().equals(requester.getId())
                || booking.getPlannerUser().getId().equals(requester.getId());
        if (!isParticipant) {
            throw new IllegalStateException("Booking does not belong to the authenticated user");
        }

        return bookingStatusEventRepository.findByBookingIdOrderByCreatedAtAsc(bookingId).stream()
                .map(this::toStatusEventResponse)
                .toList();
    }

    /** Called at the tail of every transition above - {@code fromStatus} is null for a booking's initial creation event. */
    private void recordStatusChange(
            Booking booking, BookingStatus fromStatus, BookingStatus toStatus, User changedBy, String reason) {
        bookingStatusEventRepository.save(BookingStatusEvent.builder()
                .booking(booking)
                .fromStatus(fromStatus)
                .toStatus(toStatus)
                .changedBy(changedBy)
                .reason(reason)
                .build());
    }

    private BookingStatusEventResponse toStatusEventResponse(BookingStatusEvent event) {
        return BookingStatusEventResponse.builder()
                .id(event.getId())
                .fromStatus(event.getFromStatus())
                .toStatus(event.getToStatus())
                .changedByUserId(event.getChangedBy().getId())
                .changedByName(displayName(event.getChangedBy()))
                .reason(event.getReason())
                .createdAt(event.getCreatedAt())
                .build();
    }

    private Booking requireVendorOwnedSubmittedBooking(User vendor, Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalStateException("Booking not found: " + bookingId));
        if (!booking.getVendorUser().getId().equals(vendor.getId())) {
            throw new IllegalStateException("Booking does not belong to the authenticated vendor");
        }
        if (booking.getStatus() != BookingStatus.PAYMENT_SUBMITTED) {
            throw new IllegalStateException("Booking has no payment screenshot awaiting review: " + bookingId);
        }
        return booking;
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> listForEvent(Long eventId) {
        return bookingRepository.findByEventIdOrderByCreatedAtDesc(eventId).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> listForVendor(String vendorEmail) {
        User vendor = requireUser(vendorEmail);
        return bookingRepository.findByVendorUserIdOrderByEventDatetimeAsc(vendor.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> listForPlanner(String plannerEmail) {
        User planner = requireUser(plannerEmail);
        return bookingRepository.findByPlannerUserIdOrderByEventDatetimeAsc(planner.getId()).stream()
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

    private BookingResponse toResponse(Booking booking) {
        VendorProfile vendorProfile = vendorProfileRepository.findByUserId(booking.getVendorUser().getId()).orElse(null);
        var existingReview = reviewService.existingReview(booking.getId());
        return BookingResponse.builder()
                .id(booking.getId())
                .eventId(booking.getEvent().getId())
                .eventName(booking.getEvent().getName())
                .vendorUserId(booking.getVendorUser().getId())
                .vendorBusinessName(vendorProfile != null ? vendorProfile.getBusinessName() : null)
                .vendorSlug(vendorProfile != null ? vendorProfile.getSlug() : null)
                .plannerUserId(booking.getPlannerUser().getId())
                .quotationId(booking.getQuotation() != null ? booking.getQuotation().getId() : null)
                .price(booking.getPrice())
                .eventDatetime(booking.getEventDatetime())
                .agreementDetails(booking.getAgreementDetails())
                .status(booking.getStatus())
                .proposedAt(booking.getProposedAt())
                .respondedAt(booking.getRespondedAt())
                .paymentScreenshotUrl(s3UploadService.presignedUrl(booking.getPaymentScreenshotKey(), PAYMENT_SCREENSHOT_URL_TTL))
                .paymentScreenshotUploadedAt(booking.getPaymentScreenshotUploadedAt())
                .paymentAcknowledgedAt(booking.getPaymentAcknowledgedAt())
                .paymentRejectionReason(booking.getPaymentRejectionReason())
                .invoiceUrl(s3UploadService.presignedUrl(booking.getInvoiceKey(), INVOICE_URL_TTL))
                .invoiceUploadedAt(booking.getInvoiceUploadedAt())
                .cancelledAt(booking.getCancelledAt())
                .cancellationReason(booking.getCancellationReason())
                .cancelledByUserId(booking.getCancelledBy() != null ? booking.getCancelledBy().getId() : null)
                .reviewId(existingReview != null ? existingReview.getId() : null)
                .reviewRating(existingReview != null ? existingReview.getRating() : null)
                .reviewComment(existingReview != null ? existingReview.getComment() : null)
                .canReview(existingReview == null && reviewService.isReviewable(booking))
                .build();
    }
}
