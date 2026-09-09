package com.backend.eventsrus.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class CoordinatorHistoryResponse {

    private List<CoordinatorQuestionResponse> questions;
    private int questionsRemainingToday;
    private int dailyLimit;
}
