package com.backend.eventsrus.dto;

import com.backend.eventsrus.enums.BookingStatus;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class BookingResponse {

    private Long id;
    private Long eventId;
    private String eventName;
    private Long vendorUserId;
    private String vendorBusinessName;
    private String vendorSlug;
    private String cancellationPolicyUrl;
    private String refundTermsUrl;
    private Long plannerUserId;
    private String plannerName;
    private Long quotationId;
    private BigDecimal price;
    private Instant eventDatetime;
    private String agreementDetails;
    private BookingStatus status;
    private Instant proposedAt;
    private Instant respondedAt;
    private String paymentScreenshotUrl;
    private Instant paymentScreenshotUploadedAt;
    private Instant paymentAcknowledgedAt;
    private String paymentRejectionReason;
    private String invoiceUrl;
    private Instant invoiceUploadedAt;
    private Instant cancelledAt;
    private String cancellationReason;
    private Long cancelledByUserId;

    // True while a BookingAmendment on this booking is still awaiting a
    // response from either side - see BookingAmendmentService/AmendmentStatus.
    private boolean hasPendingAmendment;

    // Review eligibility for the planner viewing their own bookings - see
    // ReviewService. canReview is true only for a successful booking whose
    // event is 3+ days past and hasn't been reviewed yet; reviewId is set
    // once they have.
    private boolean canReview;
    private Long reviewId;
    private Integer reviewRating;
    private String reviewComment;
}
