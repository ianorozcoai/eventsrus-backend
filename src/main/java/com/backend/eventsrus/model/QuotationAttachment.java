package com.backend.eventsrus.model;

import com.backend.eventsrus.common.BaseEntity;
import com.backend.eventsrus.enums.QuotationAttachmentFileType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * A free-standing image or PDF either side of a quotation can send at any
 * time, independent of the request/response/revision negotiation flow -
 * unlike QuotationImage (reference/response images tied to a specific side
 * of that flow), this deliberately never touches Quotation#status or
 * QuotationStatusEvent at all. See QuotationService#addAttachment/#history.
 */
@Entity
@Table(name = "quotation_attachments")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class QuotationAttachment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quotation_id", nullable = false)
    private Quotation quotation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by_user_id", nullable = false)
    private User uploadedBy;

    @Column(name = "file_key", nullable = false)
    private String fileKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "file_type", nullable = false)
    private QuotationAttachmentFileType fileType;

    @Column(columnDefinition = "TEXT")
    private String message;
}
