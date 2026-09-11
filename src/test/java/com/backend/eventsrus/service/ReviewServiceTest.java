package com.backend.eventsrus.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.backend.eventsrus.enums.BookingStatus;
import com.backend.eventsrus.enums.NotificationType;
import com.backend.eventsrus.exception.ReviewNotAllowedException;
import com.backend.eventsrus.model.Booking;
import com.backend.eventsrus.model.Event;
import com.backend.eventsrus.model.User;
import com.backend.eventsrus.model.VendorReview;
import com.backend.eventsrus.repository.BookingRepository;
import com.backend.eventsrus.repository.UserRepository;
import com.backend.eventsrus.repository.VendorReviewRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * The eligibility gate is the whole point of this feature (see ReviewService's
 * class javadoc) - a planner may only review a booking that's BOOKED or
 * APPROVED, whose event was at least 3 days ago, and only once. These tests
 * pin that behavior down so a future refactor can't silently loosen it.
 */
@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock
    private VendorReviewRepository vendorReviewRepository;
    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private NotificationService notificationService;

    private ReviewService reviewService;

    private User planner;
    private User vendor;

    @BeforeEach
    void setUp() {
        reviewService = new ReviewService(vendorReviewRepository, bookingRepository, userRepository, notificationService);

        planner = User.builder().id(8L).email("planner@example.com").firstName("Ana").lastName("Reyes").build();
        vendor = User.builder().id(12L).email("vendor@example.com").build();
    }

    private Booking bookingReviewableNow(BookingStatus status, Instant eventDatetime) {
        Event event = Event.builder().id(5L).name("Ana's Wedding").build();
        return Booking.builder()
                .id(1L)
                .event(event)
                .plannerUser(planner)
                .vendorUser(vendor)
                .status(status)
                .eventDatetime(eventDatetime)
                .build();
    }

    @Nested
    class IsReviewable {

        @Test
        void trueWhenBookedAndEventAtLeastThreeDaysPast() {
            Booking booking = bookingReviewableNow(BookingStatus.BOOKED, Instant.now().minus(3, ChronoUnit.DAYS).minusSeconds(60));
            assertThat(reviewService.isReviewable(booking)).isTrue();
        }

        @Test
        void trueWhenApprovedAndEventExactlyAtTheThreeDayBoundary() {
            // >=, not > - the boundary instant itself counts as reviewable.
            Instant reviewableFrom = Instant.now().minus(3, ChronoUnit.DAYS);
            Booking booking = bookingReviewableNow(BookingStatus.APPROVED, reviewableFrom);
            assertThat(reviewService.isReviewable(booking)).isTrue();
        }

        @Test
        void falseWhenEventLessThanThreeDaysPast() {
            Booking booking = bookingReviewableNow(BookingStatus.BOOKED, Instant.now().minus(1, ChronoUnit.DAYS));
            assertThat(reviewService.isReviewable(booking)).isFalse();
        }

        @Test
        void falseWhenEventIsInTheFuture() {
            Booking booking = bookingReviewableNow(BookingStatus.BOOKED, Instant.now().plus(10, ChronoUnit.DAYS));
            assertThat(reviewService.isReviewable(booking)).isFalse();
        }

        @Test
        void falseWhenEventDatetimeIsNull() {
            Booking booking = bookingReviewableNow(BookingStatus.BOOKED, null);
            assertThat(reviewService.isReviewable(booking)).isFalse();
        }

        @ParameterizedTest
        @EnumSource(value = BookingStatus.class, names = {"BOOKED", "APPROVED"}, mode = EnumSource.Mode.EXCLUDE)
        void falseForEveryNonSuccessfulStatus(BookingStatus status) {
            Booking booking = bookingReviewableNow(status, Instant.now().minus(30, ChronoUnit.DAYS));
            assertThat(reviewService.isReviewable(booking)).isFalse();
        }
    }

    @Nested
    class SubmitReview {

        @Test
        void throwsWhenBookingBelongsToAnotherPlanner() {
            User someoneElse = User.builder().id(99L).email("someone.else@example.com").build();
            Booking booking = bookingReviewableNow(BookingStatus.BOOKED, Instant.now().minus(10, ChronoUnit.DAYS));

            when(userRepository.findByEmail("someone.else@example.com")).thenReturn(Optional.of(someoneElse));
            when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));

            assertThatThrownBy(() -> reviewService.submitReview("someone.else@example.com", 1L, 5, "Great!"))
                    .isInstanceOf(ReviewNotAllowedException.class)
                    .hasMessageContaining("does not belong");
            verify(vendorReviewRepository, never()).save(any());
        }

        @Test
        void throwsWhenBookingNotYetReviewable() {
            Booking booking = bookingReviewableNow(BookingStatus.BOOKED, Instant.now().minus(1, ChronoUnit.DAYS));

            when(userRepository.findByEmail(planner.getEmail())).thenReturn(Optional.of(planner));
            when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));

            assertThatThrownBy(() -> reviewService.submitReview(planner.getEmail(), 1L, 5, "Great!"))
                    .isInstanceOf(ReviewNotAllowedException.class)
                    .hasMessageContaining("can't be reviewed yet");
            verify(vendorReviewRepository, never()).save(any());
        }

        @Test
        void throwsWhenAlreadyReviewed() {
            Booking booking = bookingReviewableNow(BookingStatus.BOOKED, Instant.now().minus(10, ChronoUnit.DAYS));

            when(userRepository.findByEmail(planner.getEmail())).thenReturn(Optional.of(planner));
            when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));
            when(vendorReviewRepository.existsByBookingId(1L)).thenReturn(true);

            assertThatThrownBy(() -> reviewService.submitReview(planner.getEmail(), 1L, 5, "Great!"))
                    .isInstanceOf(ReviewNotAllowedException.class)
                    .hasMessageContaining("already reviewed");
            verify(vendorReviewRepository, never()).save(any());
        }

        @Test
        void savesAndNotifiesTheVendorOnSuccess() {
            Booking booking = bookingReviewableNow(BookingStatus.BOOKED, Instant.now().minus(10, ChronoUnit.DAYS));

            when(userRepository.findByEmail(planner.getEmail())).thenReturn(Optional.of(planner));
            when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));
            when(vendorReviewRepository.existsByBookingId(1L)).thenReturn(false);
            when(vendorReviewRepository.save(any(VendorReview.class))).thenAnswer(invocation -> {
                VendorReview review = invocation.getArgument(0);
                review.setId(42L);
                return review;
            });

            var response = reviewService.submitReview(planner.getEmail(), 1L, 5, "Wonderful vendor!");

            assertThat(response.getId()).isEqualTo(42L);
            assertThat(response.getRating()).isEqualTo(5);
            assertThat(response.getReviewerName()).isEqualTo("Ana Reyes");
            assertThat(response.getEventName()).isEqualTo("Ana's Wedding");

            verify(notificationService, times(1)).notify(
                    org.mockito.ArgumentMatchers.eq(vendor),
                    org.mockito.ArgumentMatchers.eq(NotificationType.NEW_REVIEW),
                    anyString(), anyString(), org.mockito.ArgumentMatchers.eq("REVIEW"),
                    org.mockito.ArgumentMatchers.eq(42L));
        }
    }

    @Nested
    class UpdateReview {

        @Test
        void throwsWhenReviewBelongsToAnotherPlanner() {
            User someoneElse = User.builder().id(99L).email("someone.else@example.com").build();
            Booking booking = bookingReviewableNow(BookingStatus.BOOKED, Instant.now().minus(10, ChronoUnit.DAYS));
            VendorReview review = VendorReview.builder().id(7L).booking(booking).rating(4).comment("ok").build();

            when(userRepository.findByEmail("someone.else@example.com")).thenReturn(Optional.of(someoneElse));
            when(vendorReviewRepository.findById(7L)).thenReturn(Optional.of(review));

            assertThatThrownBy(() -> reviewService.updateReview("someone.else@example.com", 7L, 1, "changed"))
                    .isInstanceOf(ReviewNotAllowedException.class)
                    .hasMessageContaining("does not belong");
            verify(vendorReviewRepository, never()).save(any());
        }

        @Test
        void ownerCanEditRatingAndComment() {
            Booking booking = bookingReviewableNow(BookingStatus.BOOKED, Instant.now().minus(10, ChronoUnit.DAYS));
            VendorReview review = VendorReview.builder().id(7L).booking(booking).rating(3).comment("okay").build();

            when(userRepository.findByEmail(planner.getEmail())).thenReturn(Optional.of(planner));
            when(vendorReviewRepository.findById(7L)).thenReturn(Optional.of(review));
            when(vendorReviewRepository.save(any(VendorReview.class))).thenAnswer(i -> i.getArgument(0));

            var response = reviewService.updateReview(planner.getEmail(), 7L, 5, "actually amazing");

            assertThat(response.getRating()).isEqualTo(5);
            assertThat(response.getComment()).isEqualTo("actually amazing");
        }
    }

    @Nested
    class RatingSummary {

        @Test
        void nullAverageAndZeroCountWhenNoVisibleReviews() {
            when(vendorReviewRepository.findByVendorUserIdAndHiddenFalseOrderByCreatedAtDesc(12L)).thenReturn(List.of());

            var summary = reviewService.ratingSummary(12L);

            assertThat(summary.averageRating()).isNull();
            assertThat(summary.reviewCount()).isZero();
        }

        @Test
        void averagesAndRoundsToOneDecimal() {
            Booking booking = bookingReviewableNow(BookingStatus.BOOKED, Instant.now().minus(10, ChronoUnit.DAYS));
            VendorReview r1 = VendorReview.builder().id(1L).booking(booking).rating(5).comment("a").build();
            VendorReview r2 = VendorReview.builder().id(2L).booking(booking).rating(4).comment("b").build();
            VendorReview r3 = VendorReview.builder().id(3L).booking(booking).rating(4).comment("c").build();
            when(vendorReviewRepository.findByVendorUserIdAndHiddenFalseOrderByCreatedAtDesc(12L))
                    .thenReturn(List.of(r1, r2, r3));

            var summary = reviewService.ratingSummary(12L);

            // (5 + 4 + 4) / 3 = 4.333... -> rounds to 4.3
            assertThat(summary.averageRating()).isEqualTo(4.3);
            assertThat(summary.reviewCount()).isEqualTo(3);
        }
    }
}
