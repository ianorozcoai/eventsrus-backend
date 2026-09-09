package com.backend.eventsrus.service;

import com.backend.eventsrus.dto.CoordinatorHistoryResponse;
import com.backend.eventsrus.dto.CoordinatorQuestionResponse;
import com.backend.eventsrus.exception.CoordinatorQuotaExceededException;
import com.backend.eventsrus.model.CoordinatorQuestion;
import com.backend.eventsrus.model.Event;
import com.backend.eventsrus.model.User;
import com.backend.eventsrus.repository.CoordinatorQuestionRepository;
import com.backend.eventsrus.repository.EventRepository;
import com.backend.eventsrus.repository.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The planner-facing "Events Coordinator" - a scoped AI ideas/advice
 * assistant, deliberately NOT a general chatbot and NOT a stand-in for
 * vendor conversations. It only ever sees one event's basic facts (type,
 * date, location, description) - never vendor, quotation, or booking data -
 * and its system prompt explicitly redirects anything vendor-specific back
 * to the real planner-vendor conversation (see ConversationService). This
 * keeps both the token cost and the product surface small on purpose.
 */
@Service
public class CoordinatorService {

    private static final ZoneId MANILA = ZoneId.of("Asia/Manila");

    private static final String SYSTEM_PROMPT = """
            You are the Events Coordinator inside EventsRUs, an event-planning \
            marketplace in the Philippines. You help planners brainstorm ideas \
            for their own event - themes, timelines, budgets, general \
            planning advice.

            Rules you must follow:
            - Only give general event-planning ideas, inspiration, and advice.
            - Never discuss a specific vendor's pricing, availability, \
            negotiation, or booking status, and never invent details about a \
            vendor. If asked anything like that, say it's something to ask \
            the vendor directly through their conversation in the app.
            - Keep answers short and practical - a few sentences or a short \
            list, not an essay.
            - Plain text only - this is displayed as-is, with no markdown \
            rendering. Never use **bold**, #headers, or markdown bullet/ \
            numbered syntax. For a list, put each item on its own line \
            starting with a plain dash and a space ("- like this"), with a \
            blank line between sections if needed.
            - If a question has nothing to do with planning this event, \
            gently steer back to event-planning topics.
            """;

    private final CoordinatorQuestionRepository coordinatorQuestionRepository;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final AnthropicClient anthropicClient;
    private final int dailyQuestionLimit;

    public CoordinatorService(
            CoordinatorQuestionRepository coordinatorQuestionRepository,
            EventRepository eventRepository,
            UserRepository userRepository,
            AnthropicClient anthropicClient,
            @Value("${app.coordinator-daily-question-limit}") int dailyQuestionLimit) {
        this.coordinatorQuestionRepository = coordinatorQuestionRepository;
        this.eventRepository = eventRepository;
        this.userRepository = userRepository;
        this.anthropicClient = anthropicClient;
        this.dailyQuestionLimit = dailyQuestionLimit;
    }

    @Transactional
    public CoordinatorQuestionResponse askQuestion(String plannerEmail, Long eventId, String question) {
        Event event = requireOwnedEvent(plannerEmail, eventId);

        long askedToday = coordinatorQuestionRepository.countByPlannerIdAndCreatedAtAfter(
                event.getPlanner().getId(), startOfTodayManila());
        if (askedToday >= dailyQuestionLimit) {
            throw new CoordinatorQuotaExceededException(
                    "You've reached today's limit of " + dailyQuestionLimit
                            + " coordinator questions. Try again tomorrow.");
        }

        String answer = anthropicClient.ask(SYSTEM_PROMPT, buildUserPrompt(event, question));

        CoordinatorQuestion saved = coordinatorQuestionRepository.save(CoordinatorQuestion.builder()
                .event(event)
                .planner(event.getPlanner())
                .question(question)
                .answer(answer)
                .build());

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public CoordinatorHistoryResponse listHistory(String plannerEmail, Long eventId) {
        Event event = requireOwnedEvent(plannerEmail, eventId);

        var questions = coordinatorQuestionRepository.findByEventIdOrderByCreatedAtAsc(eventId).stream()
                .map(this::toResponse)
                .toList();

        long askedToday = coordinatorQuestionRepository.countByPlannerIdAndCreatedAtAfter(
                event.getPlanner().getId(), startOfTodayManila());
        int remaining = (int) Math.max(0, dailyQuestionLimit - askedToday);

        return CoordinatorHistoryResponse.builder()
                .questions(questions)
                .questionsRemainingToday(remaining)
                .dailyLimit(dailyQuestionLimit)
                .build();
    }

    private String buildUserPrompt(Event event, String question) {
        return """
                Event details:
                - Type: %s
                - Date: %s
                - Location: %s
                - Planner's description: %s

                Planner's question: %s
                """.formatted(
                event.getEventType(),
                event.getEventDate() != null ? event.getEventDate() : "not set",
                event.getLocation() != null ? event.getLocation() : "not set",
                event.getDescription() != null ? event.getDescription() : "none provided",
                question);
    }

    private Instant startOfTodayManila() {
        return LocalDate.now(MANILA).atStartOfDay(MANILA).toInstant();
    }

    private Event requireOwnedEvent(String plannerEmail, Long eventId) {
        User planner = requireUser(plannerEmail);
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalStateException("Event not found: " + eventId));
        if (!event.getPlanner().getId().equals(planner.getId())) {
            throw new IllegalStateException("Event does not belong to the authenticated user");
        }
        return event;
    }

    private User requireUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + email));
    }

    private CoordinatorQuestionResponse toResponse(CoordinatorQuestion q) {
        return CoordinatorQuestionResponse.builder()
                .id(q.getId())
                .question(q.getQuestion())
                .answer(q.getAnswer())
                .createdAt(q.getCreatedAt())
                .build();
    }
}
