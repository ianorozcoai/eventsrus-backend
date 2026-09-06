package com.backend.eventsrus.service;

import com.backend.eventsrus.dto.BookingAmendmentResponse;
import com.backend.eventsrus.enums.AmendmentStatus;
import com.backend.eventsrus.enums.BookingStatus;
import com.backend.eventsrus.enums.NotificationType;
import com.backend.eventsrus.model.Booking;
import com.backend.eventsrus.model.BookingAmendment;
import com.backend.eventsrus.model.BookingStatusEvent;
import com.backend.eventsrus.model.User;
import com.backend.eventsrus.model.VendorPackage;
import com.backend.eventsrus.repository.BookingAmendmentRepository;
import com.backend.eventsrus.repository.BookingRepository;
import com.backend.eventsrus.repository.BookingStatusEventRepository;
import com.backend.eventsrus.repository.UserRepository;
import com.backend.eventsrus.repository.VendorPackageRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Change orders against an already-BOOKED booking - price/date/details/
 * packages don't get silently overwritten once both parties have confirmed
 * (invoice included); instead one side proposes, the other accepts or
 * rejects. Pre-booking changes go through QuotationService#requestRevision
 * instead - see that class's Javadoc for why the two are kept separate.
 */
@Service
@RequiredArgsConstructor
public class BookingAmendmentService {

    private final BookingAmendmentRepository bookingAmendmentRepository;
    private final BookingRepository bookingRepository;
    private final BookingStatusEventRepository bookingStatusEventRepository;
    private final UserRepository userRepository;
    private final VendorPackageRepository vendorPackageRepository;
    private final NotificationService notificationService;

    @Transactional
    public BookingAmendmentResponse propose(
            String requesterEmail, Long bookingId, BigDecimal newPrice, Instant newEventDatetime,
            String newAgreementDetails, List<Long> newPackageIds, String note) {
        if (note == null || note.isBlank()) {
            throw new IllegalStateException("A note explaining the proposed change is required");
        }
        User requester = requireUser(requesterEmail);
        Booking booking = requireParticipantBooking(requester, bookingId);
        if (booking.getStatus() != BookingStatus.BOOKED) {
            throw new IllegalStateException("Only a BOOKED booking can have an amendment proposed: " + bookingId);
        }
        if (bookingAmendmentRepository.existsByBookingIdAndStatus(bookingId, AmendmentStatus.PENDING)) {
            throw new IllegalStateException("This booking already has a pending amendment awaiting a response");
        }

        List<Long> validPackageIds = newPackageIds == null || newPackageIds.isEmpty()
                ? Collections.emptyList()
                : vendorPackageRepository.findAllById(newPackageIds).stream()
                        .filter(pkg -> pkg.getVendorProfile().getUser().getId().equals(booking.getVendorUser().getId()))
                        .map(VendorPackage::getId)
                        .toList();

        BookingAmendment amendment = bookingAmendmentRepository.save(BookingAmendment.builder()
                .booking(booking)
                .proposedBy(requester)
                .newPrice(newPrice)
                .newEventDatetime(newEventDatetime)
                .newAgreementDetails(newAgreementDetails)
                .newPackageIds(validPackageIds)
                .note(note)
                .status(AmendmentStatus.PENDING)
                .build());

        User otherParty = otherParty(booking, requester);
        notificationService.notify(otherParty, NotificationType.BOOKING_AMENDMENT_PROPOSED,
                "Booking change proposed",
                displayName(requester) + " proposed a change to your booking: " + note,
                "BOOKING", booking.getId());

        return toResponse(amendment);
    }

    @Transactional
    public BookingAmendmentResponse respond(String requesterEmail, Long amendmentId, boolean accept) {
        User requester = requireUser(requesterEmail);
        BookingAmendment amendment = bookingAmendmentRepository.findById(amendmentId)
                .orElseThrow(() -> new IllegalStateException("Amendment not found: " + amendmentId));
        Booking booking = amendment.getBooking();
        requireParticipantBooking(requester, booking.getId());
        if (amendment.getProposedBy().getId().equals(requester.getId())) {
            throw new IllegalStateException("The proposer cannot accept or reject their own amendment - withdraw it instead");
        }
        if (amendment.getStatus() != AmendmentStatus.PENDING) {
            throw new IllegalStateException("Amendment is not pending a response: " + amendmentId);
        }

        amendment.setStatus(accept ? AmendmentStatus.ACCEPTED : AmendmentStatus.REJECTED);
        amendment.setResolvedAt(Instant.now());
        bookingAmendmentRepository.save(amendment);

        if (accept) {
            applyToBooking(booking, amendment);
            bookingRepository.save(booking);
            // Doesn't move Booking#status off BOOKED - recorded anyway since
            // the price/date/details/packages just changed, and that's
            // exactly the kind of event this history exists to capture.
            bookingStatusEventRepository.save(BookingStatusEvent.builder()
                    .booking(booking)
                    .fromStatus(BookingStatus.BOOKED)
                    .toStatus(BookingStatus.BOOKED)
                    .changedBy(requester)
                    .reason("Amendment accepted: " + amendment.getNote())
                    .build());
        }

        notificationService.notify(amendment.getProposedBy(),
                NotificationType.BOOKING_AMENDMENT_RESOLVED,
                accept ? "Booking change accepted" : "Booking change rejected",
                displayName(requester) + (accept ? " accepted" : " rejected") + " your proposed booking change",
                "BOOKING", booking.getId());

        return toResponse(amendment);
    }

