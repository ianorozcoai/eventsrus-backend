package com.backend.eventsrus.repository;

import com.backend.eventsrus.model.EventTabView;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventTabViewRepository extends JpaRepository<EventTabView, Long> {

    Optional<EventTabView> findByUserIdAndEventId(Long userId, Long eventId);
}
