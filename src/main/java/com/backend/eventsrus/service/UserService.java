package com.backend.eventsrus.service;

import com.backend.eventsrus.dto.UpdateProfileRequest;
import com.backend.eventsrus.dto.UserProfileResponse;
import com.backend.eventsrus.dto.VendorLegalDocumentResponse;
import com.backend.eventsrus.dto.VendorOnboardingRequest;
import com.backend.eventsrus.dto.VendorSettingsRequest;
import com.backend.eventsrus.dto.VendorSettingsResponse;
import com.backend.eventsrus.common.TermsConstants;
import com.backend.eventsrus.enums.BillingSource;
import com.backend.eventsrus.enums.BookingStatus;
import com.backend.eventsrus.enums.BusinessType;
import com.backend.eventsrus.enums.LegalDocumentType;
import com.backend.eventsrus.enums.PlanTier;
import com.backend.eventsrus.enums.Role;
import com.backend.eventsrus.enums.SignupIntent;
import com.backend.eventsrus.enums.SubscriptionStatus;
import com.backend.eventsrus.enums.SystemSettingKey;
import com.backend.eventsrus.exception.AccountIdentityConflictException;
import com.backend.eventsrus.exception.DuplicateUserException;
import com.backend.eventsrus.exception.InvalidFileTypeException;
import com.backend.eventsrus.exception.RecaptchaVerificationException;
import com.backend.eventsrus.model.User;
import com.backend.eventsrus.model.VendorProfile;
import com.backend.eventsrus.model.VendorSubscription;
import com.backend.eventsrus.repository.BookingRepository;
import com.backend.eventsrus.repository.EventRepository;
import com.backend.eventsrus.repository.UserRepository;
import com.backend.eventsrus.repository.VendorProfileRepository;
import com.backend.eventsrus.repository.VendorSubscriptionRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class UserService {

    // Was 10 minutes - too short for a "View current file" link sitting on
    // a settings page a vendor might leave open for a while before
    // clicking it; the link would 403 as "ExpiredRequest" by then. An hour
    // matches this app's own JWT expiration (jwt.expiration-ms), a
    // reasonable balance for a private-document link.
    private static final Duration PRESIGNED_URL_TTL = Duration.ofHours(1);

    private final UserRepository userRepository;
    private final VendorProfileRepository vendorProfileRepository;
    private final VendorSubscriptionRepository vendorSubscriptionRepository;
    private final S3UploadService s3UploadService;
    private final VendorLegalDocumentService vendorLegalDocumentService;
    private final VendorBillingHistoryService vendorBillingHistoryService;
    private final RecaptchaVerificationService recaptchaVerificationService;
    private final VendorReferralService vendorReferralService;
    private final PromoCodeService promoCodeService;
    private final BookingRepository bookingRepository;
    private final EventRepository eventRepository;
    private final SystemSettingService systemSettingService;
    private final AdminNotificationEmailService adminNotificationEmailService;
    private final VendorPlanService vendorPlanService;

    /**
     * intent is "planner" or "vendor" - which login door was used (see
     * GoogleAuthRequest#getIntent) - blank/unrecognized for any client that
     * doesn't declare one yet (the Flutter app). A brand-new email is
     * created with signupIntent locked to whichever door it came through
     * (defaulting to PLANNER if undeclared, matching prior behavior). A
     * returning email with a declared intent that conflicts with its
     * locked signupIntent is rejected outright - each email is exactly one
     * identity, permanently; the fix is a different email, not retrying.
     * An undeclared intent on a returning user skips the check entirely.
     */
    public User findOrCreateFromGoogle(GoogleUserInfo googleUser, String intent) {
        SignupIntent declaredIntent = parseIntent(intent);
        User user = userRepository.findByGoogleId(googleUser.googleId())
                .map(existing -> requireNoIdentityConflict(existing, declaredIntent))
                .orElseGet(() -> createFromGoogle(googleUser, declaredIntent));
        user.setLastLoginAt(Instant.now());
        return userRepository.save(user);
    }

    private SignupIntent parseIntent(String intent) {
        if ("vendor".equalsIgnoreCase(intent)) {
            return SignupIntent.VENDOR;
        }
        if ("planner".equalsIgnoreCase(intent)) {
            return SignupIntent.PLANNER;
        }
        return null;
    }

    private User requireNoIdentityConflict(User existing, SignupIntent declaredIntent) {
        if (declaredIntent != null && declaredIntent != existing.getSignupIntent()) {
            String message = declaredIntent == SignupIntent.VENDOR
                    ? "This email is already registered as a planner account. Please use a different email to sign up as a vendor."
                    : "This email is already registered as a vendor account. Please use a different email to plan events.";
            throw new AccountIdentityConflictException(message);
        }
        return existing;
    }

    private User createFromGoogle(GoogleUserInfo googleUser, SignupIntent declaredIntent) {
        User user = User.builder()
                .googleId(googleUser.googleId())
                .email(googleUser.email())
                .firstName(googleUser.givenName())
                .lastName(googleUser.familyName())
                .role(Role.PLANNER)
                .signupIntent(declaredIntent != null ? declaredIntent : SignupIntent.PLANNER)
                .build();

        User saved;
        try {
            saved = userRepository.save(user);
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateUserException("Account already exists for this Google user");
        }

        // Vendor-intent signups get their own alert once onboarding actually
        // finishes (see becomeVendor below) - a "vendor" door click alone is
        // just an incomplete sign-up, already tracked separately on the
        // admin vendors page.
        if (saved.getSignupIntent() == SignupIntent.PLANNER) {
            adminNotificationEmailService.notifyNewPlanner(saved);
        }
        return saved;
    }

    @Transactional
    public User becomeVendor(
            String email,
            VendorOnboardingRequest request,
            VendorUploadFiles files) {
        // Verify-if-present, not yet required: eventsrus-web's onboarding form
        // sends a real reCAPTCHA v3 token and gets checked for real; Flutter
        // doesn't send one yet (no native v3 SDK - would need a WebView token
        // flow), so an absent token is allowed through rather than breaking
        // the one real mobile signup path that already works. Tighten this to
        // @NotBlank + always-required once Flutter can supply one too.
        if (request.getRecaptchaToken() != null && !request.getRecaptchaToken().isBlank()
                && !recaptchaVerificationService.verify(request.getRecaptchaToken())) {
            throw new RecaptchaVerificationException("reCAPTCHA verification failed. Please try again.");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + email));
        // becomeVendor is safe to call again on an already-onboarded vendor
        // (e.g. re-submitting/updating profile fields) - only a genuine
        // first-time PLANNER->VENDOR transition should alert the admin.
        boolean wasAlreadyVendor = user.getRole() == Role.VENDOR;

        // Fail fast on a typo'd/unknown referral code before doing any
        // uploads below - a blank code is fine (referral is optional).
        vendorReferralService.requireValidReferralCodeIfPresent(request.getReferralCode());

        // Same fail-fast treatment for an invalid (non-blank) promo code -
        // see PromoCodeService. promoApplied decides below whether this
        // vendor gets the full free trial or has to pay/skip instead.
        boolean promoApplied = promoCodeService.isValidIfPresent(request.getPromoCode());

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
        profile.setFacebookPageUrl(request.getFacebookPageUrl());
        profile.setAddressLine1(request.getAddressLine1());
        profile.setAddressLine2(request.getAddressLine2());
        profile.setCity(request.getCity());
        profile.setState(request.getState());
        profile.setPostalCode(request.getPostalCode());
        profile.setCountry(request.getCountry());
        profile.setBusinessName(request.getBusinessName());
        profile.setBusinessTypes(request.getBusinessTypes());
        if (request.getOperatingAreas() != null) {
            profile.setOperatingAreas(request.getOperatingAreas());
        }
        if (request.getCateredEventTypes() != null) {
            profile.setCateredEventTypes(request.getCateredEventTypes());
        }
        if (profile.getSlug() == null) {
            profile.setSlug(generateUniqueSlug(request.getBusinessName()));
        }
        if (profile.getReferralCode() == null) {
            profile.setReferralCode(vendorReferralService.generateUniqueReferralCode());
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

        // A missing code, or a re-submit by an already-attributed vendor, is
        // a silent no-op - safe to call on every becomeVendor call. An
        // unknown code was already rejected above by
        // requireValidReferralCodeIfPresent, so that branch shouldn't
        // trigger here in practice - see VendorReferralService#attribute's
        // Javadoc.
        vendorReferralService.attribute(user, request.getReferralCode());

        // Only a valid promo code grants the full free trial automatically -
        // otherwise no subscription row is created here at all, and the
        // vendor is left in VendorPlanService's "never subscribed" state
        // until they pay via PayPal or GCash (see VendorSubscriptionService
        // #submitGcashPayment), which is what drives the required
        // plan-selection paywall on the web dashboard.
        if (promoApplied && !vendorSubscriptionRepository.existsByUserId(user.getId())) {
            Instant now = Instant.now();
            Instant trialEnd = now.plus(systemSettingService.getInt(SystemSettingKey.VENDOR_TRIAL_DAYS), ChronoUnit.DAYS);
            VendorSubscription subscription = vendorSubscriptionRepository.save(
                    VendorSubscription.builder()
                            .user(user)
                            .currentPeriodStart(now)
                            .currentPeriodEnd(trialEnd)
                            .plan(PlanTier.PRO)
                            .billingSource(BillingSource.FREE_GRANT)
                            .status(SubscriptionStatus.ACTIVE)
                            .build());
            vendorBillingHistoryService.recordFreeGrant(subscription, now, trialEnd, BillingSource.FREE_GRANT);
        }

        // Fired last, after every other step has succeeded - this method is
        // @Transactional, and an exception anywhere above rolls the whole
        // thing back, so a "new vendor" email should only ever go out once
        // onboarding has actually gone through.
        if (!wasAlreadyVendor) {
            adminNotificationEmailService.notifyNewVendor(user);
        }

        return user;
    }

    @Transactional(readOnly = true)
    public VendorVerificationDocuments getVerificationDocuments(Long userId) {
        VendorProfile profile = vendorProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalStateException("Vendor profile not found for user: " + userId));

        return new VendorVerificationDocuments(
                profile.getUser().getId(),
                profile.getBusinessName(),
                profile.getOwnerName(),
                s3UploadService.presignedUrl(profile.getIdCardKey(), PRESIGNED_URL_TTL),
                s3UploadService.presignedUrl(profile.getSelfieKey(), PRESIGNED_URL_TTL),
                vendorLegalDocumentService.listForVendor(profile.getUser().getEmail()),
                profile.isVerified(),
                profile.getVerifiedAt(),
                profile.getVerifiedByAdmin(),
                profile.isTopVendor());
    }

    /** The admin verification review queue - every vendor, regardless of review state. */
    @Transactional(readOnly = true)
    public List<AdminVendorListItem> listVendorsForAdmin() {
        return vendorProfileRepository.findAll().stream()
                .map(profile -> {
                    VendorPlanService.EffectivePlan plan = vendorPlanService.getEffectivePlan(profile.getUser().getId());
                    return new AdminVendorListItem(
                        profile.getUser().getId(),
                        profile.getBusinessName(),
                        profile.getOwnerName(),
                        profile.getContactEmail(),
                        profile.getPhoneNumber(),
                        List.copyOf(profile.getBusinessTypes()),
                        profile.getSlug(),
                        profile.getCity(),
                        profile.getState(),
                        // Copied out (not the lazy collection reference
                        // itself) - Jackson serializes the response after
                        // this @Transactional method has already returned
                        // and the Hibernate session is closed, so a lazy
                        // List reference here would throw
                        // LazyInitializationException at serialization time
                        // instead of loading cleanly right now.
                        List.copyOf(profile.getOperatingAreas()),
                        profile.getIdCardKey() != null,
                        profile.getSelfieKey() != null,
                        vendorLegalDocumentService.listForVendor(profile.getUser().getEmail()).size(),
                        profile.isVerified(),
                        profile.getVerifiedAt(),
                        profile.getVerifiedByAdmin(),
                        profile.isTopVendor(),
                        profile.getCreatedAt(),
                        vendorReferralService.countReferralsMade(profile.getUser().getId()),
                        profile.getUser().isFakeAccount(),
                        bookingRepository.countByVendorUserIdAndStatusNot(profile.getUser().getId(), BookingStatus.CANCELLED),
                        profile.getUser().getLastLoginAt(),
                        plan.billingSource() != null ? plan.billingSource().name() : null,
                        plan.expired(),
                        plan.inGracePeriod(),
                        plan.expiresAt());
                })
                .sorted(java.util.Comparator.comparing(AdminVendorListItem::createdAt).reversed())
                .toList();
    }

    /**
     * Declared vendor intent (signed up through "Become a Vendor") but
     * never finished the onboarding form - no VendorProfile exists yet, so
     * these accounts don't show up in listVendorsForAdmin at all. Newest
     * attempt first, same as the other admin directories.
     */
    @Transactional(readOnly = true)
    public List<AdminIncompleteVendorSignup> listIncompleteVendorSignupsForAdmin() {
        return userRepository.findBySignupIntentAndRoleNotOrderByCreatedAtDesc(SignupIntent.VENDOR, Role.VENDOR).stream()
                .map(user -> new AdminIncompleteVendorSignup(
                        user.getId(),
                        user.getFirstName(),
                        user.getLastName(),
                        user.getEmail(),
                        user.getMobileNumber(),
                        user.getCreatedAt()))
                .toList();
    }

    /**
     * The admin Planner directory - every real planner, newest first.
     * Excludes signupIntent=VENDOR: someone mid-vendor-onboarding is still
     * role=PLANNER until they finish the form, but they're not a planner in
     * any product sense - they already show up in the Vendors page's
     * "Incomplete Sign-ups" tab, and shouldn't be double-counted here too.
     */
    @Transactional(readOnly = true)
    public List<AdminPlannerListItem> listPlannersForAdmin() {
        return userRepository.findByRoleAndSignupIntentNot(Role.PLANNER, SignupIntent.VENDOR).stream()
                .map(this::toAdminPlannerListItem)
                .sorted(java.util.Comparator.comparing(AdminPlannerListItem::joinedAt).reversed())
                .toList();
    }

    /**
     * One planner's profile, for the admin module's read-only detail view.
     * Same signupIntent=VENDOR exclusion as listPlannersForAdmin - an
     * incomplete vendor sign-up isn't reachable through this detail view
     * either, consistent with it never appearing in the list.
     */
    @Transactional(readOnly = true)
    public AdminPlannerListItem getPlannerForAdmin(Long userId) {
        User planner = userRepository.findById(userId)
                .filter(u -> u.getRole() == Role.PLANNER && u.getSignupIntent() != SignupIntent.VENDOR)
                .orElseThrow(() -> new IllegalStateException("Planner not found: " + userId));
        return toAdminPlannerListItem(planner);
    }

    private AdminPlannerListItem toAdminPlannerListItem(User planner) {
        return new AdminPlannerListItem(
                planner.getId(),
                planner.getFirstName(),
                planner.getLastName(),
                planner.getEmail(),
                planner.getMobileNumber(),
                planner.getCity(),
                planner.getState(),
                planner.getCreatedAt(),
                (int) eventRepository.countByPlannerId(planner.getId()));
    }

    public record AdminPlannerListItem(
            Long id,
            String firstName,
            String lastName,
            String email,
            String mobileNumber,
            String city,
            String state,
            Instant joinedAt,
            int eventsCount) {
    }

    /** Marks a vendor verified - the ONLY thing that puts the "Verified Vendor" badge on their storefront. */
    @Transactional
    public void verifyVendor(Long userId, String adminUsername) {
        VendorProfile profile = vendorProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalStateException("Vendor profile not found for user: " + userId));
        profile.setVerified(true);
        profile.setVerifiedAt(Instant.now());
        profile.setVerifiedByAdmin(adminUsername);
        vendorProfileRepository.save(profile);
    }

    /** Reverses a verification - e.g. a document turns out to be fraudulent after the fact. */
    @Transactional
    public void unverifyVendor(Long userId) {
        VendorProfile profile = vendorProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalStateException("Vendor profile not found for user: " + userId));
        profile.setVerified(false);
        profile.setVerifiedAt(null);
        profile.setVerifiedByAdmin(null);
        vendorProfileRepository.save(profile);
    }

    /** Admin-set "Top Vendor" spotlight flag - independent of verification. */
    @Transactional
    public void setTopVendor(Long userId, boolean topVendor) {
        VendorProfile profile = vendorProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalStateException("Vendor profile not found for user: " + userId));
        profile.setTopVendor(topVendor);
        vendorProfileRepository.save(profile);
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
        profile.setDescription(request.getDescription());
        profile.setOwnerName(request.getOwnerName());
        if (request.getBusinessTypes() != null) {
            profile.setBusinessTypes(request.getBusinessTypes());
        }
        profile.setContactEmail(request.getContactEmail());
        profile.setPhoneNumber(request.getPhoneNumber());
        profile.setFacebookPageUrl(request.getFacebookPageUrl());
        profile.setAddressLine1(request.getAddressLine1());
        profile.setAddressLine2(request.getAddressLine2());
        profile.setCity(request.getCity());
        profile.setState(request.getState());
        profile.setPostalCode(request.getPostalCode());
        profile.setCountry(request.getCountry());
        profile.setMaxGuestCapacity(request.getMaxGuestCapacity());
        profile.setMaxCustomersPerDay(request.getMaxCustomersPerDay());
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
                .slug(profile.getSlug())
                .businessName(profile.getBusinessName())
                .description(profile.getDescription())
                .ownerName(profile.getOwnerName())
                .businessTypes(new ArrayList<>(profile.getBusinessTypes()))
                .contactEmail(profile.getContactEmail())
                .phoneNumber(profile.getPhoneNumber())
                .facebookPageUrl(profile.getFacebookPageUrl())
                .addressLine1(profile.getAddressLine1())
                .addressLine2(profile.getAddressLine2())
                .city(profile.getCity())
                .state(profile.getState())
                .postalCode(profile.getPostalCode())
                .country(profile.getCountry())
                .logoImageUrl(profile.getLogoImageUrl())
                .idCardUrl(s3UploadService.presignedUrl(profile.getIdCardKey(), PRESIGNED_URL_TTL))
                .selfieUrl(s3UploadService.presignedUrl(profile.getSelfieKey(), PRESIGNED_URL_TTL))
                .verified(profile.isVerified())
                .verifiedAt(profile.getVerifiedAt())
                .maxGuestCapacity(profile.getMaxGuestCapacity())
                .maxCustomersPerDay(profile.getMaxCustomersPerDay())
                .basePrice(profile.getBasePrice())
                .leadTimeDays(profile.getLeadTimeDays())
                .storefrontOverview(profile.getStorefrontOverview())
                // Both are LAZY @ElementCollections - materialized into a
                // plain list here, inside this @Transactional method, since
                // spring.jpa.open-in-view=false means the Hibernate session
                // is gone by the time this response is serialized.
                .operatingAreas(new ArrayList<>(profile.getOperatingAreas()))
                .cateredEventTypes(new ArrayList<>(profile.getCateredEventTypes()))
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
            Long vendorUserId,
            String businessName,
            String ownerName,
            String idCardUrl,
            String selfieUrl,
            List<VendorLegalDocumentResponse> legalDocuments,
            boolean verified,
            Instant verifiedAt,
            String verifiedByAdmin,
            boolean topVendor) {
    }

    public record AdminVendorListItem(
            Long vendorUserId,
            String businessName,
            String ownerName,
            String contactEmail,
            String phoneNumber,
            List<BusinessType> businessTypes,
            String slug,
            String city,
            String state,
            List<String> operatingAreas,
            boolean hasIdCard,
            boolean hasSelfie,
            int legalDocumentCount,
            boolean verified,
            Instant verifiedAt,
            String verifiedByAdmin,
            boolean topVendor,
            Instant createdAt,
            long referralCount,
            boolean fakeAccount,
            long bookingCount,
            Instant lastLoginAt,
            String billingSource,
            boolean planExpired,
            boolean planInGracePeriod,
            Instant planOverdueSince) {
    }

    public record AdminIncompleteVendorSignup(
            Long id,
            String firstName,
            String lastName,
            String email,
            String mobileNumber,
            Instant signedUpAt) {
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
