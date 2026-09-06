package com.backend.eventsrus.dto;

import com.backend.eventsrus.enums.TicketCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CreateTicketRequest {

    @NotBlank
    private String subject;

    @NotNull
    private TicketCategory category;

    /** The opening complaint - becomes the ticket's first SupportTicketMessage. */
    @NotBlank
    private String message;

    private Long relatedEventId;

    private Long relatedBookingId;

    private Long relatedQuotationId;
}
