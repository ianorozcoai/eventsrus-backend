package com.backend.eventsrus.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.backend.eventsrus.enums.BookingStatus;
import com.backend.eventsrus.enums.PaymentType;
import com.backend.eventsrus.enums.QuotationStatus;
import com.backend.eventsrus.enums.Role;
import com.backend.eventsrus.exception.QuotationConflictException;
import com.backend.eventsrus.model.Booking;
import com.backend.eventsrus.model.Event;
import com.backend.eventsrus.model.Quotation;
import com.backend.eventsrus.model.QuotationStatusEvent;
import com.backend.eventsrus.model.User;
import com.backend.eventsrus.repository.BookingRepository;
import com.backend.eventsrus.repository.BookingStatusEventRepository;
import com.backend.eventsrus.repository.EventRepository;
import com.backend.eventsrus.repository.QuotationRepository;
import com.backend.eventsrus.repository.QuotationStatusEventRepository;
import com.backend.eventsrus.repository.UserRepository;
import com.backend.eventsrus.repository.VendorPackageRepository;
import com.backend.eventsrus.repository.VendorProfileRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

/**
 * Phase 1 of the quotation/booking lifecycle rework (see project memory
 * quotation-booking-target-state-machine): real negotiation versioning plus
 * the accept/deposit/payment-review/booked pipeline, all living on
 * Quotation until QuotationService#acceptBooking creates the actual
 * Booking row for the first time.
 */
@ExtendWith(MockitoExtension.class)
class QuotationServiceTest {

    @Mock
    private QuotationRepository quotationRepository;
    @Mock
    private EventRepository eventRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private VendorProfileRepository vendorProfileRepository;
    @Mock
    private VendorPackageRepository vendorPackageRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private S3UploadService s3UploadService;
    @Mock
    private QuotationStatusEventRepository quotationStatusEventRepository;
    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private BookingStatusEventRepository bookingStatusEventRepository;
    @Mock
    private VendorPlanService vendorPlanService;

    private QuotationService quotationService;

    private static final User VENDOR = User.builder().id(1L).email("vendor@example.com").role(Role.VENDOR).build();
    private static final User PLANNER = User.builder().id(2L).email("planner@example.com").role(Role.PLANNER).build();

    @BeforeEach
    void setUp() {
        quotationService = new QuotationService(
                quotationRepository, eventRepository, userRepository, vendorProfileRepository,
                vendorPackageRepository, notificationService, s3UploadService, quotationStatusEventRepository,
                bookingRepository, bookingStatusEventRepository, vendorPlanService);
    }

    private Quotation quotationAt(QuotationStatus status) {
        Event event = Event.builder().id(5L).name("Ian's wedding").build();
        return Quotation.builder()
                .id(42L).event(event).vendorUser(VENDOR).plannerUser(PLANNER)
                .status(status).version(1).targetDate(LocalDate.of(2026, 10, 10))
                .build();
    }

    @Nested
    class RespondWithPdf {

        @Test
        void writesTheVendorsMessageAmountAndPdfKeyOntoTheStatusHistoryRow() {
            Quotation quotation = quotationAt(QuotationStatus.REQUEST_FOR_QUOTE);

            when(userRepository.findByEmail("vendor@example.com")).thenReturn(Optional.of(VENDOR));
            when(quotationRepository.findById(42L)).thenReturn(Optional.of(quotation));
            when(s3UploadService.upload(any(), anyString(), any()))
                    .thenReturn(new S3UploadService.UploadResult("quotations/42-abc.pdf", null));

            MockMultipartFile pdf = new MockMultipartFile("pdf", "quote.pdf", "application/pdf", "content".getBytes());
            quotationService.respondWithPdf(
                    "vendor@example.com", 42L, pdf, "Here's a revised quote, let me know!", new BigDecimal("50000"));

            ArgumentCaptor<QuotationStatusEvent> captor = ArgumentCaptor.forClass(QuotationStatusEvent.class);
            verify(quotationStatusEventRepository).save(captor.capture());
            QuotationStatusEvent event1 = captor.getValue();
            assertThat(event1.getToStatus()).isEqualTo(QuotationStatus.QUOTE_SENT);
            assertThat(event1.getReason()).isEqualTo("Here's a revised quote, let me know!");
            assertThat(event1.getPdfKey()).isEqualTo("quotations/42-abc.pdf");
            assertThat(event1.getQuotedAmount()).isEqualByComparingTo("50000");
            assertThat(event1.getVersion()).isEqualTo(2);

            assertThat(quotation.getPdfKey()).isEqualTo("quotations/42-abc.pdf");
            assertThat(quotation.getStatus()).isEqualTo(QuotationStatus.QUOTE_SENT);
            assertThat(quotation.getVersion()).isEqualTo(2);
        }

