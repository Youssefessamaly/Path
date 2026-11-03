package com.internplatform.ats.access;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CompanyMemberRepository extends JpaRepository<CompanyMember, UUID> {

    /** Oldest-first — if a user ever holds more than one ACTIVE membership (future
     *  multi-company support), CompanyAccessResolver resolves the earliest-created one. */
    List<CompanyMember> findAllByUserIdAndStatusOrderByCreatedAtAsc(UUID userId, MemberStatus status);

    Optional<CompanyMember> findByCompanyProfileIdAndUserId(UUID companyProfileId, UUID userId);
}
