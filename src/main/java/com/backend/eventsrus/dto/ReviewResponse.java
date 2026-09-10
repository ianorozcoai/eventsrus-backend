package com.backend.eventsrus.dto;

import java.time.Instant;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class ReviewResponse {

    private Long id;
    private int rating;
    private String comment;
    private Instant createdAt;
    private Instant updatedAt;
    private boolean hidden;

    // Context shown alongside the review on the storefront / admin view.
    private String reviewerName;
    private String eventName;
    private LocalDate eventDate;
}
