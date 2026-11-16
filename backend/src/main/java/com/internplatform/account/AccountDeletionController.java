package com.internplatform.account;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** The authenticated JWT subject is the ONLY account that may be deleted. */
@RestController
@RequestMapping("/api/v1/auth")
public class AccountDeletionController {
    private final AccountDeletionService deletionService;

    public AccountDeletionController(AccountDeletionService deletionService) {
        this.deletionService = deletionService;
    }

    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMyAccount(@AuthenticationPrincipal Jwt jwt,
                                @Valid @RequestBody DeleteAccountRequest request) {
        deletionService.deleteAccount(UUID.fromString(jwt.getSubject()), request.password());
    }
}
