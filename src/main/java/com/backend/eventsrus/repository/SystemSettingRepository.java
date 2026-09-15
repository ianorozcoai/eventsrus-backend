package com.backend.eventsrus.repository;

import com.backend.eventsrus.model.SystemSetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SystemSettingRepository extends JpaRepository<SystemSetting, String> {}
