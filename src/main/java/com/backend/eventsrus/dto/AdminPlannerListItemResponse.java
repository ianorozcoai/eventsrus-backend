package com.backend.eventsrus.dto;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/** One planner row (or the read-only profile view) in the admin module's Planner directory. */
@Getter
@Builder
@AllArgsConstructor
public class AdminPlannerListItemResponse {

    private Long id;
    private String firstName;
    private String lastName;
    private String email;
    private String mobileNumber;
    private String city;
    private String state;
    private Instant joinedAt;
    private int eventsCount;
}
