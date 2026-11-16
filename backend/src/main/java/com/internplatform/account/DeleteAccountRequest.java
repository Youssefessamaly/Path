package com.internplatform.account;

import jakarta.validation.constraints.NotBlank;

/** Identity confirmation for irreversible self-service account deletion. */
public record DeleteAccountRequest(@NotBlank String password) {}
