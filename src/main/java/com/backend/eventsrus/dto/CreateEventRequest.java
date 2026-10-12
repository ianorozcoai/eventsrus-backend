package com.backend.eventsrus.dto;

import com.backend.eventsrus.enums.EventType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CreateEventRequest {

    @NotBlank
    private String name;

    @NotNull
    private EventType eventType;

    @NotNull
    private LocalDate eventDate;

    /** Required - without it, suggestions are generic vendor types rather than matched vendors. */
    @NotBlank
    private String location;

    private String description;

    /** Optional - PHP, no decimals required but allowed. */
    @DecimalMin(value = "0", inclusive = true)
    private BigDecimal budget;

    /** Optional. */
    @Min(1)
    private Integer guestCount;
}
