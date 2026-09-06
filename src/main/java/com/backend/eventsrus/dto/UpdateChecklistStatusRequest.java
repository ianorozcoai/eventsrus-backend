package com.backend.eventsrus.dto;

import com.backend.eventsrus.enums.ChecklistStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UpdateChecklistStatusRequest {

    @NotNull
    private ChecklistStatus status;
}
