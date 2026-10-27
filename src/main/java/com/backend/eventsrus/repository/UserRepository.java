package com.backend.eventsrus.repository;

import com.backend.eventsrus.enums.Role;
import com.backend.eventsrus.enums.SignupIntent;
import com.backend.eventsrus.model.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    /** Every live (not yet scrubbed) account with the given role, for the lapsed-supplier sweep. */
    java.util.List<User> findByRoleAndDeletedAtIsNull(com.backend.eventsrus.enums.Role role);

    /** Accounts whose 30-day deletion grace period has run out and are not yet scrubbed. */
    java.util.List<User> findByDeletionRequestedAtBeforeAndDeletedAtIsNull(java.time.Instant cutoff);

    /** Deletion requests still inside the grace period (signing in again cancels them), newest first. */
    java.util.List<User> findByDeletionRequestedAtIsNotNullAndDeletedAtIsNullOrderByDeletionRequestedAtDesc();

    /** Accounts already scrubbed, most recent first (capped: the admin only needs the latest). */
    java.util.List<User> findTop200ByDeletedAtIsNotNullOrderByDeletedAtDesc();


    Optional<User> findByGoogleId(String googleId);

    Optional<User> findByEmail(String email);

    List<User> findByRole(Role role);

    long countByRole(Role role);

    // Excludes seeded/placeholder vendors (User.fakeAccount) from the admin
    // dashboard's vendor total - see AdminDashboardController.
    long countByRoleAndFakeAccountFalse(Role role);

    // Declared vendor intent (signed up through "Become a Vendor") but
    // hasn't finished the onboarding form yet (role hasn't flipped to
    // VENDOR) - see UserService#findOrCreateFromGoogle.
    long countBySignupIntentAndRoleNot(SignupIntent signupIntent, Role role);

    List<User> findBySignupIntentAndRoleNotOrderByCreatedAtDesc(SignupIntent signupIntent, Role role);

    // A user mid-vendor-onboarding is still role=PLANNER (that only flips
    // once becomeVendor succeeds) but has already declared signupIntent=
    // VENDOR at sign-in - excluding that here is what keeps them out of the
    // real Planners directory/count, so they only show up once, in the
    // admin Vendors page's "Incomplete Sign-ups" tab.
    List<User> findByRoleAndSignupIntentNot(Role role, SignupIntent signupIntent);

    long countByRoleAndSignupIntentNot(Role role, SignupIntent signupIntent);
}
