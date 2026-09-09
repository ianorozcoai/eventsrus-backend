package com.backend.eventsrus.dto;

import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Both fields optional - a planner can clear location/date back to blank just as easily as setting them. */
@Getter
@Setter
@NoArgsConstructor
public class UpdateEventDetailsRequest {

    private LocalDate eventDate;
    private String location;
}
