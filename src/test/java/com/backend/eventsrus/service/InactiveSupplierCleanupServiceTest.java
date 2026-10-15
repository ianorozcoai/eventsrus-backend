package com.backend.eventsrus.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.backend.eventsrus.enums.Role;
import com.backend.eventsrus.model.User;
import com.backend.eventsrus.model.VendorProfile;
import com.backend.eventsrus.model.VendorSubscription;
import com.backend.eventsrus.repository.UserRepository;
import com.backend.eventsrus.repository.VendorProfileRepository;
import com.backend.eventsrus.repository.VendorSubscriptionRepository;
import com.backend.eventsrus.service.S3UploadService.Visibility;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.transaction.PlatformTransactionManager;

@ExtendWith(MockitoExtension.class)
class InactiveSupplierCleanupServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-16T00:00:00Z");

    @Mock private UserRepository userRepository;
    @Mock private VendorProfileRepository vendorProfileRepository;
    @Mock private VendorSubscriptionRepository vendorSubscriptionRepository;
    @Mock private AccountDeletionService accountDeletionService;
    @Mock private S3UploadService s3UploadService;
    @Mock private JavaMailSender mailSender;
    @Mock private PlatformTransactionManager transactionManager;

    private InactiveSupplierCleanupService service;

    @BeforeEach
    void setUp() {
        service = newService(true);
    }

    private InactiveSupplierCleanupService newService(boolean enabled) {
        return new InactiveSupplierCleanupService(
                userRepository, vendorProfileRepository, vendorSubscriptionRepository, accountDeletionService,
                s3UploadService, mailSender, transactionManager, enabled, 12, 24, 30);
    }

    private static Instant monthsAgo(int months) {
        return ZonedDateTime.ofInstant(NOW, ZoneId.of("Asia/Manila")).minusMonths(months).toInstant();
    }

    /** A supplier whose last sign-in (and creation) was the given number of months ago. */
    private static User supplier(int lastActiveMonthsAgo) {
        User user = User.builder().id(5L).email("sup@example.com").firstName("Ana").googleId("g-5")
                .role(Role.VENDOR).lastLoginAt(monthsAgo(lastActiveMonthsAgo)).build();
        user.setCreatedAt(monthsAgo(lastActiveMonthsAgo));
        return user;
    }

    private void givenLapsed(User user) {
        when(userRepository.findByRoleAndDeletedAtIsNull(Role.VENDOR)).thenReturn(List.of(user));
        when(vendorSubscriptionRepository.existsByUserIdAndStatusIn(eq(5L), anyCollection())).thenReturn(false);
        when(vendorSubscriptionRepository.findFirstByUserIdOrderByCurrentPeriodStartDesc(5L)).thenReturn(Optional.empty());
    }

    @Test
    void doesNothingWhileSwitchedOff() {
        assertThat(newService(false).runOnce(NOW)).isZero();
        verify(userRepository, never()).findByRoleAndDeletedAtIsNull(any());
    }

    @Test
    void leavesARecentlyActiveLapsedSupplierAlone() {
        givenLapsed(supplier(6));

        assertThat(service.runOnce(NOW)).isZero();
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
        verify(accountDeletionService, never()).purgeAccount(any());
    }

    @Test
    void skipsDemoAccounts() {
        User demo = supplier(40);
        demo.setFakeAccount(true);
        when(userRepository.findByRoleAndDeletedAtIsNull(Role.VENDOR)).thenReturn(List.of(demo));

        assertThat(service.runOnce(NOW)).isZero();
    }

    @Test
    void removesStoredIdFilesAfterTwelveMonthsAndDropsTheVerifiedBadge() {
        User user = supplier(13);
        givenLapsed(user);
        VendorProfile profile = VendorProfile.builder().id(9L).user(user).idCardKey("id-key").selfieKey("selfie-key")
                .verified(true).build();
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));
        when(vendorProfileRepository.findByUserId(5L)).thenReturn(Optional.of(profile));

        assertThat(service.runOnce(NOW)).isEqualTo(1);

        assertThat(profile.getIdCardKey()).isNull();
        assertThat(profile.getSelfieKey()).isNull();
        assertThat(profile.isVerified()).isFalse();
        assertThat(user.getIdFilesPurgedAt()).isEqualTo(NOW);
        verify(s3UploadService).deleteQuietly("id-key", Visibility.PRIVATE);
        verify(s3UploadService).deleteQuietly("selfie-key", Visibility.PRIVATE);
        verify(accountDeletionService, never()).purgeAccount(any());
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void doesNotRepeatTheIdFileRemoval() {
        User user = supplier(13);
        user.setIdFilesPurgedAt(monthsAgo(1));
        givenLapsed(user);

        assertThat(service.runOnce(NOW)).isZero();
        verify(s3UploadService, never()).deleteQuietly(any(), any());
    }

    @Test
    void emailsAWarningAtTwentyFourMonthsAndStartsTheThirtyDays() {
        User user = supplier(25);
        user.setIdFilesPurgedAt(monthsAgo(12));
        givenLapsed(user);

        assertThat(service.runOnce(NOW)).isEqualTo(1);

        ArgumentCaptor<SimpleMailMessage> mail = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(mail.capture());
        assertThat(mail.getValue().getTo()).containsExactly("sup@example.com");
        assertThat(mail.getValue().getSubject()).contains("15 Nov 2026");
        assertThat(user.getInactivityWarningSentAt()).isEqualTo(NOW);
        verify(accountDeletionService, never()).purgeAccount(any());
    }

    @Test
    void doesNotStartTheThirtyDaysIfTheEmailCouldNotBeSent() {
        User user = supplier(25);
        user.setIdFilesPurgedAt(monthsAgo(12));
        givenLapsed(user);
        doThrow(new RuntimeException("smtp down")).when(mailSender).send(any(SimpleMailMessage.class));

        assertThat(service.runOnce(NOW)).isZero();
        assertThat(user.getInactivityWarningSentAt()).isNull();
    }

    @Test
    void deletesTheAccountThirtyOneDaysAfterTheWarning() {
        User user = supplier(26);
        user.setIdFilesPurgedAt(monthsAgo(14));
        user.setInactivityWarningSentAt(NOW.minus(Duration.ofDays(31)));
        givenLapsed(user);

        assertThat(service.runOnce(NOW)).isEqualTo(1);
        verify(accountDeletionService).purgeAccount(5L);
    }

    @Test
    void waitsOutTheThirtyDays() {
        User user = supplier(26);
        user.setIdFilesPurgedAt(monthsAgo(14));
        user.setInactivityWarningSentAt(NOW.minus(Duration.ofDays(10)));
        givenLapsed(user);

        assertThat(service.runOnce(NOW)).isZero();
        verify(accountDeletionService, never()).purgeAccount(any());
    }

    @Test
    void signingInAfterTheWarningCancelsTheDeletion() {
        // Signed in 2 months ago (UserService also clears the warning on sign-in; this covers the sweep seeing it).
        User user = supplier(2);
        user.setInactivityWarningSentAt(NOW.minus(Duration.ofDays(31)));
        givenLapsed(user);

        assertThat(service.runOnce(NOW)).isEqualTo(1);
        assertThat(user.getInactivityWarningSentAt()).isNull();
        verify(accountDeletionService, never()).purgeAccount(any());
    }

    @Test
    void aSupplierWithALiveSubscriptionIsNeverTouched() {
        User user = supplier(40);
        user.setInactivityWarningSentAt(NOW.minus(Duration.ofDays(31)));
        when(userRepository.findByRoleAndDeletedAtIsNull(Role.VENDOR)).thenReturn(List.of(user));
        when(vendorSubscriptionRepository.existsByUserIdAndStatusIn(eq(5L), anyCollection())).thenReturn(true);

        assertThat(service.runOnce(NOW)).isEqualTo(1);
        assertThat(user.getInactivityWarningSentAt()).isNull();
        verify(accountDeletionService, never()).purgeAccount(any());
        verify(s3UploadService, never()).deleteQuietly(any(), any());
    }

    @Test
    void aRecentlyEndedPaidPeriodCountsAsActivity() {
        User user = supplier(40);
        VendorSubscription subscription = new VendorSubscription();
        subscription.setCurrentPeriodEnd(monthsAgo(3));
        when(userRepository.findByRoleAndDeletedAtIsNull(Role.VENDOR)).thenReturn(List.of(user));
        when(vendorSubscriptionRepository.existsByUserIdAndStatusIn(eq(5L), anyCollection())).thenReturn(false);
        when(vendorSubscriptionRepository.findFirstByUserIdOrderByCurrentPeriodStartDesc(5L))
                .thenReturn(Optional.of(subscription));

        assertThat(service.runOnce(NOW)).isZero();
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }
}
