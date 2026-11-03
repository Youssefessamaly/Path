package com.internplatform.ats.access;

import com.internplatform.companyprofile.CompanyProfile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Minimal company-membership API for Phase 1 — just enough for a client to learn "am I an
 * authorized member of a company, and what role do I have." Invitation and team-management
 * endpoints are a later phase.
 *
 * Deliberately NOT gated by company approval, mirroring CompanyProfileService.getApprovalStatus
 * — a PENDING or REJECTED company's members must still be able to see their own membership.
 */
@RestController
@RequestMapping("/api/v1/company/ats/members")
public class CompanyMemberController {

    private final CompanyAccessResolver companyAccessResolver;
    private final CompanyMemberRepository companyMemberRepository;

    public CompanyMemberController(
            CompanyAccessResolver companyAccessResolver,
            CompanyMemberRepository companyMemberRepository
    ) {
        this.companyAccessResolver = companyAccessResolver;
        this.companyMemberRepository = companyMemberRepository;
    }

    @GetMapping("/me")
    public CompanyMemberResponse getOwnMembership(@AuthenticationPrincipal Jwt jwt) {
        CompanyContext context = companyAccessResolver.resolveActiveMember(currentUserId(jwt));

        // memberId may be null for an owner resolved via the company_profiles.user_id fast
        // path with no backing ats_company_members row (see CompanyContext) — fall back to the
        // company profile's own createdAt rather than failing the request.
        OffsetDateTime createdAt = context.memberId() != null
                ? companyMemberRepository.findById(context.memberId())
                        .map(CompanyMember::getCreatedAt)
                        .orElse(null)
                : companyAccessResolver.findCompanyProfile(context)
                        .map(CompanyProfile::getCreatedAt)
                        .orElse(null);

        return new CompanyMemberResponse(
                context.memberId(),
                context.companyProfileId(),
                context.role(),
                MemberStatus.ACTIVE,
                createdAt
        );
    }

    private UUID currentUserId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
