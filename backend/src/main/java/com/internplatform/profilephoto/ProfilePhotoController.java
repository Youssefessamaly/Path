package com.internplatform.profilephoto;

import com.internplatform.ats.access.CompanyAccessResolver;
import com.internplatform.companyprofile.CompanyProfile;
import com.internplatform.companyprofile.CompanyProfileRepository;
import com.internplatform.internprofile.InternProfile;
import com.internplatform.internprofile.InternProfileRepository;
import com.internplatform.internshipapplication.InternshipApplicationRepository;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/v1")
public class ProfilePhotoController {
    private final InternProfileRepository interns;
    private final CompanyProfileRepository companies;
    private final CompanyAccessResolver companyAccess;
    private final InternshipApplicationRepository applications;
    private final ProfilePhotoStorageService storage;

    public ProfilePhotoController(InternProfileRepository interns, CompanyProfileRepository companies,
                                  CompanyAccessResolver companyAccess, InternshipApplicationRepository applications,
                                  ProfilePhotoStorageService storage) {
        this.interns = interns;
        this.companies = companies;
        this.companyAccess = companyAccess;
        this.applications = applications;
        this.storage = storage;
    }

    @PostMapping(value = "/intern/profile/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    public PhotoResponse uploadIntern(@AuthenticationPrincipal Jwt jwt, @RequestParam("file") MultipartFile file) {
        InternProfile profile = interns.findByUserId(userId(jwt)).orElseThrow(this::missing);
        String next = storage.store(profile.getId(), storage.normalize(contents(file)));
        String previous = profile.getPhotoStorageKey();
        profile.setPhotoStorageKey(next);
        interns.saveAndFlush(profile);
        scheduleCleanup(next, previous);
        return new PhotoResponse(ProfilePhotoUrls.intern(profile));
    }

    @DeleteMapping("/intern/profile/photo")
    @Transactional
    public ResponseEntity<Void> deleteIntern(@AuthenticationPrincipal Jwt jwt) {
        InternProfile profile = interns.findByUserId(userId(jwt)).orElseThrow(this::missing);
        String previous = profile.getPhotoStorageKey();
        profile.setPhotoStorageKey(null);
        interns.saveAndFlush(profile);
        scheduleCleanup(null, previous);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/company/profile/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    public PhotoResponse uploadCompany(@AuthenticationPrincipal Jwt jwt, @RequestParam("file") MultipartFile file) {
        CompanyProfile profile = companyAccess.findCompanyProfile(companyAccess.resolveOwner(userId(jwt)))
                .orElseThrow(this::missing);
        String next = storage.store(profile.getId(), storage.normalize(contents(file)));
        String previous = profile.getPhotoStorageKey();
        profile.setPhotoStorageKey(next);
        companies.saveAndFlush(profile);
        scheduleCleanup(next, previous);
        return new PhotoResponse(ProfilePhotoUrls.company(profile));
    }

    @DeleteMapping("/company/profile/logo")
    @Transactional
    public ResponseEntity<Void> deleteCompany(@AuthenticationPrincipal Jwt jwt) {
        CompanyProfile profile = companyAccess.findCompanyProfile(companyAccess.resolveOwner(userId(jwt)))
                .orElseThrow(this::missing);
        String previous = profile.getPhotoStorageKey();
        profile.setPhotoStorageKey(null);
        companies.saveAndFlush(profile);
        scheduleCleanup(null, previous);
        return ResponseEntity.noContent().build();
    }

    /** Company logos are public assets; the surrounding feed still controls which jobs are visible. */
    @GetMapping("/profile-images/companies/{profileId}")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> companyLogo(@PathVariable UUID profileId) {
        CompanyProfile profile = companies.findById(profileId).orElseThrow(this::missing);
        return image(profile.getPhotoStorageKey(), false);
    }

    /** An intern may read their own photo; a company may read only actual applicants to its internships. */
    @GetMapping("/profile-images/interns/{profileId}")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> internPhoto(@PathVariable UUID profileId, @AuthenticationPrincipal Jwt jwt) {
        if (jwt == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        InternProfile profile = interns.findById(profileId).orElseThrow(this::missing);
        boolean owner = profile.getUserId().equals(userId(jwt));
        if (!owner) {
            var companyContext = companyAccess.findActiveMember(userId(jwt)).orElseThrow(this::missing);
            if (!applications.existsForCompanyAndIntern(companyContext.companyProfileId(), profileId)) {
                throw missing();
            }
        }
        return image(profile.getPhotoStorageKey(), true);
    }

    private ResponseEntity<byte[]> image(String key, boolean privateImage) {
        if (key == null) throw missing();
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .cacheControl(privateImage ? CacheControl.noStore() : CacheControl.maxAge(1, TimeUnit.DAYS).cachePublic())
                .body(storage.read(key));
    }

    private byte[] contents(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > ProfilePhotoStorageService.MAX_INPUT_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Photo must be 5 MB or smaller");
        }
        try { return file.getBytes(); }
        catch (IOException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to read photo"); }
    }

    /** Only after commit delete the old image; on rollback remove the newly stored file. */
    private void scheduleCleanup(String newKey, String oldKey) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { storage.delete(oldKey); }
            @Override public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) storage.delete(newKey);
            }
        });
    }

    private UUID userId(Jwt jwt) { return UUID.fromString(jwt.getSubject()); }
    private ResponseStatusException missing() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "Photo or profile not found"); }
    public record PhotoResponse(String photoUrl) { }
}
