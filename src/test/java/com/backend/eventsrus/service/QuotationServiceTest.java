package com.backend.eventsrus.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.backend.eventsrus.enums.QuotationStatus;
import com.backend.eventsrus.enums.Role;
import com.backend.eventsrus.model.Event;
import com.backend.eventsrus.model.Quotation;
import com.backend.eventsrus.model.QuotationStatusEvent;
import com.backend.eventsrus.model.User;
import com.backend.eventsrus.repository.BookingRepository;
import com.backend.eventsrus.repository.EventRepository;
import com.backend.eventsrus.repository.QuotationRepository;
import com.backend.eventsrus.repository.QuotationStatusEventRepository;
import com.backend.eventsrus.repository.UserRepository;
import com.backend.eventsrus.repository.VendorPackageRepository;
import com.backend.eventsrus.repository.VendorProfileRepository;
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
 * A version-history gap: overwriting Quotation#pdfKey on every response
 * meant an earlier PDF (and whatever the vendor said about it) was
 * unrecoverable the moment a revision was requested and re-responded to.
 * respondWithPdf now also writes the vendor's message and the exact PDF
 * key onto this transition's own audit row (quotation_status_history),
 * so past versions stay reachable via #history even after being
 * superseded on the quotation itself. See QuotationStatusEvent#pdfKey.
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
    private VendorPlanService vendorPlanService;

    private QuotationService quotationService;

    @BeforeEach
    void setUp() {
        quotationService = new QuotationService(
                quotationRepository, eventRepository, userRepository, vendorProfileRepository,
                vendorPackageRepository, notificationService, s3UploadService, quotationStatusEventRepository,
                bookingRepository, vendorPlanService);
    }

    @Nested
    class RespondWithPdf {

        @Test
        void writesTheVendorsMessageAndPdfKeyOntoTheStatusHistoryRow() {
            User vendor = User.builder().id(1L).email("vendor@example.com").role(Role.VENDOR).build();
            User planner = User.builder().id(2L).email("planner@example.com").role(Role.PLANNER).build();
            Event event = Event.builder().id(5L).name("Ian's wedding").build();
            Quotation quotation = Quotation.builder()
                    .id(42L).event(event).vendorUser(vendor).plannerUser(planner)
                    .status(QuotationStatus.REQUESTED).build();

            when(userRepository.findByEmail("vendor@example.com")).thenReturn(Optional.of(vendor));
            when(quotationRepository.findById(42L)).thenReturn(Optional.of(quotation));
            when(s3UploadService.upload(any(), anyString(), any()))
                    .thenReturn(new S3UploadService.UploadResult("quotations/42-abc.pdf", null));

            MockMultipartFile pdf = new MockMultipartFile("pdf", "quote.pdf", "application/pdf", "content".getBytes());
            quotationService.respondWithPdf("vendor@example.com", 42L, pdf, "Here's a revised quote, let me know!");

            ArgumentCaptor<QuotationStatusEvent> captor = ArgumentCaptor.forClass(QuotationStatusEvent.class);
            verify(quotationStatusEventRepository).save(captor.capture());
            QuotationStatusEvent event1 = captor.getValue();
            assertThat(event1.getToStatus()).isEqualTo(QuotationStatus.RESPONDED);
            assertThat(event1.getReason()).isEqualTo("Here's a revised quote, let me know!");
            assertThat(event1.getPdfKey()).isEqualTo("quotations/42-abc.pdf");

            assertThat(quotation.getPdfKey()).isEqualTo("quotations/42-abc.pdf");
        }

        @Test
        void toleratesNoMessageAtAll() {
            User vendor = User.builder().id(1L).email("vendor@example.com").role(Role.VENDOR).build();
            User planner = User.builder().id(2L).email("planner@example.com").role(Role.PLANNER).build();
            Event event = Event.builder().id(5L).name("Ian's wedding").build();
            Quotation quotation = Quotation.builder()
                    .id(42L).event(event).vendorUser(vendor).plannerUser(planner)
                    .status(QuotationStatus.REQUESTED).build();

            when(userRepository.findByEmail("vendor@example.com")).thenReturn(Optional.of(vendor));
            when(quotationRepository.findById(42L)).thenReturn(Optional.of(quotation));
            when(s3UploadService.upload(any(), anyString(), any()))
                    .thenReturn(new S3UploadService.UploadResult("quotations/42-abc.pdf", null));

            MockMultipartFile pdf = new MockMultipartFile("pdf", "quote.pdf", "application/pdf", "content".getBytes());
            quotationService.respondWithPdf("vendor@example.com", 42L, pdf, null);

            ArgumentCaptor<QuotationStatusEvent> captor = ArgumentCaptor.forClass(QuotationStatusEvent.class);
            verify(quotationStatusEventRepository).save(captor.capture());
            assertThat(captor.getValue().getReason()).isNull();
        }
    }
}
