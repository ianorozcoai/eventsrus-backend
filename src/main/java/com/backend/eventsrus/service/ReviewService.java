package com.backend.eventsrus.service;

import com.backend.eventsrus.dto.ReviewResponse;
import com.backend.eventsrus.enums.BookingStatus;
import com.backend.eventsrus.enums.NotificationType;
import com.backend.eventsrus.exception.ReviewNotAllowedException;
import com.backend.eventsrus.model.Booking;
import com.backend.eventsrus.model.User;
import com.backend.eventsrus.model.VendorReview;
import com.backend.eventsrus.repository.BookingRepository;
import com.backend.eventsrus.repository.UserRepository;
import com.backend.eventsrus.repository.VendorReviewRepository;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Planner reviews of completed bookings. A planner may review a booking
 * once it is a successful booking (BOOKED or APPROVED), its event is at
 * least {@link #REVIEW_DELAY_DAYS} days past, and they haven't reviewed it
 * already. An admin can hide an abusive review without deleting it.
 */
@Service
@RequiredArgsConstructor
public class ReviewService {

    private static final ZoneId MANILA = ZoneId.of("Asia/Manila");
    private static final long REVIEW_DELAY_DAYS = 3;
    private static final Set<BookingStatus> SUCCESSFUL = EnumSet.of(BookingStatus.BOOKED, BookingStatus.APPROVED);

    private final VendorReviewRepository vendorReviewRepository;
    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    @Transactional
    public ReviewResponse submitReview(String plannerEmail, Long bookingId, int rating, String comment) {
        User planner = requireUser(plannerEmail);
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalStateException("Booking not found: " + bookingId));

        if (!booking.getPlannerUser().getId().equals(planner.getId())) {
            throw new ReviewNotAllowedException("This booking does not belong to the authenticated planner");
        }
        if (!isReviewable(booking)) {
            throw new ReviewNotAllowedException(
                    "This booking can't be reviewed yet - it must be a confirmed booking whose event was at least "
                            + REVIEW_DELAY_DAYS + " days ago.");
        }
        if (vendorReviewRepository.existsByBookingId(bookingId)) {
            throw new ReviewNotAllowedException("You've already reviewed this booking - edit that review instead.");
        }

        VendorReview review = vendorReviewRepository.save(VendorReview.builder()
                .booking(booking)
                .vendorUserId(booking.getVendorUser().getId())
                .rating(rating)
                .comment(comment)
                .hidden(false)
                .build());

        notificationService.notify(booking.getVendorUser(), NotificationType.NEW_REVIEW,
                "New review",
                displayName(planner) + " left a " + rating + "-star review for "
                        + (booking.getEvent().getName() != null ? booking.getEvent().getName() : "their event"),
                "REVIEW", review.getId());

        return toResponse(review);
    }

    @Transactional
    public ReviewResponse updateReview(String plannerEmail, Long reviewId, int rating, String comment) {
        User planner = requireUser(plannerEmail);
        VendorReview review = vendorReviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalStateException("Review not found: " + reviewId));
        if (!review.getBooking().getPlannerUser().getId().equals(planner.getId())) {
            throw new ReviewNotAllowedException("This review does not belong to the authenticated planner");
        }
        review.setRating(rating);
        review.setComment(comment);
        return toResponse(vendorReviewRepository.save(review));
    }

    @Transactional
    public void setHidden(Long reviewId, boolean hidden) {
        VendorReview review = vendorReviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalStateException("Review not found: " + reviewId));
        review.setHidden(hidden);
        vendorReviewRepository.save(review);
    }

    @Transactional(readOnly = true)
    public List<ReviewResponse> listPublic(Long vendorUserId) {
        return vendorReviewRepository.findByVendorUserIdAndHiddenFalseOrderByCreatedAtDesc(vendorUserId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ReviewResponse> listAll(Long vendorUserId) {
        return vendorReviewRepository.findByVendorUserIdOrderByCreatedAtDesc(vendorUserId).stream()
                .map(this::toResponse)
                .toList();
    }

    /** (averageRating or null, count) over this vendor's visible reviews. */
    @Transactional(readOnly = true)
    public RatingSummary ratingSummary(Long vendorUserId) {
        List<VendorReview> visible = vendorReviewRepository.findByVendorUserIdAndHiddenFalseOrderByCreatedAtDesc(vendorUserId);
        if (visible.isEmpty()) {
            return new RatingSummary(null, 0);
        }
        double avg = visible.stream().mapToInt(VendorReview::getRating).average().orElse(0);
        return new RatingSummary(Math.round(avg * 10.0) / 10.0, visible.size());
    }

    /** Whether this booking is in a state a planner can leave a first review on. */
    public boolean isReviewable(Booking booking) {
        if (!SUCCESSFUL.contains(booking.getStatus()) || booking.getEventDatetime() == null) {
            return false;
        }
        Instant reviewableFrom = booking.getEventDatetime().plus(REVIEW_DELAY_DAYS, ChronoUnit.DAYS);
        return !Instant.now().isBefore(reviewableFrom);
    }

    public Long existingReviewId(Long bookingId) {
        return vendorReviewRepository.findByBookingId(bookingId).map(VendorReview::getId).orElse(null);
    }

    /** The planner's existing review for this booking, or null - used to prefill the edit form. */
    public ReviewResponse existingReview(Long bookingId) {
        return vendorReviewRepository.findByBookingId(bookingId).map(this::toResponse).orElse(null);
    }

    private ReviewResponse toResponse(VendorReview review) {
        Booking booking = review.getBooking();
        return ReviewResponse.builder()
                .id(review.getId())
                .rating(review.getRating())
                .comment(review.getComment())
                .createdAt(review.getCreatedAt())
                .updatedAt(review.getUpdatedAt())
                .hidden(review.isHidden())
                .reviewerName(displayName(booking.getPlannerUser()))
                .eventName(booking.getEvent().getName())
                .eventDate(booking.getEvent().getEventDate())
                .build();
    }

    private User requireUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + email));
    }

    private String displayName(User user) {
        if (user.getFirstName() != null) {
            return user.getLastName() != null ? user.getFirstName() + " " + user.getLastName() : user.getFirstName();
        }
        return "A planner";
    }

    public record RatingSummary(Double averageRating, int reviewCount) {
    }
}
