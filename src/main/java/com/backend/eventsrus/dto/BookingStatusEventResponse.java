package com.backend.eventsrus.dto;

import com.backend.eventsrus.enums.BookingStatus;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class BookingStatusEventResponse {

    private Long id;
    private BookingStatus fromStatus;
    private BookingStatus toStatus;
    private Long changedByUserId;
    private String changedByName;
    private String reason;
    private Instant createdAt;
}
