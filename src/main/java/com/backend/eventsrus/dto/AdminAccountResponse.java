package com.backend.eventsrus.dto;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class AdminAccountResponse {

    private String username;
    private Instant createdAt;
}
