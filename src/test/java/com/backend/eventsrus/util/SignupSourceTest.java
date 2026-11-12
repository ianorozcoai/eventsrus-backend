package com.backend.eventsrus.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SignupSourceTest {

    @Test
    void headerWinsAndIsCaseInsensitive() {
        assertThat(SignupSource.resolve("web", "Dart/3.4")).isEqualTo("WEB");
        assertThat(SignupSource.resolve("Android", null)).isEqualTo("ANDROID");
        assertThat(SignupSource.resolve(" IOS ", null)).isEqualTo("IOS");
    }

    @Test
    void oldMobileBuildsWithoutTheHeaderReadAsMobileFromTheDartUserAgent() {
        assertThat(SignupSource.resolve(null, "Dart/3.5 (dart:io)")).isEqualTo("MOBILE");
    }

    @Test
    void anythingElseIsUnknown() {
        assertThat(SignupSource.resolve(null, "Mozilla/5.0")).isEqualTo("UNKNOWN");
        assertThat(SignupSource.resolve("toaster", null)).isEqualTo("UNKNOWN");
        assertThat(SignupSource.resolve(null, null)).isEqualTo("UNKNOWN");
    }

    @Test
    void labelsAreInPlainWords() {
        assertThat(SignupSource.label("WEB")).isEqualTo("Website");
        assertThat(SignupSource.label("ANDROID")).isEqualTo("Android app");
        assertThat(SignupSource.label("IOS")).isEqualTo("iPhone app");
        assertThat(SignupSource.label(null)).startsWith("Unknown");
    }
}
