package com.backend.eventsrus.util;

import java.util.Locale;

/**
 * Works out which kind of client an account first signed up from. The web app
 * and the mobile app send an X-Client-Platform header (web, android, ios);
 * older mobile builds send none, but Flutter's HTTP client identifies itself as
 * "Dart/..." so that still reads as MOBILE. Anything else is UNKNOWN.
 */
public final class SignupSource {

    public static final String WEB = "WEB";
    public static final String ANDROID = "ANDROID";
    public static final String IOS = "IOS";
    public static final String MOBILE = "MOBILE";
    public static final String UNKNOWN = "UNKNOWN";

    private SignupSource() {
    }

    public static String resolve(String platformHeader, String userAgent) {
        if (platformHeader != null) {
            switch (platformHeader.trim().toLowerCase(Locale.ROOT)) {
                case "web":
                    return WEB;
                case "android":
                    return ANDROID;
                case "ios":
                    return IOS;
                default:
                    break;
            }
        }
        if (userAgent != null && userAgent.startsWith("Dart/")) {
            return MOBILE;
        }
        return UNKNOWN;
    }

    /** Plain-words label for emails and admin screens. */
    public static String label(String source) {
        if (source == null) {
            return "Unknown (before tracking)";
        }
        switch (source) {
            case WEB:
                return "Website";
            case ANDROID:
                return "Android app";
            case IOS:
                return "iPhone app";
            case MOBILE:
                return "Mobile app";
            default:
                return "Unknown";
        }
    }
}
