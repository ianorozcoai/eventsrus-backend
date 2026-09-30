package com.backend.eventsrus.service;

import com.backend.eventsrus.exception.GoogleCalendarApiException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * Thin wrapper over Google's OAuth token endpoint and the Calendar API's
 * REST surface - same "plain RestClient, no SDK" idiom as
 * PayPalSubscriptionClient, so this file follows the same shape. Every
 * public method here can throw GoogleCalendarApiException; GoogleCalendarService
 * is the only caller and is responsible for catching it so a Calendar hiccup
 * never blocks the real booking action that triggered the sync.
 *
 * Bookings don't carry an explicit end time today (Event/Booking only store
 * a single eventDatetime) - EVENT_DURATION is a placeholder block on the
 * vendor's calendar rather than a real end time; fine for "block this date/time
 * off," not meant to be precise.
 */
@Service
public class GoogleCalendarClient {

    private static final Duration EVENT_DURATION = Duration.ofHours(2);
    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String CALENDAR_API_BASE = "https://www.googleapis.com/calendar/v3";

    private final String clientId;
    private final String clientSecret;
    private final RestClient restClient = RestClient.create();

    public GoogleCalendarClient(
            @Value("${google.calendar.client-id}") String clientId,
            @Value("${google.calendar.client-secret}") String clientSecret) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    /** Mints a fresh access token from a stored refresh token - refresh tokens don't expire on their own, but access tokens are short-lived and must be re-minted for every sync. */
    @SuppressWarnings("unchecked")
    public String mintAccessToken(String refreshToken) {
        try {
            Map<String, Object> response = restClient.post()
                    .uri(TOKEN_URL)
                    .contentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED)
                    .body("client_id=" + clientId
                            + "&client_secret=" + clientSecret
                            + "&refresh_token=" + refreshToken
                            + "&grant_type=refresh_token")
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {
                    });
            if (response == null || response.get("access_token") == null) {
                throw new GoogleCalendarApiException("Empty response refreshing Google access token");
            }
            return (String) response.get("access_token");
        } catch (GoogleCalendarApiException e) {
            throw e;
        } catch (Exception e) {
            throw new GoogleCalendarApiException("Failed to refresh Google Calendar access token", e);
        }
    }

    /** Creates a new event on the vendor's calendar and returns its Google-assigned event id. */
    @SuppressWarnings("unchecked")
    public String createEvent(String accessToken, String calendarId, String summary, String description, Instant start) {
        try {
            Map<String, Object> body = Map.of(
                    "summary", summary,
                    "description", description,
                    "start", Map.of("dateTime", start.toString()),
                    "end", Map.of("dateTime", start.plus(EVENT_DURATION).toString()));

            Map<String, Object> response = restClient.post()
                    .uri(CALENDAR_API_BASE + "/calendars/{calendarId}/events", calendarId)
                    .headers(h -> h.setBearerAuth(accessToken))
                    .body(body)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {
                    });
            if (response == null || response.get("id") == null) {
                throw new GoogleCalendarApiException("Empty response creating Google Calendar event");
            }
            return (String) response.get("id");
        } catch (GoogleCalendarApiException e) {
            throw e;
        } catch (Exception e) {
            throw new GoogleCalendarApiException("Failed to create Google Calendar event", e);
        }
    }

    /**
     * Whether an event created earlier is still live on the calendar - used
     * by GoogleCalendarService's connect/reconnect backfill to decide
     * whether a future booking's already-stored event id still needs
     * recreating. False for a 404 (fully purged) or a soft-deleted event
     * (Google marks an event "cancelled" rather than removing it outright
     * for a while after a vendor deletes it from their calendar UI), both
     * of which mean nothing is actually left on the calendar for it.
     */
    public boolean eventExists(String accessToken, String calendarId, String eventId) {
        try {
            Map<String, Object> response = restClient.get()
                    .uri(CALENDAR_API_BASE + "/calendars/{calendarId}/events/{eventId}", calendarId, eventId)
                    .headers(h -> h.setBearerAuth(accessToken))
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {
                    });
            return response != null && !"cancelled".equals(response.get("status"));
        } catch (HttpClientErrorException.NotFound e) {
            return false;
        } catch (Exception e) {
            throw new GoogleCalendarApiException("Failed to check Google Calendar event", e);
        }
    }

    /** Relabels an existing event (e.g. prefixing the title "CANCELLED: ...") rather than deleting it, so it stays visible on the vendor's calendar - see GoogleCalendarService#onBookingCancelled for why. */
    public void updateEventSummary(String accessToken, String calendarId, String eventId, String newSummary) {
        try {
            restClient.patch()
                    .uri(CALENDAR_API_BASE + "/calendars/{calendarId}/events/{eventId}", calendarId, eventId)
                    .headers(h -> h.setBearerAuth(accessToken))
                    .body(Map.of("summary", newSummary))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            throw new GoogleCalendarApiException("Failed to update Google Calendar event", e);
        }
    }
}
