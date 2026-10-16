package com.backend.eventsrus.dto;

import com.backend.eventsrus.enums.ReportContentType;
import com.backend.eventsrus.enums.ReportReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ReportRequest {

    @NotNull
    private ReportContentType contentType;

    /** A message id, review id, or (STOREFRONT / USER) the other person's user id. */
    @NotNull
    private Long contentId;

    @NotNull
    private ReportReason reason;

    @Size(max = 1000)
    private String details;
}
