-- Planner "Events Coordinator" AI feature - one row per question a planner
-- asks about one of their events. Doubles as the usage log the daily quota
-- is computed from (see CoordinatorService#DAILY_QUESTION_LIMIT via
-- app.coordinator-daily-question-limit).
CREATE TABLE coordinator_questions (
    id BIGSERIAL PRIMARY KEY,
    event_id BIGINT NOT NULL REFERENCES events(id),
    planner_id BIGINT NOT NULL REFERENCES users(id),
    question TEXT NOT NULL,
    answer TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_coordinator_questions_event_id ON coordinator_questions(event_id);
-- Quota check is "how many has this planner asked since midnight" - across
-- all of their events, not just one.
CREATE INDEX idx_coordinator_questions_planner_created_at ON coordinator_questions(planner_id, created_at);
