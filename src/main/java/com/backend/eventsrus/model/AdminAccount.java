package com.backend.eventsrus.model;

import com.backend.eventsrus.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * A login for eventsrus-web's admin module - entirely separate from the
 * planner/vendor Google-OAuth {@link User} table (no google_id, no role
 * column - every row here is implicitly an admin). Real, DB-backed storage;
 * see AdminAccountService for the BCrypt hashing and the one-time bootstrap
 * of the first account from ADMIN_BOOTSTRAP_USERNAME/PASSWORD.
 */
@Entity
@Table(name = "admin_accounts")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class AdminAccount extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;
}
