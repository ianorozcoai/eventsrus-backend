package com.backend.eventsrus.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.backend.eventsrus.exception.DuplicateAdminUsernameException;
import com.backend.eventsrus.exception.InvalidCurrentPasswordException;
import com.backend.eventsrus.model.AdminAccount;
import com.backend.eventsrus.repository.AdminAccountRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * The security-critical piece this whole class replaced a hardcoded
 * plaintext master password with (see the class javadoc there): real
 * BCrypt hashing, a real DB-backed store, and a bootstrap that only ever
 * fires once, while the table is still empty.
 */
@ExtendWith(MockitoExtension.class)
class AdminAccountServiceTest {

    @Mock
    private AdminAccountRepository adminAccountRepository;

    private AdminAccountService adminAccountService;

    @BeforeEach
    void setUp() {
        adminAccountService = new AdminAccountService(adminAccountRepository);
    }

    @Nested
    class Authenticate {

        @Test
        void trueForTheRightPassword() {
            AdminAccount account = new AdminAccount();
            account.setUsername("ianadmin");
            account.setPasswordHash(new BCryptPasswordEncoder().encode("correct-horse-battery-staple"));
            when(adminAccountRepository.findByUsernameIgnoreCase("ianadmin")).thenReturn(Optional.of(account));

            assertThat(adminAccountService.authenticate("ianadmin", "correct-horse-battery-staple")).isTrue();
        }

        @Test
        void falseForTheWrongPassword() {
            AdminAccount account = new AdminAccount();
            account.setUsername("ianadmin");
            account.setPasswordHash(new BCryptPasswordEncoder().encode("correct-horse-battery-staple"));
            when(adminAccountRepository.findByUsernameIgnoreCase("ianadmin")).thenReturn(Optional.of(account));

            assertThat(adminAccountService.authenticate("ianadmin", "wrong")).isFalse();
        }

        @Test
        void falseWhenThePasswordThatUsedToBeHardcodedInSourceIsTried() {
            // Regression guard: the old in-memory eventsrus-web AdminAccountService
            // had MASTER_PASSWORD = "Kerberos103!" baked into source. That
            // string means nothing to this real, DB-backed implementation -
            // there's no account it could ever match.
            when(adminAccountRepository.findByUsernameIgnoreCase("ianadmin")).thenReturn(Optional.empty());

            assertThat(adminAccountService.authenticate("ianadmin", "Kerberos103!")).isFalse();
        }

        @Test
        void falseForAnUnknownUsername() {
            when(adminAccountRepository.findByUsernameIgnoreCase("ghost")).thenReturn(Optional.empty());

            assertThat(adminAccountService.authenticate("ghost", "anything")).isFalse();
        }
    }

    @Nested
    class Create {

        @Test
        void throwsWhenUsernameAlreadyTaken() {
            when(adminAccountRepository.existsByUsernameIgnoreCase("ianadmin")).thenReturn(true);

            assertThatThrownBy(() -> adminAccountService.create("ianadmin", "somePassword123"))
                    .isInstanceOf(DuplicateAdminUsernameException.class);
            verify(adminAccountRepository, never()).save(any());
        }

        @Test
        void hashesThePasswordBeforeSaving() {
            when(adminAccountRepository.existsByUsernameIgnoreCase("newadmin")).thenReturn(false);
            when(adminAccountRepository.save(any(AdminAccount.class))).thenAnswer(i -> i.getArgument(0));

            AdminAccount saved = adminAccountService.create("newadmin", "somePassword123");

            assertThat(saved.getUsername()).isEqualTo("newadmin");
            assertThat(saved.getPasswordHash()).isNotEqualTo("somePassword123");
            assertThat(new BCryptPasswordEncoder().matches("somePassword123", saved.getPasswordHash())).isTrue();
        }
    }

    @Nested
    class ListAll {

        @Test
        void delegatesToTheOrderedRepositoryQuery() {
            AdminAccount a = new AdminAccount();
            when(adminAccountRepository.findAllByOrderByCreatedAtAsc()).thenReturn(List.of(a));

            assertThat(adminAccountService.listAll()).containsExactly(a);
        }
    }

    @Nested
    class ChangePassword {

        @Test
        void updatesTheHashWhenCurrentPasswordIsCorrect() {
            AdminAccount account = new AdminAccount();
            account.setUsername("ianadmin");
            account.setPasswordHash(new BCryptPasswordEncoder().encode("oldPassword123"));
            when(adminAccountRepository.findByUsernameIgnoreCase("ianadmin")).thenReturn(Optional.of(account));

            adminAccountService.changePassword("ianadmin", "oldPassword123", "newPassword456");

            verify(adminAccountRepository).save(account);
            assertThat(new BCryptPasswordEncoder().matches("newPassword456", account.getPasswordHash())).isTrue();
            assertThat(new BCryptPasswordEncoder().matches("oldPassword123", account.getPasswordHash())).isFalse();
        }

        @Test
        void rejectsAWrongCurrentPassword() {
            AdminAccount account = new AdminAccount();
            account.setUsername("ianadmin");
            account.setPasswordHash(new BCryptPasswordEncoder().encode("oldPassword123"));
            when(adminAccountRepository.findByUsernameIgnoreCase("ianadmin")).thenReturn(Optional.of(account));

            assertThatThrownBy(() -> adminAccountService.changePassword("ianadmin", "wrongPassword", "newPassword456"))
                    .isInstanceOf(InvalidCurrentPasswordException.class);
            verify(adminAccountRepository, never()).save(any());
        }

        @Test
        void rejectsAnUnknownUsername() {
            when(adminAccountRepository.findByUsernameIgnoreCase("ghost")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> adminAccountService.changePassword("ghost", "anything", "newPassword456"))
                    .isInstanceOf(InvalidCurrentPasswordException.class);
            verify(adminAccountRepository, never()).save(any());
        }
    }

    @Nested
    class Bootstrap {

        @Test
        void doesNothingWhenAnAccountAlreadyExists() {
            when(adminAccountRepository.count()).thenReturn(1L);

            adminAccountService.bootstrapFirstAccountIfNeeded();

            verify(adminAccountRepository, never()).save(any());
            verify(adminAccountRepository, never()).existsByUsernameIgnoreCase(org.mockito.ArgumentMatchers.anyString());
        }

        @Test
        void doesNothingWhenTableIsEmptyButNoBootstrapVarsAreSet() {
            when(adminAccountRepository.count()).thenReturn(0L);
            ReflectionTestUtils.setField(adminAccountService, "bootstrapUsername", "");
            ReflectionTestUtils.setField(adminAccountService, "bootstrapPassword", "");

            adminAccountService.bootstrapFirstAccountIfNeeded();

            verify(adminAccountRepository, never()).save(any());
        }

        @Test
        void createsTheFirstAccountFromBootstrapVarsWhenTableIsEmpty() {
            when(adminAccountRepository.count()).thenReturn(0L);
            when(adminAccountRepository.existsByUsernameIgnoreCase("ianadmin")).thenReturn(false);
            when(adminAccountRepository.save(any(AdminAccount.class))).thenAnswer(i -> i.getArgument(0));
            ReflectionTestUtils.setField(adminAccountService, "bootstrapUsername", "ianadmin");
            ReflectionTestUtils.setField(adminAccountService, "bootstrapPassword", "freshLocalPassword1");

            adminAccountService.bootstrapFirstAccountIfNeeded();

            verify(adminAccountRepository, times(1)).save(any(AdminAccount.class));
        }
    }
}
