package com.backend.eventsrus.service;

import com.backend.eventsrus.dto.GoogleCalendarStatusResponse;
import com.backend.eventsrus.model.Booking;
import com.backend.eventsrus.model.GoogleCalendarConnection;
import com.backend.eventsrus.model.User;
import com.backend.eventsrus.model.VendorProfile;
import com.backend.eventsrus.repository.BookingRepository;
import com.backend.eventsrus.repository.GoogleCalendarConnectionRepository;
import com.backend.eventsrus.repository.UserRepository;
import com.backend.eventsrus.repository.VendorProfileRepository;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Vendor-opt-in sync of their confirmed EventsRUs bookings onto their own
 * Google Calendar (see the connect/disconnect flow in eventsrus-web's
 * GoogleCalendarOAuthController, which owns the actual OAuth redirect dance
 * and calls saveConnection() here with the resulting refresh token once the
 * vendor grants consent - this service never talks to Google's OAuth
 * consent screen itself, only the token-refresh and Calendar API calls via
 * GoogleCalendarClient).
 *
 * onBookingConfirmed/onBookingCancelled are the two integration points
 * (called from BookingService/QuotationService wherever a Booking actually
 * reaches BOOKED or CANCELLED) - both are deliberately no-throw: a vendor
 * who hasn't connected a calendar is the common case (silent no-op), and a
 * genuine Calendar API failure (revoked access, Google outage) is logged
 * and swallowed rather than surfaced, so a calendar hiccup can never block
 * a real booking confirmation or cancellation.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GoogleCalendarService {

    private final GoogleCalendarConnectionRepository connectionRepository;
    private final VendorProfileRepository vendorProfileRepository;
    private final UserRepository userRepository;
    private final BookingRepository bookingRepository;
    private final GoogleCalendarClient googleCalendarClient;
    private final TokenEncryptionService tokenEncryptionService;

    @Transactional
    public void saveConnection(String vendorEmail, String refreshToken, String googleCalendarId) {
        VendorProfile profile = requireProfile(vendorEmail);
        GoogleCalendarConnection connection = connectionRepository.findByVendorProfileId(profile.getId())
                .orElseGet(() -> GoogleCalendarConnection.builder().vendorProfile(profile).build());
        connection.setRefreshTokenEncrypted(tokenEncryptionService.encrypt(refreshToken));
        connection.setGoogleCalendarId(googleCalendarId);
        connection.setConnectedAt(Instant.now());
        connectionRepository.save(connection);
    }

    @Transactional
    public void disconnect(String vendorEmail) {
        VendorProfile profile = requireProfile(vendorEmail);
        connectionRepository.deleteByVendorProfileId(profile.getId());
    }

    @Transactional(readOnly = true)
    public GoogleCalendarStatusResponse getStatus(String vendorEmail) {
        VendorProfile profile = requireProfile(vendorEmail);
        return connectionRepository.findByVendorProfileId(profile.getId())
                .map(c -> GoogleCalendarStatusResponse.builder().connected(true).connectedAt(c.getConnectedAt()).build())
                .orElseGet(() -> GoogleCalendarStatusResponse.builder().connected(false).build());
    }

    @Transactional
    public void onBookingConfirmed(Booking booking) {
        if (booking.getEventDatetime() == null) {
            return;
        }
        try {
            Optional<GoogleCalendarConnection> connectionOpt =
                    connectionRepository.findByVendorProfileId(vendorProfileIdFor(booking));
            if (connectionOpt.isEmpty()) {
                return;
            }
            GoogleCalendarConnection connection = connectionOpt.get();
            String accessToken = googleCalendarClient.mintAccessToken(
                    tokenEncryptionService.decrypt(connection.getRefreshTokenEncrypted()));
            String eventId = googleCalendarClient.createEvent(
                    accessToken, connection.getGoogleCalendarId(), summaryFor(booking), descriptionFor(booking),
                    booking.getEventDatetime());
            booking.setGoogleCalendarEventId(eventId);
            bookingRepository.save(booking);
        } catch (Exception e) {
            log.warn("Google Calendar sync failed for booking {} (confirmed) - continuing without it", booking.getId(), e);
        }
    }

    @Transactional
    public void onBookingCancelled(Booking booking) {
        if (booking.getGoogleCalendarEventId() == null) {
            return;
        }
        try {
            Optional<GoogleCalendarConnection> connectionOpt =
                    connectionRepository.findByVendorProfileId(vendorProfileIdFor(booking));
            if (connectionOpt.isEmpty()) {
                return;
            }
            GoogleCalendarConnection connection = connectionOpt.get();
            String accessToken = googleCalendarClient.mintAccessToken(
                    tokenEncryptionService.decrypt(connection.getRefreshTokenEncrypted()));
            googleCalendarClient.updateEventSummary(
                    accessToken, connection.getGoogleCalendarId(), booking.getGoogleCalendarEventId(),
                    "CANCELLED: " + summaryFor(booking));
        } catch (Exception e) {
            log.warn("Google Calendar sync failed for booking {} (cancelled) - continuing without it", booking.getId(), e);
        }
    }

    private Long vendorProfileIdFor(Booking booking) {
        return vendorProfileRepository.findByUserId(booking.getVendorUser().getId())
                .map(VendorProfile::getId)
                .orElse(null);
    }

    private String summaryFor(Booking booking) {
        String eventName = booking.getEvent() != null && booking.getEvent().getName() != null
                ? booking.getEvent().getName()
                : "Event";
        return eventName + " - " + displayName(booking.getPlannerUser());
    }

    private String descriptionFor(Booking booking) {
        return "Booked via EventsRUs" + (booking.getPrice() != null ? " - ₱" + booking.getPrice() : "");
    }

    private String displayName(User user) {
        if (user.getFirstName() != null) {
            return user.getLastName() != null ? user.getFirstName() + " " + user.getLastName() : user.getFirstName();
        }
        return user.getEmail();
    }

    private VendorProfile requireProfile(String vendorEmail) {
        User user = userRepository.findByEmail(vendorEmail)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + vendorEmail));
        return vendorProfileRepository.findByUserId(user.getId())
                .orElseThrow(() -> new IllegalStateException("Vendor profile not found for user: " + user.getId()));
    }
}