        @Test
        void aRevisionRequestedQuotationRespondsAsRevisionSent() {
            Quotation quotation = quotationAt(QuotationStatus.REVISION_REQUESTED);
            quotation.setVersion(3);

            when(userRepository.findByEmail("vendor@example.com")).thenReturn(Optional.of(VENDOR));
            when(quotationRepository.findById(42L)).thenReturn(Optional.of(quotation));
            when(s3UploadService.upload(any(), anyString(), any()))
                    .thenReturn(new S3UploadService.UploadResult("quotations/42-v2.pdf", null));

            MockMultipartFile pdf = new MockMultipartFile("pdf", "quote.pdf", "application/pdf", "content".getBytes());
            quotationService.respondWithPdf("vendor@example.com", 42L, pdf, null, new BigDecimal("40000"));

            assertThat(quotation.getStatus()).isEqualTo(QuotationStatus.REVISION_SENT);
            assertThat(quotation.getVersion()).isEqualTo(4);
        }

        @Test
        void toleratesNoMessageAtAll() {
            Quotation quotation = quotationAt(QuotationStatus.REQUEST_FOR_QUOTE);

            when(userRepository.findByEmail("vendor@example.com")).thenReturn(Optional.of(VENDOR));
            when(quotationRepository.findById(42L)).thenReturn(Optional.of(quotation));
            when(s3UploadService.upload(any(), anyString(), any()))
                    .thenReturn(new S3UploadService.UploadResult("quotations/42-abc.pdf", null));

            MockMultipartFile pdf = new MockMultipartFile("pdf", "quote.pdf", "application/pdf", "content".getBytes());
            quotationService.respondWithPdf("vendor@example.com", 42L, pdf, null, new BigDecimal("50000"));

            ArgumentCaptor<QuotationStatusEvent> captor = ArgumentCaptor.forClass(QuotationStatusEvent.class);
            verify(quotationStatusEventRepository).save(captor.capture());
            assertThat(captor.getValue().getReason()).isNull();
        }

