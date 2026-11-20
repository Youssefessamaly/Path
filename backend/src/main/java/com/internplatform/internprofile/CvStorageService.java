package com.internplatform.internprofile;

import com.internplatform.storage.ObjectStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;
import java.util.UUID;

/** Private R2-backed CV storage. Both profile CVs and immutable application snapshots are PDFs. */
@Service
public class CvStorageService {
    private static final Logger log = LoggerFactory.getLogger(CvStorageService.class);
    private static final String PROFILE_PREFIX = "students/cvs/";
    private static final String SNAPSHOT_PREFIX = "applications/cv-snapshots/";
    private static final String LEGACY_PREFIX = "legacy/cvs/";
    private final ObjectStorage objects;

    public CvStorageService(ObjectStorage objects) {
        this.objects = objects;
    }

    /** Persist a new profile CV. A replacement always receives a fresh key. */
    public String store(UUID internProfileId, byte[] content) {
        return storeUnder(PROFILE_PREFIX, internProfileId, content);
    }

    /** Create a physically independent copy when a student applies. */
    public String storeSnapshot(UUID internProfileId, byte[] content) {
        return storeUnder(SNAPSHOT_PREFIX, internProfileId, content);
    }

    private String storeUnder(String prefix, UUID profileId, byte[] content) {
        if (content == null || content.length == 0 || content.length > 5 * 1024 * 1024) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid CV size");
        }
        String key = prefix + profileId + "/" + UUID.randomUUID() + ".pdf";
        try {
            objects.put(key, content, "application/pdf");
            return key;
        } catch (RuntimeException ex) {
            log.warn("Unable to upload CV to private object storage", ex);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "CV storage is currently unavailable");
        }
    }

    public byte[] read(String storageKey) {
        String key = validate(storageKey);
        try {
            return objects.get(key);
        } catch (S3Exception ex) {
            if (ex.statusCode() == 404) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "CV file not found");
            }
            log.warn("R2 could not retrieve CV (status={})", ex.statusCode());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "CV storage is currently unavailable");
        } catch (RuntimeException ex) {
            log.warn("R2 could not retrieve CV", ex);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "CV storage is currently unavailable");
        }
    }

    /** Best-effort delete after an update commits or a transaction rolls back. */
    public void delete(String storageKey) {
        if (storageKey == null) return;
        try {
            objects.delete(validate(storageKey));
        } catch (RuntimeException ex) {
            log.warn("Unable to delete CV object; it may need later cleanup");
        }
    }

    /** Strict / retryable deletion for the existing durable SQL account-cleanup outbox. */
    public void deleteAccountCleanupTask(String task) throws IOException {
        if (task.startsWith("profile:")) {
            UUID profileId = UUID.fromString(task.substring("profile:".length()));
            objects.deletePrefix(PROFILE_PREFIX + profileId + "/");
            objects.deletePrefix(SNAPSHOT_PREFIX + profileId + "/");
            objects.deletePrefix(LEGACY_PREFIX + profileId + "/");
        } else if (task.startsWith("file:")) {
            objects.delete(validate(task.substring("file:".length())));
        } else {
            throw new IllegalArgumentException("Invalid CV cleanup task");
        }
    }

    private String validate(String storageKey) {
        if (storageKey == null) throw new IllegalArgumentException("Missing CV key");
        // Old filesystem references were UUID/UUID.pdf. They can be imported into legacy/cvs/.
        if (storageKey.matches("[a-fA-F0-9-]{36}/[a-fA-F0-9-]{36}\\.pdf")) {
            String[] parts = storageKey.split("/");
            UUID.fromString(parts[0]);
            UUID.fromString(parts[1].substring(0, 36));
            return LEGACY_PREFIX + storageKey;
        }
        if (!storageKey.startsWith(PROFILE_PREFIX) && !storageKey.startsWith(SNAPSHOT_PREFIX)
                && !storageKey.startsWith(LEGACY_PREFIX)) {
            throw new IllegalArgumentException("Invalid CV key");
        }
        String prefix = storageKey.startsWith(PROFILE_PREFIX) ? PROFILE_PREFIX
                : storageKey.startsWith(SNAPSHOT_PREFIX) ? SNAPSHOT_PREFIX : LEGACY_PREFIX;
        String[] parts = storageKey.substring(prefix.length()).split("/", -1);
        if (parts.length != 2 || !parts[1].endsWith(".pdf") || parts[1].length() != 40) {
            throw new IllegalArgumentException("Invalid CV key");
        }
        UUID.fromString(parts[0]);
        UUID.fromString(parts[1].substring(0, 36));
        return storageKey;
    }
}
