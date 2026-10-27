package com.backend.eventsrus.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.backend.eventsrus.enums.Role;
import com.backend.eventsrus.enums.SignupIntent;
import com.backend.eventsrus.exception.AccountIdentityConflictException;
import com.backend.eventsrus.model.User;
import com.backend.eventsrus.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Sign in with Apple account rules: returning user, linking to a Google account by verified email, new account. */
class UserServiceAppleSignInTest {

    private UserRepository userRepository;
    private AdminNotificationEmailService adminNotificationEmailService;
    private UserService userService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        adminNotificationEmailService = mock(AdminNotificationEmailService.class);
        userService = new UserService(
                userRepository, null, null, null, null, null, null, null, null, null, null, null, null,
                adminNotificationEmailService, null);
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
    }

    private static AppleIdentity real(String email) {
        return new AppleIdentity("apple-sub-1", email, true, false);
    }

    @Test
    void aReturningAppleUserIsFoundByTheirAppleId() {
        User existing = User.builder().appleId("apple-sub-1").email("a@example.com").firstName("Ana")
                .role(Role.PLANNER).signupIntent(SignupIntent.PLANNER).build();
        when(userRepository.findByAppleId("apple-sub-1")).thenReturn(Optional.of(existing));

        User result = userService.findOrCreateFromApple(real("a@example.com"), "Other", "Name", null);

        assertThat(result).isSameAs(existing);
        // Apple only sends the name once; a later one must never overwrite what we have.
        assertThat(result.getFirstName()).isEqualTo("Ana");
        assertThat(result.getLastLoginAt()).isNotNull();
        verify(userRepository, never()).findByEmail(any());
    }

    @Test
    void aFirstTimeAppleUserWithTheEmailOfAGoogleAccountIsLinkedToIt() {
        User google = User.builder().googleId("g-1").email("person@example.com").role(Role.VENDOR)
                .signupIntent(SignupIntent.VENDOR).build();
        when(userRepository.findByAppleId("apple-sub-1")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("person@example.com")).thenReturn(Optional.of(google));

        User result = userService.findOrCreateFromApple(real("person@example.com"), "Ian", "Orozco", null);

        assertThat(result).isSameAs(google);
        assertThat(result.getAppleId()).isEqualTo("apple-sub-1");
        assertThat(result.getGoogleId()).isEqualTo("g-1");
        verify(adminNotificationEmailService, never()).notifyNewPlanner(any());
    }

    @Test
    void anUnverifiedEmailIsNeverUsedToLinkAnAccount() {
        when(userRepository.findByAppleId("apple-sub-1")).thenReturn(Optional.empty());

        User result = userService.findOrCreateFromApple(
                new AppleIdentity("apple-sub-1", "victim@example.com", false, false), "Eve", "X", null);

        verify(userRepository, never()).findByEmail(any());
        assertThat(result.getAppleId()).isEqualTo("apple-sub-1");
        assertThat(result.getGoogleId()).isNull();
    }

    @Test
    void aPrivateRelayEmailCreatesANewAccountWithoutLooking() {
        when(userRepository.findByAppleId("apple-sub-1")).thenReturn(Optional.empty());

        User result = userService.findOrCreateFromApple(
                new AppleIdentity("apple-sub-1", "abc123@privaterelay.appleid.com", true, true), "Ian", "Orozco", "planner");

        verify(userRepository, never()).findByEmail(any());
        assertThat(result.getEmail()).isEqualTo("abc123@privaterelay.appleid.com");
        assertThat(result.getFirstName()).isEqualTo("Ian");
        assertThat(result.getRole()).isEqualTo(Role.PLANNER);
        assertThat(result.getSignupIntent()).isEqualTo(SignupIntent.PLANNER);
        verify(adminNotificationEmailService).notifyNewPlanner(result);
    }

    @Test
    void aNewVendorSignUpKeepsTheVendorIntent() {
        when(userRepository.findByAppleId("apple-sub-1")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());

        User result = userService.findOrCreateFromApple(real("new@example.com"), null, null, "vendor");

        assertThat(result.getSignupIntent()).isEqualTo(SignupIntent.VENDOR);
        assertThat(result.getFirstName()).isNull();
        verify(adminNotificationEmailService, never()).notifyNewPlanner(any());
    }

    @Test
    void theOneEmailOneIdentityRuleAlsoAppliesWhenLinking() {
        User planner = User.builder().googleId("g-2").email("p@example.com").role(Role.PLANNER)
                .signupIntent(SignupIntent.PLANNER).build();
        when(userRepository.findByAppleId("apple-sub-1")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("p@example.com")).thenReturn(Optional.of(planner));

        assertThatThrownBy(() -> userService.findOrCreateFromApple(real("p@example.com"), null, null, "vendor"))
                .isInstanceOf(AccountIdentityConflictException.class);
        assertThat(planner.getAppleId()).isNull();
    }

    @Test
    void signingInAgainCancelsAPendingDeletion() {
        User existing = User.builder().appleId("apple-sub-1").role(Role.PLANNER).signupIntent(SignupIntent.PLANNER).build();
        existing.setDeletionRequestedAt(java.time.Instant.now());
        when(userRepository.findByAppleId("apple-sub-1")).thenReturn(Optional.of(existing));

        userService.findOrCreateFromApple(real("a@example.com"), null, null, null);

        assertThat(existing.getDeletionRequestedAt()).isNull();
    }
}
