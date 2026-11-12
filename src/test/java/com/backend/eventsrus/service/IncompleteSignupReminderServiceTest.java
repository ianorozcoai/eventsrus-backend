package com.backend.eventsrus.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.backend.eventsrus.enums.Role;
import com.backend.eventsrus.enums.SignupIntent;
import com.backend.eventsrus.model.User;
import com.backend.eventsrus.repository.UserRepository;
import com.backend.eventsrus.service.IncompleteSignupReminderService.Outcome;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.time.Instant;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

class IncompleteSignupReminderServiceTest {

    private UserRepository users;
    private JavaMailSender mail;
    private IncompleteSignupReminderService service;
    private User user;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        mail = mock(JavaMailSender.class);
        when(mail.createMimeMessage()).thenAnswer(i -> new MimeMessage(Session.getInstance(new Properties())));
        service = new IncompleteSignupReminderService(users, mail);
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "supportEmail", "support@eventsrus.app");
        ReflectionTestUtils.setField(service, "frontendBaseUrl", "https://eventsrus.app");

        user = new User();
        user.setId(5L);
        user.setEmail("maria@example.com");
        user.setFirstName("Maria");
        user.setSignupIntent(SignupIntent.VENDOR);
        user.setRole(Role.PLANNER);
        when(users.findById(5L)).thenReturn(java.util.Optional.of(user));
    }

    @Test
    void sendsFromAndRepliesToSupportAndRecordsIt() throws Exception {
        assertThat(service.sendTo(5L).outcome()).isEqualTo(Outcome.SENT);

        ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mail).send(sent.capture());
        MimeMessage m = sent.getValue();
        assertThat(m.getFrom()[0].toString()).contains("support@eventsrus.app");
        assertThat(m.getReplyTo()[0].toString()).contains("support@eventsrus.app");
        assertThat(m.getAllRecipients()[0].toString()).isEqualTo("maria@example.com");
        assertThat(user.getSignupReminderCount()).isEqualTo(1);
        assertThat(user.getSignupReminderLastSentAt()).isNotNull();
        verify(users).save(user);
    }

    @Test
    void theEmailsSayWhatToDoAndLinkBackIntoTheSupplierSignup() {
        String plain = IncompleteSignupReminderService.plainText("Maria", "https://eventsrus.app/vendor/", "support@eventsrus.app");
        String html = IncompleteSignupReminderService.html("<b>Maria</b>", "https://eventsrus.app/vendor/", "support@eventsrus.app");

        assertThat(plain).startsWith("Hi Maria,").contains("https://eventsrus.app/vendor/", "support@eventsrus.app", "basic information");
        assertThat(html).contains("href=\"https://eventsrus.app/vendor/\"", "Finish my sign-up").doesNotContain("<b>Maria</b>");
        assertThat(IncompleteSignupReminderService.plainText(null, "x", "s")).startsWith("Hi there,");
    }

    @Test
    void notAgainWithinAWeekAndNeverMoreThanThree() {
        user.setSignupReminderCount(1);
        user.setSignupReminderLastSentAt(Instant.now().minusSeconds(2 * 86400));
        assertThat(service.sendTo(5L).outcome()).isEqualTo(Outcome.TOO_SOON);

        user.setSignupReminderLastSentAt(Instant.now().minusSeconds(8 * 86400));
        user.setSignupReminderCount(3);
        assertThat(service.sendTo(5L).outcome()).isEqualTo(Outcome.LIMIT_REACHED);
        verify(mail, never()).send(any(MimeMessage.class));
    }

    @Test
    void onlyPeopleWhoStillHaveNotFinishedAreEmailed() {
        user.setRole(Role.VENDOR);
        assertThat(service.sendTo(5L).outcome()).isEqualTo(Outcome.NOT_ELIGIBLE);
        user.setRole(Role.PLANNER);
        user.setFakeAccount(true);
        assertThat(service.sendTo(5L).outcome()).isEqualTo(Outcome.NOT_ELIGIBLE);
        user.setFakeAccount(false);
        user.setDeletedAt(Instant.now());
        assertThat(service.sendTo(5L).outcome()).isEqualTo(Outcome.NOT_ELIGIBLE);
        assertThat(service.sendTo(99L).outcome()).isEqualTo(Outcome.NOT_ELIGIBLE);
        verify(mail, never()).send(any(MimeMessage.class));
    }

    @Test
    void whenEmailIsSwitchedOffNothingIsSentOrCounted() {
        ReflectionTestUtils.setField(service, "enabled", false);

        assertThat(service.sendTo(5L).outcome()).isEqualTo(Outcome.EMAIL_OFF);
        verify(mail, never()).send(any(MimeMessage.class));
        assertThat(user.getSignupReminderCount()).isZero();
    }

    @Test
    void aFailedSendIsReportedAndNotCounted() {
        doThrow(new org.springframework.mail.MailSendException("smtp down")).when(mail).send(any(MimeMessage.class));

        assertThat(service.sendTo(5L).outcome()).isEqualTo(Outcome.FAILED);
        assertThat(user.getSignupReminderCount()).isZero();
        verify(users, never()).save(any());
    }

    @Test
    void emailAllSkipsAnyoneNotAllowedYet() {
        User recent = new User();
        recent.setId(6L);
        recent.setEmail("recent@example.com");
        recent.setSignupIntent(SignupIntent.VENDOR);
        recent.setRole(Role.PLANNER);
        recent.setSignupReminderCount(1);
        recent.setSignupReminderLastSentAt(Instant.now().minusSeconds(3600));
        when(users.findBySignupIntentAndRoleNotOrderByCreatedAtDesc(SignupIntent.VENDOR, Role.VENDOR))
                .thenReturn(List.of(user, recent));

        List<IncompleteSignupReminderService.Result> results = service.sendToAll();

        assertThat(results).extracting(IncompleteSignupReminderService.Result::outcome)
                .containsExactly(Outcome.SENT, Outcome.TOO_SOON);
    }
}
