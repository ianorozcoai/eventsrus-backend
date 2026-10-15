package com.backend.eventsrus.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** The explicit confirmation a user gives before account deletion starts. */
@Getter
@Setter
@NoArgsConstructor
public class AccountDeletionRequest {

    /** The user must type the word DELETE (any case). */
    @NotBlank
    @Pattern(regexp = "(?i)\\s*DELETE\\s*", message = "Type DELETE to confirm.")
    private String confirmText;

    /** Ticked "I understand and accept" on the warning screen. */
    @AssertTrue(message = "You must accept the deletion terms to continue.")
    private boolean acceptedTerms;
}
