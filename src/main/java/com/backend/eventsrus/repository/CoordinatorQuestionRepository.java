package com.backend.eventsrus.repository;

import com.backend.eventsrus.model.CoordinatorQuestion;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CoordinatorQuestionRepository extends JpaRepository<CoordinatorQuestion, Long> {

    List<CoordinatorQuestion> findByEventIdOrderByCreatedAtAsc(Long eventId);

    /** Quota check - how many questions this planner has asked (across all their events) since some instant. */
    long countByPlannerIdAndCreatedAtAfter(Long plannerId, Instant since);
}
