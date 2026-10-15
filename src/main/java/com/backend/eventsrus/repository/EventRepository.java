package com.backend.eventsrus.repository;

import com.backend.eventsrus.model.Event;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventRepository extends JpaRepository<Event, Long> {

    List<Event> findByPlannerIdOrderByCreatedAtDesc(Long plannerId);

    List<Event> findByPlannerIdAndSavedTrueOrderByCreatedAtDesc(Long plannerId);

    long countByPlannerId(Long plannerId);

    /** Account deletion: blank the free-text fields of a planner's events (name, place, notes, AI text). */
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(
            "update Event e set e.name = 'Deleted event', e.location = null, e.description = null, "
                    + "e.aiIdeaText = null where e.planner.id = :userId")
    int scrubEventsOf(@org.springframework.data.repository.query.Param("userId") Long userId);
}