    @Transactional
    public BookingAmendmentResponse withdraw(String requesterEmail, Long amendmentId) {
        User requester = requireUser(requesterEmail);
        BookingAmendment amendment = bookingAmendmentRepository.findById(amendmentId)
                .orElseThrow(() -> new IllegalStateException("Amendment not found: " + amendmentId));
        if (!amendment.getProposedBy().getId().equals(requester.getId())) {
            throw new IllegalStateException("Only the proposer can withdraw an amendment");
        }
        if (amendment.getStatus() != AmendmentStatus.PENDING) {
            throw new IllegalStateException("Amendment is not pending, so it can't be withdrawn: " + amendmentId);
        }

        amendment.setStatus(AmendmentStatus.WITHDRAWN);
        amendment.setResolvedAt(Instant.now());
        bookingAmendmentRepository.save(amendment);
        return toResponse(amendment);
    }

    @Transactional(readOnly = true)
    public List<BookingAmendmentResponse> listForBooking(String requesterEmail, Long bookingId) {
        User requester = requireUser(requesterEmail);
        requireParticipantBooking(requester, bookingId);
        return bookingAmendmentRepository.findByBookingIdOrderByCreatedAtDesc(bookingId).stream()
                .map(this::toResponse)
                .toList();
    }

    private void applyToBooking(Booking booking, BookingAmendment amendment) {
        if (amendment.getNewPrice() != null) {
            booking.setPrice(amendment.getNewPrice());
        }
        if (amendment.getNewEventDatetime() != null) {
            booking.setEventDatetime(amendment.getNewEventDatetime());
        }
        if (amendment.getNewAgreementDetails() != null) {
            booking.setAgreementDetails(amendment.getNewAgreementDetails());
        }
        // newPackageIds isn't stored directly on Booking today (packages
        // live on the originating Quotation, if any) - the accepted
        // amendment's package list is still preserved on the amendment
        // record itself as the durable record of what was agreed.
    }

    private Booking requireParticipantBooking(User user, Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalStateException("Booking not found: " + bookingId));
        boolean isParticipant = booking.getVendorUser().getId().equals(user.getId())
                || booking.getPlannerUser().getId().equals(user.getId());
        if (!isParticipant) {
            throw new IllegalStateException("Booking does not belong to the authenticated user");
        }
        return booking;
    }

    private User otherParty(Booking booking, User requester) {
        return booking.getVendorUser().getId().equals(requester.getId())
                ? booking.getPlannerUser()
                : booking.getVendorUser();
    }

    private User requireUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + email));
    }

    private String displayName(User user) {
        return user.getFirstName() != null ? user.getFirstName() : user.getEmail();
    }

    private BookingAmendmentResponse toResponse(BookingAmendment amendment) {
        List<String> packageNames = amendment.getNewPackageIds().isEmpty()
                ? List.of()
                : vendorPackageRepository.findAllById(amendment.getNewPackageIds()).stream()
                        .map(VendorPackage::getName)
                        .toList();
        return BookingAmendmentResponse.builder()
                .id(amendment.getId())
                .bookingId(amendment.getBooking().getId())
                .proposedByUserId(amendment.getProposedBy().getId())
                .proposedByName(displayName(amendment.getProposedBy()))
                .newPrice(amendment.getNewPrice())
                .newEventDatetime(amendment.getNewEventDatetime())
                .newAgreementDetails(amendment.getNewAgreementDetails())
                .newPackageIds(amendment.getNewPackageIds())
                .newPackageNames(packageNames)
                .note(amendment.getNote())
                .status(amendment.getStatus())
                .createdAt(amendment.getCreatedAt())
                .resolvedAt(amendment.getResolvedAt())
                .build();
    }
}
