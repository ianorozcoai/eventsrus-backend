package com.backend.eventsrus.repository;

import com.backend.eventsrus.enums.Role;
import com.backend.eventsrus.enums.SignupIntent;
import com.backend.eventsrus.model.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByGoogleId(String googleId);

    Optional<User> findByEmail(String email);

    List<User> findByRole(Role role);

    long countByRole(Role role);

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
