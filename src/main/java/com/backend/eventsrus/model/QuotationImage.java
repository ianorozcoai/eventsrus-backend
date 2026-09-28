package com.backend.eventsrus.model;

import com.backend.eventsrus.common.BaseEntity;
import com.backend.eventsrus.enums.QuotationImageSource;
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
 * One image attached to a quotation - either a planner's reference photo
 * on the initial request (source=REQUEST) or a vendor's supplementary
 * photo alongside their required PDF response (source=RESPONSE). Private-
 * bucket S3 key, same as Quotation#pdfKey - retrieved via presigned URL,
 * never a direct URL. Deliberately NOT snapshotted per quotation version
 * the way pdfKey is via QuotationStatusEvent - these belong to the
 * quotation as a whole rather than one specific historical revision.
 */
@Entity
@Table(name = "quotation_images")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class QuotationImage extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quotation_id", nullable = false)
    private Quotation quotation;

    @Column(name = "image_key", nullable = false)
    private String imageKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false)
    private QuotationImageSource source;
}
