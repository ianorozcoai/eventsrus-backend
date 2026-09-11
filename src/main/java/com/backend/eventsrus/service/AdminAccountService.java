package com.backend.eventsrus.service;

import com.backend.eventsrus.exception.DuplicateAdminUsernameException;
import com.backend.eventsrus.exception.InvalidCurrentPasswordException;
import com.backend.eventsrus.model.AdminAccount;
import com.backend.eventsrus.repository.AdminAccountRepository;
import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Real, DB-backed admin logins (admin_accounts table) - replaces the old
 * eventsrus-web-side in-memory AdminAccountService, which held a hardcoded
 * plaintext master password in source. Passwords are BCrypt-hashed; nothing
 * plaintext is ever stored or logged.
 *
 * The very first account is bootstrapped once, on startup, from
 * ADMIN_BOOTSTRAP_USERNAME/ADMIN_BOOTSTRAP_PASSWORD (see application.properties)
 * - only when the table is still empty, so it's a one-time seed, not
 * something that re-creates or resets an account on every restart. Once at
 * least one account exists, further ones are created through the admin
 * module's own "Create Admin Account" form (AdminAccountController).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAccountService {

    private final AdminAccountRepository adminAccountRepository;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Value("${app.admin-bootstrap-username:}")
    private String bootstrapUsername;

    @Value("${app.admin-bootstrap-password:}")
    private String bootstrapPassword;

    @PostConstruct
    @Transactional
    public void bootstrapFirstAccountIfNeeded() {
        if (adminAccountRepository.count() > 0) {
            return;
        }
        if (bootstrapUsername.isBlank() || bootstrapPassword.isBlank()) {
            log.warn("No admin accounts exist yet and ADMIN_BOOTSTRAP_USERNAME/ADMIN_BOOTSTRAP_PASSWORD "
                    + "aren't set - the admin module has no way to log in until one is created "
                    + "(set those env vars and restart, or insert a row into admin_accounts directly).");
            return;
        }
        create(bootstrapUsername, bootstrapPassword);
        log.info("Bootstrapped the first admin account ('{}') from ADMIN_BOOTSTRAP_USERNAME/PASSWORD.", bootstrapUsername);
    }

    @Transactional(readOnly = true)
    public boolean authenticate(String username, String rawPassword) {
        return findByUsername(username)
                .map(account -> encoder.matches(rawPassword, account.getPasswordHash()))
                .orElse(false);
    }

    @Transactional
    public AdminAccount create(String username, String rawPassword) {
        if (adminAccountRepository.existsByUsernameIgnoreCase(username)) {
            throw new DuplicateAdminUsernameException("That username is already taken.");
        }
        AdminAccount account = AdminAccount.builder()
                .username(username)
                .passwordHash(encoder.encode(rawPassword))
                .build();
        return adminAccountRepository.save(account);
    }

    @Transactional(readOnly = true)
    public List<AdminAccount> listAll() {
        return adminAccountRepository.findAllByOrderByCreatedAtAsc();
    }

    /**
     * Self-service password change - username comes from the caller's own
     * JWT (see AdminAccountController#changePassword), not a request field,
     * so this can only ever change the logged-in admin's own password.
     */
    @Transactional
    public void changePassword(String username, String currentPassword, String newPassword) {
        AdminAccount account = findByUsername(username)
                .orElseThrow(() -> new InvalidCurrentPasswordException("Current password is incorrect."));
        if (!encoder.matches(currentPassword, account.getPasswordHash())) {
            throw new InvalidCurrentPasswordException("Current password is incorrect.");
        }
        account.setPasswordHash(encoder.encode(newPassword));
        adminAccountRepository.save(account);
    }

    private Optional<AdminAccount> findByUsername(String username) {
        return adminAccountRepository.findByUsernameIgnoreCase(username);
    }
}
