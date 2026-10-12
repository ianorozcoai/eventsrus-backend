package com.backend.eventsrus.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.backend.eventsrus.dto.AuthResponse;
import com.backend.eventsrus.enums.Role;
import com.backend.eventsrus.enums.SignupIntent;
import com.backend.eventsrus.model.User;
import com.backend.eventsrus.repository.UserRepository;
import com.backend.eventsrus.service.AuthService;
import com.backend.eventsrus.service.RefreshTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Regression guard for a LazyInitializationException class of bug found
 * three separate times this session via live manual testing alone - every
 * other backend test mocks the repository layer entirely, so none of them
 * touch a real Hibernate session and none could reproduce this.
 *
 * Specifically: RefreshTokenService#validateAndRotate is its own separately
 * @Transactional method - its session closes the moment it returns, and the
 * User proxy it hands back stays bound to that now-dead session.
 * AuthService#issueTokenFor opening a new transaction doesn't help on its
 * own, since a Hibernate proxy resolves lazy fields through the session it
 * was created in, not whatever's active on the calling thread. The fix was
 * moving @Transactional up to AuthService#refresh itself, so
 * validateAndRotate joins that same transaction/session (Spring's default
 * REQUIRED propagation) instead of opening its own. If that annotation is
 * ever accidentally removed, this test fails loudly instead of the bug only
 * surfacing again via live testing.
 *
 * Real Postgres (not H2) via Testcontainers - migrations use Postgres-only
 * syntax (jsonb, gen_random_uuid()), and faithfully reproducing Hibernate's
 * lazy-proxy/session behavior needs the real thing, not an approximation.
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class AuthServiceLazyLoadingIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RefreshTokenService refreshTokenService;
    @Autowired
    private AuthService authService;

    @Test
    void refreshDoesNotThrowLazyInitializationExceptionAndReturnsAPopulatedResponse() {
        User user = userRepository.save(User.builder()
                .googleId("test-google-id-" + System.nanoTime())
                .email("planner@example.com")
                .firstName("Jane")
                .lastName("Dela Cruz")
                .role(Role.PLANNER)
                .signupIntent(SignupIntent.PLANNER)
                .build());

        // issue() is itself @Transactional and returns after its own
        // transaction/session has already closed - the User instance
        // refresh(...) eventually resolves from the stored RefreshToken is
        // a lazy proxy bound to THAT closed session, not this one. This is
        // the exact real-world sequence that broke in production.
        String rawRefreshToken = refreshTokenService.issue(user);

        // No try/catch on purpose - if a LazyInitializationException (or
        // anything else) is thrown, JUnit reports this test as a failure
        // with that exception's own stack trace, which is exactly the
        // signal this test exists to produce.
        AuthResponse response = authService.refresh(rawRefreshToken);

        assertThat(response.getEmail()).isEqualTo("planner@example.com");
        assertThat(response.getRole()).isEqualTo(Role.PLANNER);
        assertThat(response.getToken()).isNotBlank();
        assertThat(response.getRefreshToken()).isNotBlank();
    }
}
