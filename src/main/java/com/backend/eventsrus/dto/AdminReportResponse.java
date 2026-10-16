package com.backend.eventsrus.dto;

import com.backend.eventsrus.enums.ReportContentType;
import com.backend.eventsrus.enums.ReportReason;
import com.backend.eventsrus.enums.ReportStatus;
import java.time.Instant;

/** The admin's view of a report; reporter identity is shown only here. */
public record AdminReportResponse(
        Long id,
        ReportContentType contentType,
        Long contentId,
        ReportReason reason,
        String details,
        ReportStatus status,
        Long reporterUserId,
        String reporterName,
        Long reportedUserId,
        String reportedName,
        String contentPreview,
        Instant createdAt,
        Instant resolvedAt,
        String resolvedNote) {}
