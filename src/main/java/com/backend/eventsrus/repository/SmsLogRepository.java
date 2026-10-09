package com.backend.eventsrus.repository;

import com.backend.eventsrus.model.SmsLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SmsLogRepository extends JpaRepository<SmsLog, Long> {

    List<SmsLog> findTop100ByOrderByCreatedAtDesc();
}
