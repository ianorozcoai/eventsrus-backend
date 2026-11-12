package com.backend.eventsrus.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Plain-words reasons an admin should take a second look at a sign-up, based
 * only on what the person typed (phone number) and their email address. It
 * never blocks anyone - the admin screens just show a "Check this account" tag
 * with these reasons, and the admin decides. A real person can be flagged by
 * mistake (a lucky number, an email with digits), which is why it is a tag
 * and not a rule.
 */
public final class SuspiciousSignup {

    // jamie.hansen.00196@gmail.com style: letters, a dot, then 4-6 digits.
    private static final Pattern NAME_DOT_DIGITS = Pattern.compile("^[a-z]+\\.\\d{4,6}$");

    private SuspiciousSignup() {
    }

    public static List<String> reasons(String phone, String email) {
        List<String> reasons = new ArrayList<>();
        String phoneReason = phoneReason(phone);
        if (phoneReason != null) {
            reasons.add(phoneReason);
        }
        if (looksMachineMade(email)) {
            reasons.add("Email looks machine-made (a name, a dot, then random digits)");
        }
        return reasons;
    }

    private static String phoneReason(String phone) {
        if (phone == null) {
            return null;
        }
        String digits = phone.replaceAll("\\D", "");
        if (digits.isEmpty()) {
            return null;
        }
        if (digits.length() < 7) {
            return "Phone number is too short";
        }
        if (hasRun(digits, 6, 0)) {
            return "Phone number repeats the same digit";
        }
        if (hasRun(digits, 6, 1) || hasRun(digits, 6, -1)) {
            return "Phone number is a simple sequence (like 123456)";
        }
        // 555 numbers are the fictional numbers used in examples and tests.
        String national = digits.length() == 11 && digits.startsWith("1") ? digits.substring(1) : digits;
        if (national.startsWith("555")) {
            return "Phone number starts with 555, a number used in examples";
        }
        return null;
    }

    /** True if digits contains {@code length} digits in a row that each step by {@code step} (0 = same digit). */
    private static boolean hasRun(String digits, int length, int step) {
        int run = 1;
        for (int i = 1; i < digits.length(); i++) {
            int diff = digits.charAt(i) - digits.charAt(i - 1);
            if (diff == step) {
                run++;
                if (run >= length) {
                    return true;
                }
            } else {
                run = 1;
            }
        }
        return false;
    }

    private static boolean looksMachineMade(String email) {
        if (email == null) {
            return false;
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return false;
        }
        String local = email.substring(0, at).toLowerCase(Locale.ROOT);
        return NAME_DOT_DIGITS.matcher(local).matches();
    }
}
