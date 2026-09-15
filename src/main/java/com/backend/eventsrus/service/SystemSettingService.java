package com.backend.eventsrus.service;

import com.backend.eventsrus.dto.SystemSettingResponse;
import com.backend.eventsrus.enums.SystemSettingKey;
import com.backend.eventsrus.exception.InvalidSystemSettingException;
import com.backend.eventsrus.model.SystemSetting;
import com.backend.eventsrus.repository.SystemSettingRepository;
import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read/write path for the admin-tunable values enumerated in
 * SystemSettingKey (vendor trial length, subscription warning window,
 * referral commission, coordinator daily question limit) - these used to be
 * application.properties @Value defaults, now DB-backed (see migration V39)
 * so an admin can change them from the admin module without a redeploy.
 * Values are cached in memory after the first load and mutated only through
 * updateValue (DB row + cache entry written together), so the hot paths
 * that read these - vendor signup, the coordinator's per-question quota
 * check, etc. - never hit the DB.
 */
@Service
@RequiredArgsConstructor
public class SystemSettingService {

    private final SystemSettingRepository systemSettingRepository;

    private final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();

    @PostConstruct
    void loadCache() {
        systemSettingRepository.findAll().forEach(setting -> cache.put(setting.getSettingKey(), setting.getSettingValue()));

        // Defensive seed for any key missing from the table (e.g. a new
        // SystemSettingKey constant added after V39 already ran) - same
        // fallback-to-default behavior the old @Value("${...:default}")
        // fields had.
        for (SystemSettingKey key : SystemSettingKey.values()) {
            cache.computeIfAbsent(key.key(), k -> {
                SystemSetting seeded = new SystemSetting();
                seeded.setSettingKey(key.key());
                seeded.setSettingValue(key.defaultValue());
                systemSettingRepository.save(seeded);
                return key.defaultValue();
            });
        }
    }

    public int getInt(SystemSettingKey key) {
        return Integer.parseInt(cache.getOrDefault(key.key(), key.defaultValue()));
    }

    public BigDecimal getBigDecimal(SystemSettingKey key) {
        return new BigDecimal(cache.getOrDefault(key.key(), key.defaultValue()));
    }

    public List<SystemSettingResponse> listAll() {
        return Arrays.stream(SystemSettingKey.values())
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public SystemSettingResponse updateValue(String rawKey, String rawValue) {
        SystemSettingKey key = SystemSettingKey.fromKey(rawKey)
                .orElseThrow(() -> new InvalidSystemSettingException("Unknown setting: " + rawKey));

        String trimmed = rawValue == null ? "" : rawValue.trim();
        int parsed;
        try {
            parsed = Integer.parseInt(trimmed);
        } catch (NumberFormatException e) {
            throw new InvalidSystemSettingException("Value must be a whole number.");
        }
        if (parsed < 0) {
            throw new InvalidSystemSettingException("Value must be zero or greater.");
        }

        SystemSetting setting = systemSettingRepository.findById(key.key())
                .orElseGet(() -> {
                    SystemSetting created = new SystemSetting();
                    created.setSettingKey(key.key());
                    return created;
                });
        setting.setSettingValue(String.valueOf(parsed));
        systemSettingRepository.save(setting);
        cache.put(key.key(), String.valueOf(parsed));

        return toResponse(key);
    }

    private SystemSettingResponse toResponse(SystemSettingKey key) {
        return SystemSettingResponse.builder()
                .key(key.key())
                .label(key.label())
                .description(key.description())
                .value(cache.getOrDefault(key.key(), key.defaultValue()))
                .build();
    }
}
