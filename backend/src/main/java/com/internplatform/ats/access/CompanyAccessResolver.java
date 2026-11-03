package com.internplatform.ats.access;

import com.internplatform.companyprofile.CompanyApprovalGuard;
import com.internplatform.companyprofile.CompanyProfile;
import com.internplatform.companyprofile.CompanyProfileRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

/**
 * Single checkpoint resolving "which company (if any) is this authenticated user allowed to
 * act on behalf of, and with what role" — the ATS equivalent of CompanyApprovalGuard. Every
 * company-scoped service resolves a CompanyContext here instead of calling
 * CompanyProfileRepository.findByUserId directly, so a revoked member, a non-member, and an
 * unapproved company all collapse to a consistent outcome regardless of which endpoint they hit.
 *
 * Resolution never trusts a client-supplied company id: the authenticated user's own identity
 * (from the JWT subject) is the only input.
 *
 * Owner resolution is a deliberate two-tier design:
 *   1. company_profiles.user_id (the pre-existing, untouched 1:1 relationship) is checked
 *      FIRST and is always authoritative for OWNER access — independent of whether a matching
 *      ats_company_members row exists. This means every company that existed before this
 *      resolver was introduced (and every CompanyProfile created directly, bypassing
 *      registration — a common existing test-fixture pattern) keeps resolving its owner
 *      exactly as it did via the old direct findByUserId lookup, with zero dependency on the
 *      V17 backfill having run.
 *   2. Only when no owned CompanyProfile exists does resolution fall through to an ACTIVE
 *      ats_company_members row — the only path by which a RECRUITER or INTERVIEWER (who has
 *      no company_profiles row of their own) is ever resolved.
 */
@Component
public class CompanyAccessResolver {

    private final CompanyProfileRepository companyProfileRepository;
    private final CompanyMemberRepository companyMemberRepository;
    private final CompanyApprovalGuard companyApprovalGuard;

    public CompanyAccessResolver(
            CompanyProfileRepository companyProfileRepository,
            CompanyMemberRepository companyMemberRepository,
            CompanyApprovalGuard companyApprovalGuard
    ) {
        this.companyProfileRepository = companyProfileRepository;
        this.companyMemberRepository = companyMemberRepository;
        this.companyApprovalGuard = companyApprovalGuard;
    }

    /**
     * Core, non-throwing resolution — returns empty rather than throwing when the user has no
     * access, so callers can choose their own not-found status/message (some existing call
     * sites return 403 rather than 404 for historical reasons, preserved deliberately rather
     * than changed here). No approval check is performed.
     */
    @Transactional(readOnly = true)
    public Optional<CompanyContext> findActiveMember(UUID userId) {
        Optional<CompanyProfile> owned = companyProfileRepository.findByUserId(userId);

        if (owned.isPresent()) {
            CompanyProfile profile = owned.get();
            UUID memberId = companyMemberRepository
                    .findByCompanyProfileIdAndUserId(profile.getId(), userId)
                    .map(CompanyMember::getId)
                    .orElse(null);
            return Optional.of(new CompanyContext(profile.getId(), memberId, userId, AtsMemberRole.OWNER));
        }

        return companyMemberRepository
                .findAllByUserIdAndStatusOrderByCreatedAtAsc(userId, MemberStatus.ACTIVE)
                .stream()
                .findFirst()
                .map(member -> new CompanyContext(member.getCompanyProfileId(), member.getId(), userId, member.getRole()));
    }

    /** Resolves the CompanyProfile entity backing an already-resolved CompanyContext. */
    @Transactional(readOnly = true)
    public Optional<CompanyProfile> findCompanyProfile(CompanyContext context) {
        return companyProfileRepository.findById(context.companyProfileId());
    }

    /** findActiveMember, but throws 404 ("Company not found") when absent — the convention
     *  every NEW ats endpoint should use, so a revoked member and a non-member are
     *  indistinguishable from each other and from a company that never existed. */
    public CompanyContext resolveActiveMember(UUID userId) {
        return findActiveMember(userId).orElseThrow(this::companyNotFound);
    }

    /** resolveActiveMember, plus the company must be APPROVED — mirrors
     *  CompanyApprovalGuard's existing role in InternshipService, CompanyApplicationService,
     *  and InternshipApplicationQuestionService. Use this for marketplace-facing actions; use
     *  resolveActiveMember/findActiveMember for the small set of self-service endpoints
     *  (profile view/edit, approval-status, own-membership) a PENDING/REJECTED company must
     *  still be able to reach. */
    public CompanyContext requireActiveMember(UUID userId) {
        CompanyContext context = resolveActiveMember(userId);
        CompanyProfile profile = findCompanyProfile(context).orElseThrow(this::companyNotFound);
        companyApprovalGuard.requireApproved(profile);
        return context;
    }

    /** requireActiveMember, additionally restricted to the OWNER role. */
    public CompanyContext requireOwner(UUID userId) {
        CompanyContext context = requireActiveMember(userId);
        requireOwnerRole(context);
        return context;
    }

    /** resolveActiveMember, additionally restricted to the OWNER role, WITHOUT the approval
     *  gate — company profile editing and onboarding completion must work even before a
     *  company is approved (they always have, independent of this resolver). */
    public CompanyContext resolveOwner(UUID userId) {
        CompanyContext context = resolveActiveMember(userId);
        requireOwnerRole(context);
        return context;
    }

    private void requireOwnerRole(CompanyContext context) {
        if (!context.isOwner()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Only the company owner can perform this action"
            );
        }
    }

    private ResponseStatusException companyNotFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Company not found");
    }
}
