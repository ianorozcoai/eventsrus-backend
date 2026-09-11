package com.backend.eventsrus.enums;

/**
 * Which door a Google account first walked through - set once, at account
 * creation, and never changed afterward. Distinct from {@link Role}, which
 * still freely flows PLANNER -> VENDOR the moment a vendor finishes
 * onboarding: this field is what actually locks an email to one identity,
 * so a returning user's *declared* intent can be checked against it even
 * while role is still catching up (mid-onboarding, role is still PLANNER
 * but signupIntent is already VENDOR). See UserService#findOrCreateFromGoogle.
 */
public enum SignupIntent {
    PLANNER,
    VENDOR
}
