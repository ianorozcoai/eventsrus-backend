package com.backend.eventsrus.dto;

import com.backend.eventsrus.enums.TicketStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UpdateTicketStatusRequest {

    @NotNull
    private TicketStatus status;
}
