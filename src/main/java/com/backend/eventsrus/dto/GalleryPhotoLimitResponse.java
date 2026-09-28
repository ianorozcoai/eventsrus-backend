package com.backend.eventsrus.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class GalleryPhotoLimitResponse {

    private int limit;
    private int used;
}
