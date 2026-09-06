package com.backend.eventsrus.dto;

import com.backend.eventsrus.enums.ChecklistSource;
import com.backend.eventsrus.enums.ChecklistStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class ChecklistItemResponse {

    private Long id;
    private String label;
    private ChecklistStatus status;
    private ChecklistSource source;
}
