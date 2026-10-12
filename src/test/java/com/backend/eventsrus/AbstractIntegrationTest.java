package com.backend.eventsrus;

import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Shared base for any test that boots a full Spring context - wires a real
 * Testcontainers Postgres (migrations use Postgres-only syntax, H2 isn't a
 * safe substitute) so the context can start with no ambient environment
 * variables at all. Without this, a @SpringBootTest needs DB_URL/
 * JWT_SECRET/etc. from the real shell environment, which CI never
 * supplies - this was invisible before this repo had any CI (nothing ran
 * `mvn test` automatically, and Railway's deploy used -DskipTests) and
 * surfaced the moment CI started actually running tests.
 * The container is static, so every subclass in the same JVM fork shares
 * one instance rather than each spinning up its own.
 */
@Testcontainers
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
