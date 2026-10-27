package com.backend.eventsrus.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.backend.eventsrus.common.TermsConstants;
import com.backend.eventsrus.model.User;
import com.backend.eventsrus.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TermsAcceptanceServiceTest {

    @Mock
    private UserRepository userRepository;
    @InjectMocks
    private TermsAcceptanceService service;

    private User user(String acceptedVersion) {
        User user = User.builder().id(1L).email("a@example.com").build();
        user.setTermsVersion(acceptedVersion);
        when(userRepository.findByEmail("a@example.com")).thenReturn(Optional.of(user));
        return user;
    }

    @Test
    void aPersonWhoNeverAcceptedIsAskedToAccept() {
        user(null);

        var status = service.status("a@example.com");

        assertThat(status.accepted()).isFalse();
        assertThat(status.currentVersion()).isEqualTo(TermsConstants.CURRENT_VERSION);
    }

    @Test
    void acceptingTheCurrentVersionIsRememberedWithTheDate() {
        User user = user(null);

        var status = service.accept("a@example.com");

        assertThat(status.accepted()).isTrue();
        assertThat(user.getTermsVersion()).isEqualTo(TermsConstants.CURRENT_VERSION);
        assertThat(user.getTermsAcceptedAt()).isNotNull();
        verify(userRepository).save(user);
    }

    @Test
    void anAcceptedPersonIsNotAskedAgain() {
        user(TermsConstants.CURRENT_VERSION);

        assertThat(service.status("a@example.com").accepted()).isTrue();
    }

    @Test
    void anOlderAcceptanceMeansAskingOnceMoreWhenTheTermsAreBumped() {
        user("2026-09-03"); // an older version of the Terms

        assertThat(service.status("a@example.com").accepted()).isFalse();
    }

    @Test
    void anUnknownUserIsAnError() {
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.status("nobody@example.com")).isInstanceOf(IllegalStateException.class);
    }
}
