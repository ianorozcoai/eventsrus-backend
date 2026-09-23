package com.backend.eventsrus.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class SystemSettingResponse {

    private String key;
    private String label;
    private String description;
    private String value;
    private boolean numeric;
}
