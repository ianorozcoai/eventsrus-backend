package com.backend.eventsrus.dto;

import com.backend.eventsrus.enums.ReportStatus;

/** What the reporter gets back: just that it was received. The reported person is never told who reported. */
public record ReportResponse(Long id, ReportStatus status) {}
