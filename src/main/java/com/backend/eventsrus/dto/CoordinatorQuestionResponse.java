package com.backend.eventsrus.dto;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class CoordinatorQuestionResponse {

    private Long id;
    private String question;
    private String answer;
    private Instant createdAt;
}
