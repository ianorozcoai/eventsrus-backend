package com.backend.eventsrus.controller;

import com.backend.eventsrus.dto.CoordinatorAskRequest;
import com.backend.eventsrus.dto.CoordinatorHistoryResponse;
import com.backend.eventsrus.dto.CoordinatorQuestionResponse;
import com.backend.eventsrus.service.CoordinatorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/events/{eventId}/coordinator")
@RequiredArgsConstructor
public class CoordinatorController {

    private final CoordinatorService coordinatorService;

    @GetMapping("/questions")
    public CoordinatorHistoryResponse listQuestions(@PathVariable Long eventId, Authentication authentication) {
        return coordinatorService.listHistory(authentication.getName(), eventId);
    }

    @PostMapping("/ask")
    public CoordinatorQuestionResponse ask(
            @PathVariable Long eventId,
            @Valid @RequestBody CoordinatorAskRequest request,
            Authentication authentication) {
        return coordinatorService.askQuestion(authentication.getName(), eventId, request.getQuestion());
    }
}
