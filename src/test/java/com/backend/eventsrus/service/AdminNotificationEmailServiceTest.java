package com.backend.eventsrus.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.backend.eventsrus.enums.Role;
import com.backend.eventsrus.enums.SignupIntent;
import com.backend.eventsrus.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * enabled mirrors app.admin-notifications-enabled, which has no default of
 * true anywhere in this repo - only Railway's production environment sets
 * it, so local/dev runs must never actually send mail. That's the one
 * behavior this test suite exists to lock in.
 */
@ExtendWith(MockitoExtension.class)
class AdminNotificationEmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    private AdminNotificationEmailService service;

    private static final User PLANNER = User.builder()
            .email("new.planner@example.com").firstName("Jamie").lastName("Cruz")
            .role(Role.PLANNER).signupIntent(SignupIntent.PLANNER).build();

    @BeforeEach
    void setUp() {
        service = new AdminNotificationEmailService(mailSender);
        ReflectionTestUtils.setField(service, "adminEmail", "admin.eventsrus@gmail.com");
    }

    @Test
    void sendsNothingWhenDisabled() {
        ReflectionTestUtils.setField(service, "enabled", false);

        service.notifyNewPlanner(PLANNER);

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void sendsToTheFixedAdminAddressWhenEnabled() {
        ReflectionTestUtils.setField(service, "enabled", true);

        service.notifyNewPlanner(PLANNER);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage sent = captor.getValue();
        org.assertj.core.api.Assertions.assertThat(sent.getTo()).containsExactly("admin.eventsrus@gmail.com");
        org.assertj.core.api.Assertions.assertThat(sent.getText()).contains("new.planner@example.com");
    }

    @Test
    void neverThrowsEvenWhenSendingFails() {
        ReflectionTestUtils.setField(service, "enabled", true);
        org.mockito.Mockito.doThrow(new org.springframework.mail.MailSendException("smtp down"))
                .when(mailSender).send(any(SimpleMailMessage.class));

        service.notifyNewVendor(PLANNER);
    }
}
