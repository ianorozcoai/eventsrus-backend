package com.backend.eventsrus.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * One admin-tunable business-rule value - see SystemSettingKey for the
 * fixed set of known keys and their defaults, and SystemSettingService for
 * the read/write path. Deliberately not a {@link com.backend.eventsrus.common.BaseEntity}
 * since the primary key here is the setting's own string key, not a
 * generated id.
 */
@Entity
@Table(name = "system_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SystemSetting {

    @Id
    @Column(name = "setting_key", nullable = false, updatable = false)
    private String settingKey;

    @Column(name = "setting_value", nullable = false)
    private String settingValue;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
