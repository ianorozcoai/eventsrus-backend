package com.backend.eventsrus.dto;

import com.backend.eventsrus.enums.EventType;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CreateEventRequest {

    @NotNull
    private EventType eventType;

    private LocalDate eventDate;

    /** Optional — when absent, suggestions are generic vendor types rather than matched vendors. */
    private String location;

    private String description;
}
