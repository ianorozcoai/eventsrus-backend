package com.backend.eventsrus.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SuspiciousSignupTest {

    @Test
    void theThreeOddSignupsFromNovember2026AreFlagged() {
        assertThat(SuspiciousSignup.reasons("5551234567", "jamiehansen.00196@gmail.com")).hasSize(2);
        assertThat(SuspiciousSignup.reasons("5551234567", "jillrichards.71303@gmail.com")).hasSize(2);
        assertThat(SuspiciousSignup.reasons("5551234567", "mildredcopeland.08922@gmail.com")).hasSize(2);
    }

    @Test
    void realLookingSignupsAreNotFlagged() {
        assertThat(SuspiciousSignup.reasons("09209099332", "ianorozcoai@gmail.com")).isEmpty();
        assertThat(SuspiciousSignup.reasons("+639175040348", "nathaliesalcedoevents@gmail.com")).isEmpty();
        assertThat(SuspiciousSignup.reasons("09098426887", "simoneseventsinc@gmail.com")).isEmpty();
        assertThat(SuspiciousSignup.reasons("+63 927 886 3270", "rlotasbakeshop@gmail.com")).isEmpty();
    }

    @Test
    void missingValuesAreNotFlaggedAndNeverThrow() {
        assertThat(SuspiciousSignup.reasons(null, null)).isEmpty();
        assertThat(SuspiciousSignup.reasons("", "")).isEmpty();
        assertThat(SuspiciousSignup.reasons(null, "no-at-sign")).isEmpty();
    }

    @Test
    void shortRepeatedAndSequentialNumbersAreFlagged() {
        assertThat(SuspiciousSignup.reasons("12345", "a@b.com")).containsExactly("Phone number is too short");
        assertThat(SuspiciousSignup.reasons("09111111111", "a@b.com")).containsExactly("Phone number repeats the same digit");
        assertThat(SuspiciousSignup.reasons("09123456781", "a@b.com"))
                .containsExactly("Phone number is a simple sequence (like 123456)");
        assertThat(SuspiciousSignup.reasons("09876543219", "a@b.com"))
                .containsExactly("Phone number is a simple sequence (like 123456)");
    }

    @Test
    void anAmericanStyle555NumberIsFlaggedWithOrWithoutCountryCode() {
        assertThat(SuspiciousSignup.reasons("+1 555 019 8374", "a@b.com"))
                .containsExactly("Phone number starts with 555, a number used in examples");
        assertThat(SuspiciousSignup.reasons("5550198374", "a@b.com"))
                .containsExactly("Phone number starts with 555, a number used in examples");
    }

    @Test
    void ordinaryEmailsWithDigitsAreNotFlagged() {
        assertThat(SuspiciousSignup.reasons("09209099332", "john1234@gmail.com")).isEmpty();
        assertThat(SuspiciousSignup.reasons("09209099332", "x.obscura.ph@gmail.com")).isEmpty();
    }
}
