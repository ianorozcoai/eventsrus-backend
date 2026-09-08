package com.backend.eventsrus.repository;

import com.backend.eventsrus.model.Event;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventRepository extends JpaRepository<Event, Long> {

    List<Event> findByPlannerIdOrderByCreatedAtDesc(Long plannerId);

    List<Event> findByPlannerIdAndSavedTrueOrderByCreatedAtDesc(Long plannerId);

    long countByPlannerId(Long plannerId);
}
