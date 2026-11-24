package com.internplatform.internprofile;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import com.internplatform.profilephoto.ProfilePhotoUrls;
import java.time.OffsetDateTime;
import java.time.Year;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class InternProfileService {

    private static final Pattern LOCAL_EGYPT_PHONE = Pattern.compile("^01[0125]\\d{8}$");
    private static final Pattern INTERNATIONAL_EGYPT_PHONE = Pattern.compile("^\\+201[0125]\\d{8}$");

    private static final int MAX_GRADUATION_YEARS_AHEAD = 10;

    private static final long MAX_CV_SIZE_BYTES = 5L * 1024 * 1024;
    private static final byte[] PDF_SIGNATURE = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    private static final int MAX_CV_FILENAME_LENGTH = 255;

    private final InternProfileRepository internProfileRepository;
    private final SkillRepository skillRepository;
    private final CvStorageService cvStorageService;
    private final CvSkillExtractionService cvSkillExtractionService;
    private final InternCvSkillEvidenceRepository cvSkillEvidenceRepository;

    public InternProfileService(
            InternProfileRepository internProfileRepository,
            SkillRepository skillRepository,
            CvStorageService cvStorageService,
            CvSkillExtractionService cvSkillExtractionService,
            InternCvSkillEvidenceRepository cvSkillEvidenceRepository
    ) {
        this.internProfileRepository = internProfileRepository;
        this.skillRepository = skillRepository;
        this.cvStorageService = cvStorageService;
        this.cvSkillExtractionService = cvSkillExtractionService;
        this.cvSkillEvidenceRepository = cvSkillEvidenceRepository;
    }

    @Transactional(readOnly = true)
    public InternProfileResponse getProfile(UUID userId) {
        InternProfile profile = findProfileOrThrow(userId);
        return toResponse(profile);
    }

    @Transactional(readOnly = true)
    public CvDownload getCv(UUID userId) {
        InternProfile profile = findProfileOrThrow(userId);

        if (profile.getCvStorageKey() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "CV not found");
        }

        byte[] bytes = cvStorageService.read(profile.getCvStorageKey());
        return new CvDownload(bytes, profile.getCvOriginalFilename(), profile.getCvContentType());
    }

    /** Curated catalog only — see resolveOrCreateSkill for why a custom (is_standard=false)
     *  skill is deliberately excluded from this general suggestions/browse list, and
     *  InternProfileResponse/InternshipResponse for how a caller's own already-selected custom
     *  skill still reaches them regardless (those come from the profile's/internship's own
     *  skill associations, never from this list). */
    public List<SkillResponse> getSkillCatalog() {
        return skillRepository.findAllByStandardTrueOrderByNameAsc().stream()
                .map(this::toSkillResponse)
                .toList();
    }

    /**
     * Resolves a user-typed skill name against the catalog — first by exact name, then by
     * known alias, both case-insensitive — and returns that existing skill untouched. Only
     * when neither matches is a new skill actually created, explicitly flagged as non-standard
     * (see Skill#standard) and excluded from getSkillCatalog's general suggestions list above,
     * though it remains fully usable (selectable, CV-detectable, rubric-eligible — nothing
     * else filters by this flag) and resolvable again by the same name/alias lookup, so the
     * exact same submission from anyone else reuses it rather than creating a second copy.
     * Reused by both INTERN and COMPANY callers (same shared endpoint as getSkillCatalog).
     *
     * Deliberately NOT @Transactional: each repository call below gets its own independent
     * transaction (Spring Data's default per-method demarcation). That matters specifically
     * for the race-condition fallback — if saveAndFlush fails because a concurrent request
     * just inserted the same (or differently-cased) name, PostgreSQL has already marked that
     * statement's transaction unusable for anything further. Re-querying inside that same
     * transaction would itself fail with "current transaction is aborted"; letting each call
     * run in its own transaction means the fallback lookup below starts clean. The unique,
     * case-insensitive uk_skills_name_lower index (V30) is what actually makes the race
     * detectable at all — the application-level checks above it are the common-case fast path.
     */
    public SkillResponse resolveOrCreateSkill(String rawName) {
        String trimmed = rawName.trim();
        if (trimmed.length() < 2 || trimmed.length() > 100) {
            // @Size on SkillCreateRequest checks the raw string, which padding whitespace can
            // satisfy even when the trimmed value — the one actually stored — would not (e.g.
            // "a " passes a raw min-length of 2 but trims to a 1-character name). This is the
            // check that applies to what's actually persisted.
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Skill name must be between 2 and 100 characters");
        }

        Optional<Skill> existingByName = skillRepository.findByNameIgnoreCase(trimmed);
        if (existingByName.isPresent()) {
            return toSkillResponse(existingByName.get());
        }

        Optional<Skill> existingByAlias = skillRepository.findByAliasIgnoreCase(trimmed);
        if (existingByAlias.isPresent()) {
            return toSkillResponse(existingByAlias.get());
        }

        Skill custom = new Skill(trimmed);
        custom.setStandard(false);
        try {
            return toSkillResponse(skillRepository.saveAndFlush(custom));
        } catch (DataIntegrityViolationException raceLostToAnotherRequest) {
            // Someone else created the exact same (or differently-cased) name between our
            // check above and this insert — re-resolve rather than surface a raw 500 or a
            // misleading "skill name already exists" error for what is, to this caller, a
            // perfectly normal duplicate submission. Runs in its own fresh transaction (see
            // the method-level note above) — the prior statement's aborted transaction can't
            // affect it.
            return skillRepository.findByNameIgnoreCase(trimmed)
                    .map(this::toSkillResponse)
                    .orElseThrow(() -> raceLostToAnotherRequest);
        }
    }

    private SkillResponse toSkillResponse(Skill skill) {
        return new SkillResponse(skill.getId(), skill.getName());
    }

    @Transactional
    public InternProfileResponse upsertProfile(UUID userId, InternProfileRequest request) {
        InternProfile profile = internProfileRepository.findByUserId(userId)
                .orElseGet(() -> new InternProfile(userId));

        if (request.firstName() != null) {
            profile.setFirstName(request.firstName());
        }

        if (request.lastName() != null) {
            profile.setLastName(request.lastName());
        }

        if (request.dateOfBirth() != null) {
            profile.setDateOfBirth(request.dateOfBirth());
        }

        if (request.phoneNumber() != null) {
            profile.setPhoneNumber(normalizeEgyptianPhoneNumber(request.phoneNumber()));
        }

        if (request.institutionName() != null) {
            profile.setInstitutionName(request.institutionName());
        }

        if (request.academicLevel() != null) {
            profile.setAcademicLevel(request.academicLevel());
        }

        if (request.academicYear() != null) {
            profile.setAcademicYear(request.academicYear().shortValue());
        }

        if (request.major() != null) {
            profile.setMajor(request.major());
        }

        if (request.expectedGraduationYear() != null) {
            validateExpectedGraduationYear(request.expectedGraduationYear());
            profile.setExpectedGraduationYear(request.expectedGraduationYear().shortValue());
        }

        if (request.skillIds() != null) {
            profile.setSkills(resolveSkills(request.skillIds()));
        }

        if (request.careerInterests() != null) {
            profile.setCareerInterests(request.careerInterests());
        }

        if (request.linkedinUrl() != null) {
            profile.setLinkedinUrl(request.linkedinUrl());
        }

        InternProfile saved = internProfileRepository.saveAndFlush(profile);
        return toResponse(saved);
    }

    @Transactional
    public CompletionResult completeOnboarding(UUID userId) {
        InternProfile profile = findProfileOrThrow(userId);

        if (profile.getOnboardingCompletedAt() != null) {
            return new CompletionResult(toResponse(profile), List.of());
        }

        List<String> missingFields = findMissingRequiredFields(profile);

        if (!missingFields.isEmpty()) {
            return new CompletionResult(null, missingFields);
        }

        profile.setOnboardingCompletedAt(OffsetDateTime.now(ZoneOffset.UTC));
        InternProfile saved = internProfileRepository.saveAndFlush(profile);

        return new CompletionResult(toResponse(saved), List.of());
    }

    @Transactional
    public CvUploadResponse uploadCv(UUID userId, MultipartFile file) {
        InternProfile profile = findProfileOrThrow(userId);

        validateCvFile(file);

        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to read the uploaded file");
        }

        validatePdfSignature(content);

        // Extraction/detection happens before anything is persisted or stored: if the PDF
        // can't be parsed, the upload fails cleanly with the old CV/metadata untouched,
        // exactly like a bad signature already does — never a half-applied replacement.
        String extractedText = cvSkillExtractionService.extractText(content);
        List<SkillEvidence> skillEvidence = cvSkillExtractionService.detectSkillEvidence(
                extractedText, skillRepository.findAllByOrderByNameAsc());
        Set<Skill> detectedSkills = new HashSet<>(skillEvidence.stream().map(SkillEvidence::skill).toList());

        String previousStorageKey = profile.getCvStorageKey();
        String storageKey = cvStorageService.store(profile.getId(), content);
        // R2 writes cannot roll back with SQL. Keep the old CV until commit succeeds.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { cvStorageService.delete(previousStorageKey); }
            @Override public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) cvStorageService.delete(storageKey);
            }
        });

        profile.setCvOriginalFilename(sanitizeCvFileName(file.getOriginalFilename()));
        profile.setCvStorageKey(storageKey);
        profile.setCvContentType(MediaType.APPLICATION_PDF_VALUE);
        profile.setCvSizeBytes(content.length);
        profile.setCvUploadedAt(OffsetDateTime.now(ZoneOffset.UTC));
        // Full replace, not merge — a replaced CV must not accumulate skills detected from
        // an earlier, now-superseded CV.
        profile.setCvDetectedSkills(detectedSkills);

        InternProfile saved = internProfileRepository.saveAndFlush(profile);
        replaceCvSkillEvidence(saved.getId(), skillEvidence);

        return new CvUploadResponse(
                saved.getCvOriginalFilename(),
                saved.getCvContentType(),
                saved.getCvSizeBytes(),
                saved.getCvUploadedAt(),
                toSkillResponses(saved.getCvDetectedSkills())
        );
    }

    private void validateCvFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CV file is required");
        }

        if (file.getSize() > MAX_CV_SIZE_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CV file must be 5 MB or smaller");
        }

        String contentType = file.getContentType();
        if (contentType != null && !contentType.equalsIgnoreCase(MediaType.APPLICATION_PDF_VALUE)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CV must be a PDF file");
        }
    }

    private void validatePdfSignature(byte[] content) {
        boolean looksLikePdf = content.length >= PDF_SIGNATURE.length
                && Arrays.equals(content, 0, PDF_SIGNATURE.length, PDF_SIGNATURE, 0, PDF_SIGNATURE.length);

        if (!looksLikePdf) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CV must be a valid PDF file");
        }
    }

    private String sanitizeCvFileName(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "resume.pdf";
        }

        String withoutPathSegments = originalFilename.replace('\\', '/');
        withoutPathSegments = withoutPathSegments.substring(withoutPathSegments.lastIndexOf('/') + 1);

        String cleaned = withoutPathSegments.replaceAll("\\p{Cntrl}", "").trim();

        if (cleaned.isEmpty()) {
            return "resume.pdf";
        }

        if (cleaned.length() > MAX_CV_FILENAME_LENGTH) {
            cleaned = cleaned.substring(cleaned.length() - MAX_CV_FILENAME_LENGTH);
        }

        return cleaned;
    }

    private InternProfile findProfileOrThrow(UUID userId) {
        return internProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Intern profile not found"
                ));
    }

    private List<String> findMissingRequiredFields(InternProfile profile) {
        List<String> missing = new ArrayList<>();

        if (isBlank(profile.getFirstName())) {
            missing.add("firstName");
        }
        if (isBlank(profile.getLastName())) {
            missing.add("lastName");
        }
        if (profile.getDateOfBirth() == null) {
            missing.add("dateOfBirth");
        }
        if (isBlank(profile.getPhoneNumber())) {
            missing.add("phoneNumber");
        }
        if (isBlank(profile.getInstitutionName())) {
            missing.add("institutionName");
        }
        if (profile.getAcademicLevel() == null) {
            missing.add("academicLevel");
        }
        if (isBlank(profile.getMajor())) {
            missing.add("major");
        }
        if (profile.getExpectedGraduationYear() == null) {
            missing.add("expectedGraduationYear");
        }
        if (profile.getSkills().isEmpty()) {
            missing.add("skills");
        }
        if (isBlank(profile.getCareerInterests())) {
            missing.add("careerInterests");
        }
        // CV is mandatory for NEW interns to complete onboarding; photo remains optional.
        // Check the persisted storage key, not just the filename displayed in the UI.
        if (isBlank(profile.getCvStorageKey())) {
            missing.add("cv");
        }

        if (profile.getAcademicLevel() == AcademicLevel.UNIVERSITY) {
            Short academicYear = profile.getAcademicYear();
            if (academicYear == null || academicYear < 1 || academicYear > 6) {
                missing.add("academicYear");
            }
        }

        return missing;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String normalizeEgyptianPhoneNumber(String phoneNumber) {
        if (LOCAL_EGYPT_PHONE.matcher(phoneNumber).matches()) {
            return "+20" + phoneNumber.substring(1);
        }

        if (INTERNATIONAL_EGYPT_PHONE.matcher(phoneNumber).matches()) {
            return phoneNumber;
        }

        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Phone number must be a valid Egyptian mobile number"
        );
    }

    private void validateExpectedGraduationYear(int expectedGraduationYear) {
        int currentYear = Year.now(ZoneOffset.UTC).getValue();

        if (expectedGraduationYear < currentYear
                || expectedGraduationYear > currentYear + MAX_GRADUATION_YEARS_AHEAD) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Expected graduation year must be between "
                            + currentYear + " and " + (currentYear + MAX_GRADUATION_YEARS_AHEAD)
            );
        }
    }

    private Set<Skill> resolveSkills(List<UUID> skillIds) {
        if (skillIds.isEmpty()) {
            return new HashSet<>();
        }

        List<Skill> found = skillRepository.findAllById(skillIds);
        Set<UUID> requestedUnique = new HashSet<>(skillIds);

        if (found.size() != requestedUnique.size()) {
            Set<UUID> foundIds = found.stream()
                    .map(Skill::getId)
                    .collect(java.util.stream.Collectors.toSet());

            List<UUID> unknown = requestedUnique.stream()
                    .filter(id -> !foundIds.contains(id))
                    .toList();

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Unknown skill id(s): " + unknown
            );
        }

        return new HashSet<>(found);
    }

    private InternProfileResponse toResponse(InternProfile profile) {
        List<SkillResponse> skills = toSkillResponses(profile.getSkills());

        return new InternProfileResponse(
                profile.getId(),
                profile.getUserId(),
                profile.getFirstName(),
                profile.getLastName(),
                profile.getDateOfBirth(),
                profile.getPhoneNumber(),
                profile.getInstitutionName(),
                profile.getAcademicLevel(),
                toInteger(profile.getAcademicYear()),
                profile.getMajor(),
                toInteger(profile.getExpectedGraduationYear()),
                skills,
                profile.getCareerInterests(),
                profile.getLinkedinUrl(),
                profile.getOnboardingCompletedAt() != null,
                profile.getCvOriginalFilename(),
                profile.getCvUploadedAt(),
                toSkillResponses(profile.getCvDetectedSkills()),
                ProfilePhotoUrls.intern(profile),
                profile.getCreatedAt(),
                profile.getUpdatedAt()
        );
    }

    /**
     * Fully replaces the intern's stored CV skill evidence — delete-then-insert, never merge,
     * matching the existing cvDetectedSkills replace semantics — so a replaced CV never leaves
     * evidence from an earlier, now-superseded CV behind. Never touches the intern's manually
     * selected skills (profile.getSkills()), which this method does not reference at all.
     */
    private void replaceCvSkillEvidence(UUID internProfileId, List<SkillEvidence> skillEvidence) {
        cvSkillEvidenceRepository.deleteAllByInternProfileId(internProfileId);

        List<InternCvSkillEvidence> rows = skillEvidence.stream()
                .map(evidence -> new InternCvSkillEvidence(
                        internProfileId,
                        evidence.skill().getId(),
                        evidence.source(),
                        evidence.strength(),
                        evidence.textReference()
                ))
                .toList();
        cvSkillEvidenceRepository.saveAll(rows);
    }

    private List<SkillResponse> toSkillResponses(Set<Skill> skills) {
        return skills.stream()
                .sorted(Comparator.comparing(Skill::getName))
                .map(skill -> new SkillResponse(skill.getId(), skill.getName()))
                .toList();
    }

    private static Integer toInteger(Short value) {
        return value == null ? null : value.intValue();
    }

    public record CompletionResult(
            InternProfileResponse profile,
            List<String> missingFields
    ) {
    }

    public record CvDownload(byte[] bytes, String fileName, String contentType) {
    }
}
