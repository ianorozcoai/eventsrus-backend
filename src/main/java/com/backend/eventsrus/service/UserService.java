package com.backend.eventsrus.service;

import com.backend.eventsrus.dto.UpdateProfileRequest;
import com.backend.eventsrus.dto.UserProfileResponse;
import com.backend.eventsrus.dto.VendorLegalDocumentResponse;
import com.backend.eventsrus.dto.VendorOnboardingRequest;
import com.backend.eventsrus.dto.VendorSettingsRequest;
import com.backend.eventsrus.dto.VendorSettingsResponse;
import com.backend.eventsrus.common.TermsConstants;
import com.backend.eventsrus.enums.BillingSource;
import com.backend.eventsrus.enums.LegalDocumentType;
import com.backend.eventsrus.enums.PlanTier;
import com.backend.eventsrus.enums.Role;
import com.backend.eventsrus.enums.SubscriptionStatus;
import com.backend.eventsrus.exception.DuplicateUserException;
import com.backend.eventsrus.exception.InvalidFileTypeException;
import com.backend.eventsrus.model.User;
import com.backend.eventsrus.model.VendorProfile;
import com.backend.eventsrus.model.VendorSubscription;
import com.backend.eventsrus.repository.UserRepository;
import com.backend.eventsrus.repository.VendorProfileRepository;
import com.backend.eventsrus.repository.VendorSubscriptionRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class UserService {

    // Configurable rather than hardcoded - the business wants the ability to
    // change the free-trial length (e.g. 6 months now, 3 months later)
    // without a code change. See application.properties.
    @Value("${app.vendor-trial-months:6}")
    private int vendorTrialMonths;
    private static final Duration PRESIGNED_URL_TTL = Duration.ofMinutes(10);

    private final UserRepository userRepository;
    private final VendorProfileRepository vendorProfileRepository;
    private final VendorSubscriptionRepository vendorSubscriptionRepository;
    private final S3UploadService s3UploadService;
    private final VendorLegalDocumentService vendorLegalDocumentService;
    private final VendorBillingHistoryService vendorBillingHistoryService;

    public User findOrCreateFromGoogle(GoogleUserInfo googleUser) {
        return userRepository.findByGoogleId(googleUser.googleId())
                .orElseGet(() -> createPlannerFromGoogle(googleUser));
    }

    private User createPlannerFromGoogle(GoogleUserInfo googleUser) {
        User user = User.builder()
                .googleId(googleUser.googleId())
                .email(googleUser.email())
                .firstName(googleUser.givenName())
                .lastName(googleUser.familyName())
                .role(Role.PLANNER)
                .build();

        try {
            return userRepository.save(user);
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateUserException("Account already exists for this Google user");
        }
    }

    @Transactional
    public User becomeVendor(
            String email,
            VendorOnboardingRequest request,
            VendorUploadFiles files) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + email));

        VendorProfile profile = vendorProfileRepository.findByUserId(user.getId())
                .orElseGet(() -> VendorProfile.builder().user(user).build());

        if (files.logo() != null && !files.logo().isEmpty()) {
            profile.setLogoImageUrl(s3UploadService
                    .upload(files.logo(), keyPrefix(user.getId(), "logo"), S3UploadService.Visibility.PUBLIC)
                    .url());
        }
        if (files.idCard() != null && !files.idCard().isEmpty()) {
            profile.setIdCardKey(s3UploadService
                    .upload(files.idCard(), keyPrefix(user.getId(), "id-card"), S3UploadService.Visibility.PRIVATE)
                    .key());
        }
        if (files.selfie() != null && !files.selfie().isEmpty()) {
            profile.setSelfieKey(s3UploadService
                    .upload(files.selfie(), keyPrefix(user.getId(), "selfie"), S3UploadService.Visibility.PRIVATE)
                    .key());
        }
        profile.setOwnerName(request.getOwnerName());
        profile.setDescription(request.getDescription());
        profile.setContactEmail(request.getContactEmail());
        profile.setPhoneNumber(request.getPhoneNumber());
        profile.setAddressLine1(request.getAddressLine1());
        profile.setAddressLine2(request.getAddressLine2());
        profile.setCity(request.getCity());
        profile.setState(request.getState());
        profile.setPostalCode(request.getPostalCode());
        profile.setCountry(request.getCountry());
        profile.setBusinessName(request.getBusinessName());
        profile.setBusinessType(request.getBusinessType());
        if (request.getOperatingAreas() != null) {
            profile.setOperatingAreas(request.getOperatingAreas());
        }
        if (profile.getSlug() == null) {
            profile.setSlug(generateUniqueSlug(request.getBusinessName()));
        }
        vendorProfileRepository.save(profile);

        // Any number of business-registration documents can be attached at
        // onboarding now (DTI, SEC, Mayor's Permit, Barangay Clearance,
        // BIR, ...) - legalDocumentTypes/legalDocumentLabels are parallel
        // lists correlated by index with legalDocumentFiles; a vendor can
        // still add more later via the dedicated legal-documents endpoint
        // (VendorLegalDocumentController).
        if (files.legalDocumentFiles() != null) {
            for (int i = 0; i < files.legalDocumentFiles().size(); i++) {
                MultipartFile file = files.legalDocumentFiles().get(i);
                if (file == null || file.isEmpty()) {
                    continue;
                }
                vendorLegalDocumentService.createForProfile(
                        profile,
                        parseDocumentType(files.legalDocumentTypes(), i),
                        parseLabel(files.legalDocumentLabels(), i),
                        file);
            }
        }

        user.setRole(Role.VENDOR);
        user.setTermsAcceptedAt(Instant.now());
        user.setTermsVersion(TermsConstants.CURRENT_VERSION);
        userRepository.save(user);

        if (!vendorSubscriptionRepository.existsByUserId(user.getId())) {
            Instant now = Instant.now();
            Instant trialEnd = now.plus(vendorTrialMonths * 30L, ChronoUnit.DAYS);
            VendorSubscription subscription = vendorSubscriptionRepository.save(
                    VendorSubscription.builder()
                            .user(user)
                            .currentPeriodStart(now)
                            .currentPeriodEnd(trialEnd)
                            .plan(PlanTier.PRO)
                            .billingSource(BillingSource.FREE_GRANT)
                            .status(SubscriptionStatus.ACTIVE)
                            .build());
            vendorBillingHistoryService.recordFreeGrant(subscription, now, trialEnd);
        }

        return user;
    }

    @Transactional(readOnly = true)
    public VendorVerificationDocuments getVerificationDocuments(Long userId) {
        VendorProfile profile = vendorProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalStateException("Vendor profile not found for user: " + userId));

        return new VendorVerificationDocuments(
                s3UploadService.presignedUrl(profile.getIdCardKey(), PRESIGNED_URL_TTL),
                s3UploadService.presignedUrl(profile.getSelfieKey(), PRESIGNED_URL_TTL),
                vendorLegalDocumentService.listForVendor(profile.getUser().getEmail()));
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(String email) {
        return toProfileResponse(requireUser(email));
    }

    // Returns the updated User entity (not a response DTO) rather than
    // UserProfileResponse - callers that let email change need to re-issue
    // a token for it (AuthService.issueTokenFor), same as
    // UserController#becomeVendor already does after a save that affects
    // the JWT subject (which is the user's email - see JwtService).
    @Transactional
    public User updateProfile(String currentEmail, UpdateProfileRequest request) {
        User user = requireUser(currentEmail);
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setEmail(request.getEmail());
        user.setMobileNumber(request.getMobileNumber());
        user.setAddressLine1(request.getAddressLine1());
        user.setAddressLine2(request.getAddressLine2());
        user.setCity(request.getCity());
        user.setState(request.getState());
        user.setPostalCode(request.getPostalCode());
        return userRepository.save(user);
    }

    private User requireUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + email));
    }

    private UserProfileResponse toProfileResponse(User user) {
        return UserProfileResponse.builder()
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .mobileNumber(user.getMobileNumber())
                .addressLine1(user.getAddressLine1())
                .addressLine2(user.getAddressLine2())
                .city(user.getCity())
                .state(user.getState())
                .postalCode(user.getPostalCode())
                .build();
    }

    @Transactional(readOnly = true)
    public VendorSettingsResponse getSettings(String vendorEmail) {
        VendorProfile profile = requireProfile(vendorEmail);
        return toSettingsResponse(profile);
    }

    @Transactional
    public VendorSettingsResponse updateSettings(
            String vendorEmail, VendorSettingsRequest request, VendorSettingsFiles files) {
        VendorProfile profile = requireProfile(vendorEmail);

        profile.setBusinessName(request.getBusinessName());
        profile.setOwnerName(request.getOwnerName());
        profile.setBusinessType(request.getBusinessType());
        profile.setContactEmail(request.getContactEmail());
        profile.setPhoneNumber(request.getPhoneNumber());
        profile.setAddressLine1(request.getAddressLine1());
        profile.setAddressLine2(request.getAddressLine2());
        profile.setCity(request.getCity());
        profile.setState(request.getState());
        profile.setPostalCode(request.getPostalCode());
        profile.setCountry(request.getCountry());

        profile.setPrimaryCategory(request.getPrimaryCategory());
        profile.setMaxGuestCapacity(request.getMaxGuestCapacity());
        profile.setBasePrice(request.getBasePrice());
        profile.setLeadTimeDays(request.getLeadTimeDays());
        profile.setStorefrontOverview(request.getStorefrontOverview());
        profile.setPaymentInstructions(request.getPaymentInstructions());
        if (request.getOperatingAreas() != null) {
            profile.setOperatingAreas(request.getOperatingAreas());
        }
        if (request.getCateredEventTypes() != null) {
            profile.setCateredEventTypes(request.getCateredEventTypes());
        }

        Long vendorUserId = profile.getUser().getId();
        if (files.logo() != null && !files.logo().isEmpty()) {
            profile.setLogoImageUrl(s3UploadService
                    .upload(files.logo(), keyPrefix(vendorUserId, "logo"), S3UploadService.Visibility.PUBLIC)
                    .url());
        }
        if (files.idCard() != null && !files.idCard().isEmpty()) {
            profile.setIdCardKey(s3UploadService
                    .upload(files.idCard(), keyPrefix(vendorUserId, "id-card"), S3UploadService.Visibility.PRIVATE)
                    .key());
        }
        if (files.selfie() != null && !files.selfie().isEmpty()) {
            profile.setSelfieKey(s3UploadService
                    .upload(files.selfie(), keyPrefix(vendorUserId, "selfie"), S3UploadService.Visibility.PRIVATE)
                    .key());
        }
        if (files.cancellationPolicyFile() != null && !files.cancellationPolicyFile().isEmpty()) {
            requirePdf(files.cancellationPolicyFile());
            profile.setCancellationPolicyUrl(s3UploadService
                    .upload(
                            files.cancellationPolicyFile(),
                            keyPrefix(vendorUserId, "cancellation-policy"),
                            S3UploadService.Visibility.PUBLIC)
                    .url());
        }
        if (files.refundTermsFile() != null && !files.refundTermsFile().isEmpty()) {
            requirePdf(files.refundTermsFile());
            profile.setRefundTermsUrl(s3UploadService
                    .upload(
                            files.refundTermsFile(),
                            keyPrefix(vendorUserId, "refund-terms"),
                            S3UploadService.Visibility.PUBLIC)
                    .url());
        }

        vendorProfileRepository.save(profile);
        return toSettingsResponse(profile);
    }

    private void requirePdf(MultipartFile file) {
        if (!"application/pdf".equals(file.getContentType())) {
            throw new InvalidFileTypeException("Only PDF files are accepted for this upload");
        }
    }

    private VendorProfile requireProfile(String vendorEmail) {
        User user = userRepository.findByEmail(vendorEmail)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + vendorEmail));
        return vendorProfileRepository.findByUserId(user.getId())
                .orElseThrow(() -> new IllegalStateException("Vendor profile not found for user: " + user.getId()));
    }

    private VendorSettingsResponse toSettingsResponse(VendorProfile profile) {
        return VendorSettingsResponse.builder()
                .businessName(profile.getBusinessName())
                .ownerName(profile.getOwnerName())
                .businessType(profile.getBusinessType())
                .contactEmail(profile.getContactEmail())
                .phoneNumber(profile.getPhoneNumber())
                .addressLine1(profile.getAddressLine1())
                .addressLine2(profile.getAddressLine2())
                .city(profile.getCity())
                .state(profile.getState())
                .postalCode(profile.getPostalCode())
                .country(profile.getCountry())
                .logoImageUrl(profile.getLogoImageUrl())
                .idCardUrl(s3UploadService.presignedUrl(profile.getIdCardKey(), PRESIGNED_URL_TTL))
                .selfieUrl(s3UploadService.presignedUrl(profile.getSelfieKey(), PRESIGNED_URL_TTL))
                .primaryCategory(profile.getPrimaryCategory())
                .maxGuestCapacity(profile.getMaxGuestCapacity())
                .basePrice(profile.getBasePrice())
                .leadTimeDays(profile.getLeadTimeDays())
                .storefrontOverview(profile.getStorefrontOverview())
                .operatingAreas(profile.getOperatingAreas())
                .cateredEventTypes(profile.getCateredEventTypes())
                .cancellationPolicyUrl(profile.getCancellationPolicyUrl())
                .refundTermsUrl(profile.getRefundTermsUrl())
                .paymentInstructions(profile.getPaymentInstructions())
                .build();
    }

    private String keyPrefix(Long userId, String label) {
        return "vendors/" + userId + "/" + label;
    }

    // Defensive against a missing/short/malformed list at this index -
    // onboarding's document rows are client-submitted parallel lists, not a
    // structured payload, so a client bug or truncated request shouldn't
    // ever throw here, just fall back to OTHER.
    private LegalDocumentType parseDocumentType(List<String> types, int index) {
        if (types == null || index >= types.size()) {
            return LegalDocumentType.OTHER;
        }
        try {
            return LegalDocumentType.valueOf(types.get(index));
        } catch (IllegalArgumentException | NullPointerException e) {
            return LegalDocumentType.OTHER;
        }
    }

    private String parseLabel(List<String> labels, int index) {
        if (labels == null || index >= labels.size()) {
            return null;
        }
        String label = labels.get(index);
        return (label == null || label.isBlank()) ? null : label;
    }

    private String generateUniqueSlug(String businessName) {
        String base = businessName.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        if (base.isBlank()) {
            base = "vendor";
        }

        String candidate = base;
        int suffix = 1;
        while (vendorProfileRepository.existsBySlug(candidate)) {
            suffix++;
            candidate = base + "-" + suffix;
        }
        return candidate;
    }

    // legalDocumentFiles/legalDocumentTypes/legalDocumentLabels are parallel
    // lists correlated by index - replaces the old single businessPermit
    // field, since a vendor can attach any number of business-registration
    // documents at onboarding now.
    public record VendorUploadFiles(
            MultipartFile logo,
            MultipartFile idCard,
            MultipartFile selfie,
            List<MultipartFile> legalDocumentFiles,
            List<String> legalDocumentTypes,
            List<String> legalDocumentLabels) {
    }

    public record VendorVerificationDocuments(
            String idCardUrl,
            String selfieUrl,
            List<VendorLegalDocumentResponse> legalDocuments) {
    }

    // businessPermit is gone - legal documents (any number of them) are now
    // managed through their own dedicated endpoint (VendorLegalDocumentController),
    // not this monolithic settings PUT.
    public record VendorSettingsFiles(
            MultipartFile logo,
            MultipartFile idCard,
            MultipartFile selfie,
            MultipartFile cancellationPolicyFile,
            MultipartFile refundTermsFile) {
    }
}
