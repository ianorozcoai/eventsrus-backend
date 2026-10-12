package com.backend.eventsrus;

import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Shared base for any test that boots a full Spring context - wires a real
 * Testcontainers Postgres (migrations use Postgres-only syntax, H2 isn't a
 * safe substitute) so the context can start with no ambient environment
 * variables at all. Without this, a @SpringBootTest needs DB_URL/
 * JWT_SECRET/etc. from the real shell environment, which CI never
 * supplies - this was invisible before this repo had any CI (nothing ran
 * `mvn test` automatically, and Railway's deploy used -DskipTests) and
 * surfaced the moment CI started actually running tests.
 *
 * Deliberately NOT using @Testcontainers/@Container here. That combination
 * only manages a container's lifecycle within the single class that
 * declares it - when inherited by two unrelated top-level test classes, the
 * JUnit5 extension starts a SEPARATE container per class and tears the
 * first one down once that class finishes, even though the field itself is
 * static. That exact failure mode showed up in CI: EventsrusApplicationTests
 * passed against its own container, then AuthServiceLazyLoadingIntegrationTest
 * started a second container on a different port while Spring's cached
 * ApplicationContext (same configuration signature, so Spring reuses it)
 * kept its DataSource pointed at the now-stopped first container, which the
 * second class promptly got connection-refused against.
 *
 * This is Testcontainers' documented "singleton container" pattern instead:
 * start it once in a static initializer with no extension managing it, so
 * nothing stops it between classes - Ryuk reaps it at JVM exit regardless.
 */
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
