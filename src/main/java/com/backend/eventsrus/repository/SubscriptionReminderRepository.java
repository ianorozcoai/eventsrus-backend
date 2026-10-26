package com.backend.eventsrus.repository;

import com.backend.eventsrus.model.SubscriptionReminder;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionReminderRepository extends JpaRepository<SubscriptionReminder, Long> {

    boolean existsByUserIdAndAccessEndDateAndLeadDaysAndChannel(
            Long userId, LocalDate accessEndDate, int leadDays, String channel);
}
