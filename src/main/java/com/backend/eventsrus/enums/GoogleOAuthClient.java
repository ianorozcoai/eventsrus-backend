package com.backend.eventsrus.enums;

/**
 * Which Google OAuth client issued a stored Google Calendar refresh token -
 * minting a fresh access token from it must use the matching client id/
 * secret (see GoogleCalendarClient#mintAccessToken), or Google rejects the
 * refresh.
 */
public enum GoogleOAuthClient {
    /** eventsrus-web's "Web application" type client (GoogleCalendarOAuthController) - https-only redirect, tied to a browser session. */
    WEB,
    /** eventsrus-ui's "Desktop app" type client (GoogleCalendarService#exchangeAuthorizationCode) - supports the native app's custom-scheme redirect with no Console-side registration. */
    MOBILE_DESKTOP
}
