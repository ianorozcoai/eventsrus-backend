package com.backend.eventsrus.repository;

import com.backend.eventsrus.enums.ReportContentType;
import com.backend.eventsrus.enums.ReportStatus;
import com.backend.eventsrus.model.ContentReport;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContentReportRepository extends JpaRepository<ContentReport, Long> {

    Optional<ContentReport> findByReporterIdAndContentTypeAndContentId(
            Long reporterId, ReportContentType contentType, Long contentId);

    List<ContentReport> findByStatusOrderByCreatedAtDesc(ReportStatus status);

    List<ContentReport> findAllByOrderByCreatedAtDesc();
}
