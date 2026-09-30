package com.backend.eventsrus.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.backend.eventsrus.enums.BookingStatus;
import com.backend.eventsrus.model.Booking;
import com.backend.eventsrus.model.Event;
import com.backend.eventsrus.model.GoogleCalendarConnection;
import com.backend.eventsrus.model.User;
import com.backend.eventsrus.model.VendorProfile;
import com.backend.eventsrus.repository.BookingRepository;
import com.backend.eventsrus.repository.GoogleCalendarConnectionRepository;
import com.backend.eventsrus.repository.UserRepository;
import com.backend.eventsrus.repository.VendorProfileRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GoogleCalendarServiceTest {

    @Mock
    private GoogleCalendarConnectionRepository connectionRepository;
    @Mock
    private VendorProfileRepository vendorProfileRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private GoogleCalendarClient googleCalendarClient;
    @Mock
    private TokenEncryptionService tokenEncryptionService;

    private GoogleCalendarService googleCalendarService;

    private static final User VENDOR_USER = User.builder().id(1L).email("vendor@example.com").build();
    private static final VendorProfile PROFILE = VendorProfile.builder().id(9L).user(VENDOR_USER).build();

    @BeforeEach
    void setUp() {
        googleCalendarService = new GoogleCalendarService(
                connectionRepository, vendorProfileRepository, userRepository, bookingRepository,
                googleCalendarClient, tokenEncryptionService);
    }

    private Booking bookingFor(User vendorUser) {
        Event event = Event.builder().id(5L).name("Ian's Wedding").build();
        User planner = User.builder().id(2L).email("planner@example.com").firstName("Jane").build();
        return Booking.builder().id(42L).event(event).vendorUser(vendorUser).plannerUser(planner)
                .eventDatetime(Instant.parse("2026-12-01T00:00:00Z")).build();
    }

    private Booking pastBookingFor(User vendorUser) {
        Event event = Event.builder().id(6L).name("Already Happened").build();
        User planner = User.builder().id(2L).email("planner@example.com").firstName("Jane").build();
        return Booking.builder().id(43L).event(event).vendorUser(vendorUser).plannerUser(planner)
                .eventDatetime(Instant.parse("2020-01-01T00:00:00Z")).build();
    }

    @Nested
    class SaveConnection {

        @Test
        void backfillsAFutureBookedBookingThatHasNoEventYet() {
            when(vendorProfileRepository.findByUserId(1L)).thenReturn(Optional.of(PROFILE));
            when(userRepository.findByEmail("vendor@example.com")).thenReturn(Optional.of(VENDOR_USER));
            when(connectionRepository.findByVendorProfileId(9L)).thenReturn(Optional.empty());
            when(tokenEncryptionService.encrypt("raw-refresh-token")).thenReturn("enc-token");
            when(tokenEncryptionService.decrypt("enc-token")).thenReturn("raw-refresh-token");
            when(googleCalendarClient.mintAccessToken("raw-refresh-token")).thenReturn("access-token");

            Booking booking = bookingFor(VENDOR_USER);
            when(bookingRepository.findByVendorUserIdAndStatus(1L, BookingStatus.BOOKED)).thenReturn(List.of(booking));
            when(googleCalendarClient.createEvent(eq("access-token"), eq("primary"), anyString(), anyString(), any()))
                    .thenReturn("google-event-id-999");

            googleCalendarService.saveConnection("vendor@example.com", "raw-refresh-token", "primary");

            assertThat(booking.getGoogleCalendarEventId()).isEqualTo("google-event-id-999");
            verify(bookingRepository).save(booking);
        }

        @Test
        void doesNotRecreateAnEventStillLiveOnTheCalendar() {
            when(vendorProfileRepository.findByUserId(1L)).thenReturn(Optional.of(PROFILE));
            when(userRepository.findByEmail("vendor@example.com")).thenReturn(Optional.of(VENDOR_USER));
            when(connectionRepository.findByVendorProfileId(9L)).thenReturn(Optional.empty());
            when(tokenEncryptionService.encrypt("raw-refresh-token")).thenReturn("enc-token");
            when(tokenEncryptionService.decrypt("enc-token")).thenReturn("raw-refresh-token");
            when(googleCalendarClient.mintAccessToken("raw-refresh-token")).thenReturn("access-token");

            Booking booking = bookingFor(VENDOR_USER);
            booking.setGoogleCalendarEventId("google-event-id-already-there");
            when(bookingRepository.findByVendorUserIdAndStatus(1L, BookingStatus.BOOKED)).thenReturn(List.of(booking));
            when(googleCalendarClient.eventExists("access-token", "primary", "google-event-id-already-there"))
                    .thenReturn(true);

            googleCalendarService.saveConnection("vendor@example.com", "raw-refresh-token", "primary");

            verify(googleCalendarClient, never()).createEvent(any(), any(), any(), any(), any());
            verify(bookingRepository, never()).save(any());
        }

        @Test
        void recreatesAnEventTheVendorDeletedDirectlyFromGoogleCalendar() {
            when(vendorProfileRepository.findByUserId(1L)).thenReturn(Optional.of(PROFILE));
            when(userRepository.findByEmail("vendor@example.com")).thenReturn(Optional.of(VENDOR_USER));
            when(connectionRepository.findByVendorProfileId(9L)).thenReturn(Optional.empty());
            when(tokenEncryptionService.encrypt("raw-refresh-token")).thenReturn("enc-token");
            when(tokenEncryptionService.decrypt("enc-token")).thenReturn("raw-refresh-token");
            when(googleCalendarClient.mintAccessToken("raw-refresh-token")).thenReturn("access-token");

            Booking booking = bookingFor(VENDOR_USER);
            booking.setGoogleCalendarEventId("google-event-id-deleted");
            when(bookingRepository.findByVendorUserIdAndStatus(1L, BookingStatus.BOOKED)).thenReturn(List.of(booking));
            when(googleCalendarClient.eventExists("access-token", "primary", "google-event-id-deleted"))
                    .thenReturn(false);
            when(googleCalendarClient.createEvent(eq("access-token"), eq("primary"), anyString(), anyString(), any()))
                    .thenReturn("google-event-id-recreated");

            googleCalendarService.saveConnection("vendor@example.com", "raw-refresh-token", "primary");

            assertThat(booking.getGoogleCalendarEventId()).isEqualTo("google-event-id-recreated");
            verify(bookingRepository).save(booking);
        }

        @Test
        void skipsBookingsWhoseEventAlreadyHappened() {
            when(vendorProfileRepository.findByUserId(1L)).thenReturn(Optional.of(PROFILE));
            when(userRepository.findByEmail("vendor@example.com")).thenReturn(Optional.of(VENDOR_USER));
            when(connectionRepository.findByVendorProfileId(9L)).thenReturn(Optional.empty());
            when(tokenEncryptionService.encrypt("raw-refresh-token")).thenReturn("enc-token");

            when(bookingRepository.findByVendorUserIdAndStatus(1L, BookingStatus.BOOKED))
                    .thenReturn(List.of(pastBookingFor(VENDOR_USER)));

            googleCalendarService.saveConnection("vendor@example.com", "raw-refresh-token", "primary");

            verify(googleCalendarClient, never()).mintAccessToken(any());
            verify(googleCalendarClient, never()).createEvent(any(), any(), any(), any(), any());
        }
    }

    @Nested
    class OnBookingConfirmed {

        @Test
        void doesNothingWhenTheVendorHasNotConnectedACalendar() {
            when(vendorProfileRepository.findByUserId(1L)).thenReturn(Optional.of(PROFILE));
            when(connectionRepository.findByVendorProfileId(9L)).thenReturn(Optional.empty());

            googleCalendarService.onBookingConfirmed(bookingFor(VENDOR_USER));

            verify(googleCalendarClient, never()).createEvent(any(), any(), any(), any(), any());
            verify(bookingRepository, never()).save(any());
        }

        @Test
        void createsAnEventAndStoresItsIdWhenConnected() {
            GoogleCalendarConnection connection = GoogleCalendarConnection.builder()
                    .vendorProfile(PROFILE).refreshTokenEncrypted("enc-token").googleCalendarId("primary").build();
            when(vendorProfileRepository.findByUserId(1L)).thenReturn(Optional.of(PROFILE));
            when(connectionRepository.findByVendorProfileId(9L)).thenReturn(Optional.of(connection));
            when(tokenEncryptionService.decrypt("enc-token")).thenReturn("raw-refresh-token");
            when(googleCalendarClient.mintAccessToken("raw-refresh-token")).thenReturn("access-token");
            when(googleCalendarClient.createEvent(eq("access-token"), eq("primary"), anyString(), anyString(), any()))
                    .thenReturn("google-event-id-123");

            Booking booking = bookingFor(VENDOR_USER);
            googleCalendarService.onBookingConfirmed(booking);

            assertThat(booking.getGoogleCalendarEventId()).isEqualTo("google-event-id-123");
            verify(bookingRepository).save(booking);
        }

        @Test
        void swallowsAClientFailureRatherThanThrowing() {
            GoogleCalendarConnection connection = GoogleCalendarConnection.builder()
                    .vendorProfile(PROFILE).refreshTokenEncrypted("enc-token").googleCalendarId("primary").build();
            when(vendorProfileRepository.findByUserId(1L)).thenReturn(Optional.of(PROFILE));
            when(connectionRepository.findByVendorProfileId(9L)).thenReturn(Optional.of(connection));
            when(tokenEncryptionService.decrypt("enc-token")).thenThrow(new IllegalStateException("boom"));

            googleCalendarService.onBookingConfirmed(bookingFor(VENDOR_USER));

            verify(bookingRepository, never()).save(any());
        }
    }

    @Nested
    class OnBookingCancelled {

        @Test
        void doesNothingWhenNoEventWasEverCreated() {
            Booking booking = bookingFor(VENDOR_USER);

            googleCalendarService.onBookingCancelled(booking);

            verify(vendorProfileRepository, never()).findByUserId(any());
            verify(googleCalendarClient, never()).updateEventSummary(any(), any(), any(), any());
        }

        @Test
        void relabelsTheExistingEventRatherThanDeletingIt() {
            GoogleCalendarConnection connection = GoogleCalendarConnection.builder()
                    .vendorProfile(PROFILE).refreshTokenEncrypted("enc-token").googleCalendarId("primary").build();
            when(vendorProfileRepository.findByUserId(1L)).thenReturn(Optional.of(PROFILE));
            when(connectionRepository.findByVendorProfileId(9L)).thenReturn(Optional.of(connection));
            when(tokenEncryptionService.decrypt("enc-token")).thenReturn("raw-refresh-token");
            when(googleCalendarClient.mintAccessToken("raw-refresh-token")).thenReturn("access-token");

            Booking booking = bookingFor(VENDOR_USER);
            booking.setGoogleCalendarEventId("google-event-id-123");
            googleCalendarService.onBookingCancelled(booking);

            verify(googleCalendarClient).updateEventSummary(
                    eq("access-token"), eq("primary"), eq("google-event-id-123"), anyString());
        }
    }
}