        @Test
        void rejectsRespondingToAQuotationThatIsNotAwaitingAResponse() {
            Quotation quotation = quotationAt(QuotationStatus.PENDING_DEPOSIT);
            when(userRepository.findByEmail("vendor@example.com")).thenReturn(Optional.of(VENDOR));
            when(quotationRepository.findById(42L)).thenReturn(Optional.of(quotation));

            MockMultipartFile pdf = new MockMultipartFile("pdf", "quote.pdf", "application/pdf", "content".getBytes());
            assertThatThrownBy(() -> quotationService.respondWithPdf(
                    "vendor@example.com", 42L, pdf, null, new BigDecimal("50000")))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    class CreateFromChat {

        @Test
        void createsAQuoteSentQuotationDirectlyWhenNoHistoryExists() {
            Event event = Event.builder().id(5L).name("Ian's wedding").planner(PLANNER).build();
            when(userRepository.findByEmail("vendor@example.com")).thenReturn(Optional.of(VENDOR));
            when(eventRepository.findById(5L)).thenReturn(Optional.of(event));
            when(quotationRepository.existsByEventIdAndVendorUserId(5L, 1L)).thenReturn(false);
            when(quotationRepository.save(any())).thenAnswer(invocation -> {
                Quotation q = invocation.getArgument(0);
                q.setId(99L);
                return q;
            });
            when(s3UploadService.upload(any(), anyString(), any()))
                    .thenReturn(new S3UploadService.UploadResult("quotations/99-abc.pdf", null));

            MockMultipartFile pdf = new MockMultipartFile("pdf", "quote.pdf", "application/pdf", "content".getBytes());
            quotationService.createFromChat(
                    "vendor@example.com", 5L, LocalDate.of(2026, 12, 1), "Here's what we discussed", null, pdf,
                    new BigDecimal("30000"));

            ArgumentCaptor<QuotationStatusEvent> captor = ArgumentCaptor.forClass(QuotationStatusEvent.class);
            verify(quotationStatusEventRepository).save(captor.capture());
            assertThat(captor.getValue().getFromStatus()).isNull();
            assertThat(captor.getValue().getToStatus()).isEqualTo(QuotationStatus.QUOTE_SENT);
            assertThat(captor.getValue().getVersion()).isEqualTo(1);
        }

        @Test
        void refusesWhenAQuotationAlreadyExistsForThisEventAndVendor() {
            when(userRepository.findByEmail("vendor@example.com")).thenReturn(Optional.of(VENDOR));
            when(eventRepository.findById(5L)).thenReturn(Optional.of(Event.builder().id(5L).build()));
            when(quotationRepository.existsByEventIdAndVendorUserId(5L, 1L)).thenReturn(true);

            MockMultipartFile pdf = new MockMultipartFile("pdf", "quote.pdf", "application/pdf", "content".getBytes());
            assertThatThrownBy(() -> quotationService.createFromChat(
                    "vendor@example.com", 5L, null, "hi", null, pdf, new BigDecimal("30000")))
                    .isInstanceOf(QuotationConflictException.class);

            verify(quotationRepository, never()).save(any());
        }
    }

    @Nested
    class AcceptQuote {

        @Test
        void withoutAScreenshotResolvesToPendingDeposit() {
            Quotation quotation = quotationAt(QuotationStatus.QUOTE_SENT);
            when(userRepository.findByEmail("planner@example.com")).thenReturn(Optional.of(PLANNER));
            when(quotationRepository.findById(42L)).thenReturn(Optional.of(quotation));

            quotationService.acceptQuote("planner@example.com", 42L, null);

            assertThat(quotation.getStatus()).isEqualTo(QuotationStatus.PENDING_DEPOSIT);
            assertThat(quotation.getAcceptedAt()).isNotNull();
            // One row for ->QUOTE_ACCEPTED, one for QUOTE_ACCEPTED->PENDING_DEPOSIT.
            verify(quotationStatusEventRepository, org.mockito.Mockito.times(2)).save(any());
        }

        @Test
        void withAScreenshotResolvesDirectlyToPaymentReview() {
            Quotation quotation = quotationAt(QuotationStatus.QUOTE_SENT);
            when(userRepository.findByEmail("planner@example.com")).thenReturn(Optional.of(PLANNER));
            when(quotationRepository.findById(42L)).thenReturn(Optional.of(quotation));
            when(s3UploadService.upload(any(), anyString(), any()))
                    .thenReturn(new S3UploadService.UploadResult("quotations/42/payment-screenshot-abc.jpg", null));

            MockMultipartFile screenshot =
                    new MockMultipartFile("screenshot", "proof.jpg", "image/jpeg", "img".getBytes());
            quotationService.acceptQuote("planner@example.com", 42L, screenshot);

            assertThat(quotation.getStatus()).isEqualTo(QuotationStatus.PAYMENT_REVIEW);
            assertThat(quotation.getPaymentScreenshotKey()).isEqualTo("quotations/42/payment-screenshot-abc.jpg");
        }

        @Test
        void rejectsAcceptingAQuotationThatWasNeverSent() {
            Quotation quotation = quotationAt(QuotationStatus.REQUEST_FOR_QUOTE);
            when(userRepository.findByEmail("planner@example.com")).thenReturn(Optional.of(PLANNER));
            when(quotationRepository.findById(42L)).thenReturn(Optional.of(quotation));

            assertThatThrownBy(() -> quotationService.acceptQuote("planner@example.com", 42L, null))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    class SubmitPaymentScreenshot {

        @Test
        void movesFromPendingDepositToPaymentReview() {
            Quotation quotation = quotationAt(QuotationStatus.PENDING_DEPOSIT);
            when(userRepository.findByEmail("planner@example.com")).thenReturn(Optional.of(PLANNER));
            when(quotationRepository.findById(42L)).thenReturn(Optional.of(quotation));
            when(s3UploadService.upload(any(), anyString(), any()))
                    .thenReturn(new S3UploadService.UploadResult("quotations/42/payment-screenshot-xyz.jpg", null));

            MockMultipartFile screenshot =
                    new MockMultipartFile("screenshot", "proof.jpg", "image/jpeg", "img".getBytes());
            quotationService.submitPaymentScreenshot("planner@example.com", 42L, screenshot);

            assertThat(quotation.getStatus()).isEqualTo(QuotationStatus.PAYMENT_REVIEW);
        }

        @Test
        void resubmittingAfterARejectionClearsTheOldReason() {
            Quotation quotation = quotationAt(QuotationStatus.PAYMENT_REJECTED);
            quotation.setPaymentRejectionReason("Screenshot was blurry");
            when(userRepository.findByEmail("planner@example.com")).thenReturn(Optional.of(PLANNER));
            when(quotationRepository.findById(42L)).thenReturn(Optional.of(quotation));
            when(s3UploadService.upload(any(), anyString(), any()))
                    .thenReturn(new S3UploadService.UploadResult("quotations/42/payment-screenshot-2.jpg", null));

            MockMultipartFile screenshot =
                    new MockMultipartFile("screenshot", "proof.jpg", "image/jpeg", "img".getBytes());
            quotationService.submitPaymentScreenshot("planner@example.com", 42L, screenshot);

            assertThat(quotation.getStatus()).isEqualTo(QuotationStatus.PAYMENT_REVIEW);
            assertThat(quotation.getPaymentRejectionReason()).isNull();
        }
    }

    @Nested
    class AcceptBooking {

        @Test
        void createsABookedBookingWithTheQuotedAmount() {
            Quotation quotation = quotationAt(QuotationStatus.PAYMENT_REVIEW);
            quotation.setQuotedAmount(new BigDecimal("45000"));
            when(userRepository.findByEmail("vendor@example.com")).thenReturn(Optional.of(VENDOR));
            when(quotationRepository.findById(42L)).thenReturn(Optional.of(quotation));
            when(bookingRepository.existsByQuotationId(42L)).thenReturn(false);
            when(s3UploadService.upload(any(), anyString(), any()))
                    .thenReturn(new S3UploadService.UploadResult("quotations/42/invoice.pdf", null));
            when(bookingRepository.save(any())).thenAnswer(invocation -> {
                Booking b = invocation.getArgument(0);
                b.setId(7L);
                return b;
            });

            MockMultipartFile invoice =
                    new MockMultipartFile("invoice", "invoice.pdf", "application/pdf", "inv".getBytes());
            quotationService.acceptBooking(
                    "vendor@example.com", 42L, "Looking forward to it!", PaymentType.FULL, invoice);

            ArgumentCaptor<Booking> captor = ArgumentCaptor.forClass(Booking.class);
            verify(bookingRepository).save(captor.capture());
            Booking booking = captor.getValue();
            assertThat(booking.getStatus()).isEqualTo(BookingStatus.BOOKED);
            assertThat(booking.getPrice()).isEqualByComparingTo("45000");
            assertThat(booking.getPaymentType()).isEqualTo(PaymentType.FULL);
            assertThat(booking.getQuotation()).isEqualTo(quotation);
            assertThat(quotation.getStatus()).isEqualTo(QuotationStatus.BOOKED);
        }

        @Test
        void requiresAnInvoice() {
            Quotation quotation = quotationAt(QuotationStatus.PAYMENT_REVIEW);
            assertThatThrownBy(() -> quotationService.acceptBooking(
                    "vendor@example.com", 42L, null, PaymentType.PARTIAL, null))
                    .isInstanceOf(com.backend.eventsrus.exception.InvalidFileTypeException.class);
            verify(bookingRepository, never()).save(any());
        }
    }
}
