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
        assertThat(SuspiciousSignup.reasons("09209099332", "adrianalbertsunodan28@gmail.com")).isEmpty();
    }

    @Test
    void aNonPhilippineNumberIsFlagged() {
        assertThat(SuspiciousSignup.reasons("+1 415 867 5309", "a@b.com"))
                .containsExactly("Phone number is not a Philippine number");
        assertThat(SuspiciousSignup.reasons("+44 7700 900461", "a@b.com"))
                .containsExactly("Phone number is not a Philippine number");
        assertThat(SuspiciousSignup.reasons("4158675309", "a@b.com"))
                .containsExactly("Phone number is not a Philippine number");
    }

    @Test
    void philippineMobileAndLandlineFormatsAreNotFlagged() {
        assertThat(SuspiciousSignup.reasons("09209099332", "a@b.com")).isEmpty();
        assertThat(SuspiciousSignup.reasons("+63 920 909 9332", "a@b.com")).isEmpty();
        assertThat(SuspiciousSignup.reasons("639209099332", "a@b.com")).isEmpty();
        assertThat(SuspiciousSignup.reasons("9209099332", "a@b.com")).isEmpty();
        assertThat(SuspiciousSignup.reasons("(02) 8123 4097", "a@b.com")).isEmpty();
    }

    @Test
    void anEmailWithALongNumberOrSequentialDigitsIsFlagged() {
        assertThat(SuspiciousSignup.reasons("09209099332", "jamiehansen00196@gmail.com")).hasSize(1);
        assertThat(SuspiciousSignup.reasons("09209099332", "jamie_hansen_123456@gmail.com")).hasSize(1);
        assertThat(SuspiciousSignup.reasons("09209099332", "user54321@gmail.com")).hasSize(1);
        assertThat(SuspiciousSignup.reasons("09209099332", "mark.4821@gmail.com")).hasSize(1);
    }

    @Test
    void aNameWithTheWordTestIsFlaggedButNotWordsThatJustContainIt() {
        assertThat(SuspiciousSignup.reasons("09209099332", "a@b.com", "Test Catering", "Maria Santos"))
                .containsExactly("Name contains the word \"test\"");
        assertThat(SuspiciousSignup.reasons("09209099332", "a@b.com", "Maria Events", "Juan TEST"))
                .containsExactly("Name contains the word \"test\"");
        assertThat(SuspiciousSignup.reasons("09209099332", "a@b.com", "test1 Photos", null))
                .containsExactly("Name contains the word \"test\"");
        assertThat(SuspiciousSignup.reasons("09209099332", "a@b.com", "Latest Events", "Contest Master")).isEmpty();
        assertThat(SuspiciousSignup.reasons("09209099332", "a@b.com", (String) null)).isEmpty();
    }
}
