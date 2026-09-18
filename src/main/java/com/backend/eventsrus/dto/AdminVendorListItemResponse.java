package com.backend.eventsrus.dto;

import com.backend.eventsrus.enums.BusinessType;
import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * One row of the admin verification review queue AND the Vendors directory
 * page - see AdminVendorController#list. Both admin views are every vendor,
 * just displayed differently, so this one response shape serves both rather
 * than duplicating the query.
 */
@Getter
@Builder
@AllArgsConstructor
public class AdminVendorListItemResponse {

    private Long vendorUserId;
    private String businessName;
    private String ownerName;
    private String contactEmail;
    private String phoneNumber;
    private BusinessType businessType;
    private String slug;
    private String city;
    private String state;
    private List<String> operatingAreas;
    private boolean hasIdCard;
    private boolean hasSelfie;
    private int legalDocumentCount;
    private boolean verified;
    private Instant verifiedAt;
    private String verifiedByAdmin;
    private boolean topVendor;
    private Instant createdAt;
    private long referralCount;
}
