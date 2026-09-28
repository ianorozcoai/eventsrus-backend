package com.backend.eventsrus.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.backend.eventsrus.enums.BookingStatus;
import com.backend.eventsrus.enums.HistoryEntryType;
import com.backend.eventsrus.enums.NotificationType;
import com.backend.eventsrus.enums.PaymentType;
import com.backend.eventsrus.enums.QuotationAttachmentFileType;
import com.backend.eventsrus.enums.QuotationStatus;
import com.backend.eventsrus.enums.Role;
import com.backend.eventsrus.exception.InvalidFileTypeException;
import com.backend.eventsrus.exception.QuotationConflictException;
import com.backend.eventsrus.exception.TooManyAttachmentsException;
import com.backend.eventsrus.model.Booking;
import com.backend.eventsrus.model.Event;
import com.backend.eventsrus.model.Quotation;
import com.backend.eventsrus.model.QuotationAttachment;
import com.backend.eventsrus.model.QuotationStatusEvent;
import com.backend.eventsrus.model.User;
import com.backend.eventsrus.repository.BookingRepository;
import com.backend.eventsrus.repository.BookingStatusEventRepository;
import com.backend.eventsrus.repository.EventRepository;
import com.backend.eventsrus.repository.QuotationImageRepository;
import com.backend.eventsrus.repository.QuotationRepository;
import com.backend.eventsrus.repository.QuotationStatusEventRepository;
import com.backend.eventsrus.repository.UserRepository;
import com.backend.eventsrus.repository.VendorPackageRepository;
import com.backend.eventsrus.repository.VendorProfileRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

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
    @Mock
    private QuotationImageRepository quotationImageRepository;
    @Mock
    private com.backend.eventsrus.repository.QuotationAttachmentRepository quotationAttachmentRepository;
    @Mock
    private SystemSettingService systemSettingService;

    private QuotationService quotationService;

    private static final User VENDOR = User.builder().id(1L).email("vendor@example.com").role(Role.VENDOR).build();
    private static final User PLANNER = User.builder().id(2L).email("planner@example.com").role(Role.PLANNER).build();

    @BeforeEach
    void setUp() {
        lenient().when(systemSettingService.getInt(com.backend.eventsrus.enums.SystemSettingKey.QUOTATION_IMAGE_LIMIT))
                .thenReturn(5);
        quotationService = new QuotationService(
                quotationRepository, eventRepository, userRepository, vendorProfileRepository,
                vendorPackageRepository, notificationService, s3UploadService, quotationStatusEventRepository,
                bookingRepository, bookingStatusEventRepository, vendorPlanService, quotationImageRepository,
                quotationAttachmentRepository, systemSettingService);
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
                    "vendor@example.com", 42L, pdf, "Here's a revised quote, let me know!", new BigDecimal("50000"),
                    null);

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
            quotationService.respondWithPdf("vendor@example.com", 42L, pdf, null, new BigDecimal("40000"), null);

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
            quotationService.respondWithPdf("vendor@example.com", 42L, pdf, null, new BigDecimal("50000"), null);

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
                    "vendor@example.com", 42L, pdf, null, new BigDecimal("50000"), null))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        void uploadsSupplementaryImagesAlongsideThePdf() {
            Quotation quotation = quotationAt(QuotationStatus.REQUEST_FOR_QUOTE);
            when(userRepository.findByEmail("vendor@example.com")).thenReturn(Optional.of(VENDOR));
            when(quotationRepository.findById(42L)).thenReturn(Optional.of(quotation));
            when(s3UploadService.upload(any(), anyString(), any()))
                    .thenReturn(new S3UploadService.UploadResult("quotations/42-abc.pdf", null));

            MockMultipartFile pdf = new MockMultipartFile("pdf", "quote.pdf", "application/pdf", "content".getBytes());
            MockMultipartFile image =
                    new MockMultipartFile("images", "sample-work.jpg", "image/jpeg", "content".getBytes());
            quotationService.respondWithPdf(
                    "vendor@example.com", 42L, pdf, null, new BigDecimal("50000"), List.of(image));

            verify(quotationImageRepository).save(any());
        }

        @Test
        void rejectsMoreThanFiveResponseImages() {
            Quotation quotation = quotationAt(QuotationStatus.REQUEST_FOR_QUOTE);
            when(userRepository.findByEmail("vendor@example.com")).thenReturn(Optional.of(VENDOR));
            when(quotationRepository.findById(42L)).thenReturn(Optional.of(quotation));
            when(s3UploadService.upload(any(), anyString(), any()))
                    .thenReturn(new S3UploadService.UploadResult("quotations/42-abc.pdf", null));

            MockMultipartFile pdf = new MockMultipartFile("pdf", "quote.pdf", "application/pdf", "content".getBytes());
            List<MultipartFile> sixImages = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                sixImages.add(new MockMultipartFile("images", "photo" + i + ".jpg", "image/jpeg", "content".getBytes()));
            }

            assertThatThrownBy(() -> quotationService.respondWithPdf(
                    "vendor@example.com", 42L, pdf, null, new BigDecimal("50000"), sixImages))
                    .isInstanceOf(TooManyAttachmentsException.class);
        }

        @Test
        void rejectsAResponseImageThatIsNotPngOrJpeg() {
            Quotation quotation = quotationAt(QuotationStatus.REQUEST_FOR_QUOTE);
            when(userRepository.findByEmail("vendor@example.com")).thenReturn(Optional.of(VENDOR));
            when(quotationRepository.findById(42L)).thenReturn(Optional.of(quotation));
            when(s3UploadService.upload(any(), anyString(), any()))
                    .thenReturn(new S3UploadService.UploadResult("quotations/42-abc.pdf", null));

            MockMultipartFile pdf = new MockMultipartFile("pdf", "quote.pdf", "application/pdf", "content".getBytes());
            MockMultipartFile badImage =
                    new MockMultipartFile("images", "sample.gif", "image/gif", "content".getBytes());

            assertThatThrownBy(() -> quotationService.respondWithPdf(
                    "vendor@example.com", 42L, pdf, null, new BigDecimal("50000"), List.of(badImage)))
                    .isInstanceOf(com.backend.eventsrus.exception.InvalidFileTypeException.class);
        }
    }

    @Nested
    class RequestQuotationWithImages {

        @Test
        void uploadsReferenceImagesAgainstTheNewQuotation() {
            Event event = Event.builder().id(5L).name("Ian's wedding").build();
            when(userRepository.findByEmail("planner@example.com")).thenReturn(Optional.of(PLANNER));
            when(userRepository.findById(1L)).thenReturn(Optional.of(VENDOR));
            when(eventRepository.findById(5L)).thenReturn(Optional.of(event));
            when(quotationRepository.save(any())).thenAnswer(inv -> {
                Quotation q = inv.getArgument(0);
                q.setId(99L);
                return q;
            });
            when(s3UploadService.upload(any(), anyString(), any()))
                    .thenReturn(new S3UploadService.UploadResult("quotations/99-reference.jpg", null));

            MockMultipartFile image =
                    new MockMultipartFile("referenceImages", "cake-idea.jpg", "image/jpeg", "content".getBytes());
            quotationService.requestQuotation(
                    "planner@example.com", 5L, 1L, null, "Something like this cake, but blue", null,
                    List.of(image));

            verify(quotationImageRepository).save(any());
        }

        @Test
        void rejectsMoreThanFiveReferenceImages() {
            Event event = Event.builder().id(5L).name("Ian's wedding").build();
            when(userRepository.findByEmail("planner@example.com")).thenReturn(Optional.of(PLANNER));
            when(userRepository.findById(1L)).thenReturn(Optional.of(VENDOR));
            when(eventRepository.findById(5L)).thenReturn(Optional.of(event));
            when(quotationRepository.save(any())).thenAnswer(inv -> {
                Quotation q = inv.getArgument(0);
                q.setId(99L);
                return q;
            });

            List<MultipartFile> sixImages = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                sixImages.add(
                        new MockMultipartFile("referenceImages", "photo" + i + ".jpg", "image/jpeg", "content".getBytes()));
            }

            assertThatThrownBy(() -> quotationService.requestQuotation(
                    "planner@example.com", 5L, 1L, null, "message", null, sixImages))
                    .isInstanceOf(TooManyAttachmentsException.class);
        }
    }

    @Nested
    class RequestRevision {

        /**
         * Regression test: requestRevision used to increment Quotation#version
         * itself, on top of respondWithPdf's own increment - so a single
         * revision cycle (vendor sends v1 -> planner asks for a revision ->
         * vendor sends v2) burned two version numbers and the vendor's second
         * real file showed up mislabeled v3.
         */
        @Test
        void doesNotIncrementVersionByItself() {
            Quotation quotation = quotationAt(QuotationStatus.QUOTE_SENT);
            quotation.setVersion(1);
            when(userRepository.findByEmail("planner@example.com")).thenReturn(Optional.of(PLANNER));
            when(quotationRepository.findById(42L)).thenReturn(Optional.of(quotation));

            quotationService.requestRevision("planner@example.com", 42L, null, "Please lower the price", null, null);

            assertThat(quotation.getVersion()).isEqualTo(1);
            assertThat(quotation.getStatus()).isEqualTo(QuotationStatus.REVISION_REQUESTED);
        }

        @Test
        void aFullRevisionCycleLandsTheVendorsSecondFileOnVersionTwo() {
            // Vendor's first real file is already sent and sitting at v1
            // (e.g. via createFromChat, which starts a quote at version 1
            // with no separate REQUEST_FOR_QUOTE stage) - the planner then
            // asks for a revision, and the vendor sends a second file. That
            // second file must land on v2, not v3.
            Quotation quotation = quotationAt(QuotationStatus.QUOTE_SENT);
            quotation.setVersion(1);
            when(userRepository.findByEmail("vendor@example.com")).thenReturn(Optional.of(VENDOR));
            when(userRepository.findByEmail("planner@example.com")).thenReturn(Optional.of(PLANNER));
            when(quotationRepository.findById(42L)).thenReturn(Optional.of(quotation));
            when(s3UploadService.upload(any(), anyString(), any()))
                    .thenReturn(new S3UploadService.UploadResult("quotations/42-v2.pdf", null));

            quotationService.requestRevision("planner@example.com", 42L, null, "Please lower the price", null, null);
            assertThat(quotation.getVersion()).isEqualTo(1);

            MockMultipartFile secondPdf = new MockMultipartFile("pdf", "quote.pdf", "application/pdf", "v2".getBytes());
            quotationService.respondWithPdf("vendor@example.com", 42L, secondPdf, null, new BigDecimal("45000"), null);
            assertThat(quotation.getVersion()).isEqualTo(2);
        }

        @Test
        void rejectsRevisingAQuotationThatIsNotSent() {
            Quotation quotation = quotationAt(QuotationStatus.REQUEST_FOR_QUOTE);
            when(userRepository.findByEmail("planner@example.com")).thenReturn(Optional.of(PLANNER));
            when(quotationRepository.findById(42L)).thenReturn(Optional.of(quotation));

            assertThatThrownBy(() -> quotationService.requestRevision(
                    "planner@example.com", 42L, null, "Please lower the price", null, null))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        void uploadsReferenceImagesAlongsideTheRevisionRequest() {
            Quotation quotation = quotationAt(QuotationStatus.QUOTE_SENT);
            when(userRepository.findByEmail("planner@example.com")).thenReturn(Optional.of(PLANNER));
            when(quotationRepository.findById(42L)).thenReturn(Optional.of(quotation));
            when(s3UploadService.upload(any(), anyString(), any()))
                    .thenReturn(new S3UploadService.UploadResult("quotations/42-reference2.jpg", null));

            MockMultipartFile image =
                    new MockMultipartFile("images", "new-idea.jpg", "image/jpeg", "content".getBytes());
            quotationService.requestRevision(
                    "planner@example.com", 42L, null, "Actually, blue instead of red", null, List.of(image));

            verify(quotationImageRepository).save(any());
        }

        // A revision request is a SECOND point (on top of the initial
        // requestQuotation call) where reference images can be added -
        // validateAndUploadImages has to check what's already on file for
        // this side, not just the size of this one call, or 5-then-5 across
        // two separate submissions would sail past the real per-side cap.
        @Test
        void rejectsRevisionImagesThatWouldPushThePerSideTotalPastFive() {
            Quotation quotation = quotationAt(QuotationStatus.QUOTE_SENT);
            when(userRepository.findByEmail("planner@example.com")).thenReturn(Optional.of(PLANNER));
            when(quotationRepository.findById(42L)).thenReturn(Optional.of(quotation));
            when(quotationImageRepository.countByQuotationIdAndSource(42L, com.backend.eventsrus.enums.QuotationImageSource.REQUEST))
                    .thenReturn(3L);

            List<MultipartFile> threeMoreImages = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                threeMoreImages.add(
                        new MockMultipartFile("images", "photo" + i + ".jpg", "image/jpeg", "content".getBytes()));
            }

            assertThatThrownBy(() -> quotationService.requestRevision(
                    "planner@example.com", 42L, null, "message", null, threeMoreImages))
                    .isInstanceOf(TooManyAttachmentsException.class);
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
                    new BigDecimal("30000"), null);

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
                    "vendor@example.com", 5L, null, "hi", null, pdf, new BigDecimal("30000"), null))
                    .isInstanceOf(QuotationConflictException.class);

            verify(quotationRepository, never()).save(any());
        }
    }

    @Nested
    class AcceptQuote {

        private QuotationStatusEvent sentOfferEvent(QuotationStatus toStatus) {
            return QuotationStatusEvent.builder()
                    .toStatus(toStatus).version(1).quotedAmount(new BigDecimal("50000"))
                    .targetDate(LocalDate.of(2026, 10, 10)).pdfKey("quotations/42-abc.pdf")
                    .packageIds(new ArrayList<>())
                    .build();
        }

        @Test
        void withoutAScreenshotResolvesToPendingDeposit() {
            Quotation quotation = quotationAt(QuotationStatus.QUOTE_SENT);
            when(userRepository.findByEmail("planner@example.com")).thenReturn(Optional.of(PLANNER));
            when(quotationRepository.findById(42L)).thenReturn(Optional.of(quotation));
            when(quotationStatusEventRepository.findFirstByQuotationIdAndVersionAndToStatusIn(
                    eq(42L), eq(1), any()))
                    .thenReturn(Optional.of(sentOfferEvent(QuotationStatus.QUOTE_SENT)));

            quotationService.acceptQuote("planner@example.com", 42L, null, null, null);

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
            when(quotationStatusEventRepository.findFirstByQuotationIdAndVersionAndToStatusIn(
                    eq(42L), eq(1), any()))
                    .thenReturn(Optional.of(sentOfferEvent(QuotationStatus.QUOTE_SENT)));
            when(s3UploadService.upload(any(), anyString(), any()))
                    .thenReturn(new S3UploadService.UploadResult("quotations/42/payment-screenshot-abc.jpg", null));

            MockMultipartFile screenshot =
                    new MockMultipartFile("screenshot", "proof.jpg", "image/jpeg", "img".getBytes());
            quotationService.acceptQuote("planner@example.com", 42L, null, null, screenshot);

            assertThat(quotation.getStatus()).isEqualTo(QuotationStatus.PAYMENT_REVIEW);
            assertThat(quotation.getPaymentScreenshotKey()).isEqualTo("quotations/42/payment-screenshot-abc.jpg");
        }

        @Test
        void acceptingAnEarlierVersionLocksInThatVersionsTerms() {
            Quotation quotation = quotationAt(QuotationStatus.REVISION_SENT);
            quotation.setVersion(3);
            quotation.setQuotedAmount(new BigDecimal("95000"));
            when(userRepository.findByEmail("planner@example.com")).thenReturn(Optional.of(PLANNER));
            when(quotationRepository.findById(42L)).thenReturn(Optional.of(quotation));
            QuotationStatusEvent earlierOffer = sentOfferEvent(QuotationStatus.QUOTE_SENT);
            earlierOffer.setQuotedAmount(new BigDecimal("75000"));
            earlierOffer.setPdfKey("quotations/42-v1.pdf");
            when(quotationStatusEventRepository.findFirstByQuotationIdAndVersionAndToStatusIn(
                    eq(42L), eq(1), any()))
                    .thenReturn(Optional.of(earlierOffer));

            quotationService.acceptQuote("planner@example.com", 42L, 1, "I'd like this earlier offer instead", null);

            assertThat(quotation.getQuotedAmount()).isEqualByComparingTo("75000");
            assertThat(quotation.getPdfKey()).isEqualTo("quotations/42-v1.pdf");
        }

        @Test
        void rejectsAcceptingAQuotationThatWasNeverSent() {
            Quotation quotation = quotationAt(QuotationStatus.REQUEST_FOR_QUOTE);
            when(userRepository.findByEmail("planner@example.com")).thenReturn(Optional.of(PLANNER));
            when(quotationRepository.findById(42L)).thenReturn(Optional.of(quotation));

            assertThatThrownBy(() -> quotationService.acceptQuote("planner@example.com", 42L, null, null, null))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        void rejectsAcceptingAVersionThatWasNeverActuallySent() {
            Quotation quotation = quotationAt(QuotationStatus.QUOTE_SENT);
            when(userRepository.findByEmail("planner@example.com")).thenReturn(Optional.of(PLANNER));
            when(quotationRepository.findById(42L)).thenReturn(Optional.of(quotation));
            when(quotationStatusEventRepository.findFirstByQuotationIdAndVersionAndToStatusIn(
                    eq(42L), eq(99), any()))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> quotationService.acceptQuote("planner@example.com", 42L, 99, null, null))
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

    @Nested
    class AddAttachment {

        @Test
        void uploadsAnImageAttachmentFromThePlannerAndNotifiesTheVendor() {
            Quotation quotation = quotationAt(QuotationStatus.QUOTE_SENT);
            when(userRepository.findByEmail("planner@example.com")).thenReturn(Optional.of(PLANNER));
            when(quotationRepository.findById(42L)).thenReturn(Optional.of(quotation));
            when(s3UploadService.upload(any(), anyString(), any()))
                    .thenReturn(new S3UploadService.UploadResult("quotations/42/attachments/idea.jpg", null));

            MockMultipartFile image = new MockMultipartFile("file", "idea.jpg", "image/jpeg", "content".getBytes());
            quotationService.addAttachment("planner@example.com", 42L, image, "Something like this");

            ArgumentCaptor<QuotationAttachment> captor = ArgumentCaptor.forClass(QuotationAttachment.class);
            verify(quotationAttachmentRepository).save(captor.capture());
            QuotationAttachment saved = captor.getValue();
            assertThat(saved.getFileType()).isEqualTo(QuotationAttachmentFileType.IMAGE);
            assertThat(saved.getMessage()).isEqualTo("Something like this");
            assertThat(saved.getUploadedBy()).isEqualTo(PLANNER);
            verify(notificationService).notify(eq(VENDOR), eq(NotificationType.QUOTATION_ATTACHMENT), anyString(), anyString(), anyString(), eq(42L));
            // Sending an attachment must never touch the negotiation state.
            assertThat(quotation.getStatus()).isEqualTo(QuotationStatus.QUOTE_SENT);
        }

        @Test
        void uploadsAPdfAttachmentFromTheVendorAndNotifiesThePlanner() {
            Quotation quotation = quotationAt(QuotationStatus.BOOKED);
            when(userRepository.findByEmail("vendor@example.com")).thenReturn(Optional.of(VENDOR));
            when(quotationRepository.findById(42L)).thenReturn(Optional.of(quotation));
            when(s3UploadService.upload(any(), anyString(), any()))
                    .thenReturn(new S3UploadService.UploadResult("quotations/42/attachments/invoice-copy.pdf", null));

            MockMultipartFile pdf =
                    new MockMultipartFile("file", "invoice-copy.pdf", "application/pdf", "content".getBytes());
            quotationService.addAttachment("vendor@example.com", 42L, pdf, null);

            ArgumentCaptor<QuotationAttachment> captor = ArgumentCaptor.forClass(QuotationAttachment.class);
            verify(quotationAttachmentRepository).save(captor.capture());
            assertThat(captor.getValue().getFileType()).isEqualTo(QuotationAttachmentFileType.PDF);
            verify(notificationService).notify(eq(PLANNER), eq(NotificationType.QUOTATION_ATTACHMENT), anyString(), anyString(), anyString(), eq(42L));
        }

        @Test
        void rejectsANonParticipant() {
            Quotation quotation = quotationAt(QuotationStatus.QUOTE_SENT);
            User stranger = User.builder().id(99L).email("stranger@example.com").role(Role.PLANNER).build();
            when(userRepository.findByEmail("stranger@example.com")).thenReturn(Optional.of(stranger));
            when(quotationRepository.findById(42L)).thenReturn(Optional.of(quotation));

            MockMultipartFile image = new MockMultipartFile("file", "idea.jpg", "image/jpeg", "content".getBytes());
            assertThatThrownBy(() -> quotationService.addAttachment("stranger@example.com", 42L, image, null))
                    .isInstanceOf(IllegalStateException.class);
            verify(quotationAttachmentRepository, never()).save(any());
        }

        @Test
        void rejectsAnUnsupportedFileType() {
            Quotation quotation = quotationAt(QuotationStatus.QUOTE_SENT);
            when(userRepository.findByEmail("planner@example.com")).thenReturn(Optional.of(PLANNER));
            when(quotationRepository.findById(42L)).thenReturn(Optional.of(quotation));

            MockMultipartFile gif = new MockMultipartFile("file", "idea.gif", "image/gif", "content".getBytes());
            assertThatThrownBy(() -> quotationService.addAttachment("planner@example.com", 42L, gif, null))
                    .isInstanceOf(InvalidFileTypeException.class);
            verify(quotationAttachmentRepository, never()).save(any());
        }
    }

    @Nested
    class History {

        @Test
        void mergesStatusEventsAndAttachmentsSortedByCreatedAt() {
            Quotation quotation = quotationAt(QuotationStatus.QUOTE_SENT);
            when(userRepository.findByEmail("planner@example.com")).thenReturn(Optional.of(PLANNER));
            when(quotationRepository.findById(42L)).thenReturn(Optional.of(quotation));

            QuotationStatusEvent statusEvent = QuotationStatusEvent.builder()
                    .id(1L).quotation(quotation).toStatus(QuotationStatus.REQUEST_FOR_QUOTE).changedBy(PLANNER)
                    .version(1).packageIds(new ArrayList<>()).createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                    .build();
            QuotationAttachment attachment = QuotationAttachment.builder()
                    .id(2L).quotation(quotation).uploadedBy(VENDOR).fileKey("quotations/42/attachments/photo.jpg")
                    .fileType(QuotationAttachmentFileType.IMAGE).createdAt(Instant.parse("2026-01-02T00:00:00Z"))
                    .build();
            when(quotationStatusEventRepository.findByQuotationIdOrderByCreatedAtAsc(42L)).thenReturn(List.of(statusEvent));
            when(quotationAttachmentRepository.findByQuotationIdOrderByCreatedAtAsc(42L)).thenReturn(List.of(attachment));

            List<com.backend.eventsrus.dto.QuotationStatusEventResponse> result =
                    quotationService.history("planner@example.com", 42L);

            assertThat(result).hasSize(2);
            assertThat(result.get(0).getEntryType()).isEqualTo(HistoryEntryType.STATUS_CHANGE);
            assertThat(result.get(1).getEntryType()).isEqualTo(HistoryEntryType.ATTACHMENT);
            assertThat(result.get(1).getToStatus()).isNull();
            assertThat(result.get(1).getAttachmentFileType()).isEqualTo("IMAGE");
        }

        @Test
        void rejectsANonParticipant() {
            Quotation quotation = quotationAt(QuotationStatus.QUOTE_SENT);
            User stranger = User.builder().id(99L).email("stranger@example.com").role(Role.PLANNER).build();
            when(userRepository.findByEmail("stranger@example.com")).thenReturn(Optional.of(stranger));
            when(quotationRepository.findById(42L)).thenReturn(Optional.of(quotation));

            assertThatThrownBy(() -> quotationService.history("stranger@example.com", 42L))
                    .isInstanceOf(IllegalStateException.class);
        }
    }
}
