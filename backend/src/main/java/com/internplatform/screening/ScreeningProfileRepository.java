package com.internplatform.screening;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScreeningProfileRepository extends JpaRepository<ScreeningProfile, UUID> {

    List<ScreeningProfile> findAllByCompanyProfileIdOrderByNameAsc(UUID companyProfileId);

    Optional<ScreeningProfile> findByIdAndCompanyProfileId(UUID id, UUID companyProfileId);
}
