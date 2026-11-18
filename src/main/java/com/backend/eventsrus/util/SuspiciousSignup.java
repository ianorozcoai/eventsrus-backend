package com.backend.eventsrus.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Plain-words reasons an admin should take a second look at a sign-up, based
 * only on what the person typed (phone number, names) and their email address.
 * It never blocks anyone - the admin screens just show an orange "Might be fake"
 * tag with these reasons, and the admin decides. A real person can be flagged by
 * mistake (a lucky number, an email with digits), which is why it is a tag
 * and not a rule.
 */
public final class SuspiciousSignup {

    // jamie.hansen.00196@gmail.com style: letters, a dot, then 4-6 digits.
    private static final Pattern NAME_DOT_DIGITS = Pattern.compile("^[a-z]+\\.\\d{4,6}$");
    // jamiehansen00196 / jamie_hansen_123456: letters (and . _ -) followed by a long run of 5 or more digits.
    private static final Pattern NAME_LONG_DIGITS = Pattern.compile("^[a-z][a-z._-]*\\d{5,}$");
    // The word "test" on its own (test, Test Catering, test1, tester, testing) - not "Latest" or "Contest".
    private static final Pattern TEST_WORD =
            Pattern.compile("\\b(test|tester|testing)\\d*\\b", Pattern.CASE_INSENSITIVE);

    private SuspiciousSignup() {
    }

    /** @param names any names typed at sign-up (business name, owner name, first/last name); nulls are ignored. */
    public static List<String> reasons(String phone, String email, String... names) {
        List<String> reasons = new ArrayList<>();
        String phoneReason = phoneReason(phone);
        if (phoneReason != null) {
            reasons.add(phoneReason);
        }
        if (looksMachineMade(email)) {
            reasons.add("Email looks machine-made (a name, then random or sequential digits)");
        }
        if (nameHasTestWord(names)) {
            reasons.add("Name contains the word \"test\"");
        }
        return reasons;
    }

    private static boolean nameHasTestWord(String... names) {
        if (names == null) {
            return false;
        }
        for (String name : names) {
            if (name != null && TEST_WORD.matcher(name).find()) {
                return true;
            }
        }
        return false;
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
        if (!looksPhilippine(phone.trim(), digits)) {
            return "Phone number is not a Philippine number";
        }
        return null;
    }

    /**
     * Philippine formats: +63 9xx xxx xxxx, 63 9xx..., 09xx xxx xxxx, 9xx xxx xxxx, or a landline written with the
     * national 0 (02 8xxx xxxx) or +63. Anything else (a +1, +44, +91 ... number, or a 10-digit number that does not
     * start with 9) is flagged.
     */
    private static boolean looksPhilippine(String raw, String digits) {
        if (digits.startsWith("63")) {
            return true;
        }
        if (raw.startsWith("+") || raw.startsWith("00")) {
            return false; // an international prefix other than +63
        }
        if (digits.startsWith("0")) {
            return true; // Philippine national format (mobile 09.. or landline 02../03..)
        }
        return digits.length() == 10 && digits.startsWith("9");
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
        String digitsOnly = local.replaceAll("\\D", "");
        return NAME_DOT_DIGITS.matcher(local).matches()
                || NAME_LONG_DIGITS.matcher(local).matches()
                || hasRun(digitsOnly, 5, 1)
                || hasRun(digitsOnly, 5, -1);
    }
}
