package com.internplatform.internprofile;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.FileVisitResult;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.UUID;

/**
 * Local-filesystem storage for uploaded CVs. Files are addressed only by a
 * server-generated storage key — the client's original filename and any
 * client-supplied path segments are never used to build a filesystem path.
 */
@Service
public class CvStorageService {

    private final Path baseDirectory;

    public CvStorageService(@Value("${app.cv-storage.directory}") String directory) {
        this.baseDirectory = Path.of(directory).toAbsolutePath().normalize();
        try {
            Files.createDirectories(baseDirectory);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to create CV storage directory", exception);
        }
    }

    /**
     * Stores the given bytes under a fresh, server-generated key scoped to the profile.
     * Returns the storage key (relative to the base directory) — never an absolute path.
     */
    public String store(UUID internProfileId, byte[] content) {
        String storageKey = internProfileId + "/" + UUID.randomUUID() + ".pdf";
        Path target = resolveWithinBaseDirectory(storageKey);

        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content);
        } catch (IOException exception) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unable to store the uploaded file"
            );
        }

        return storageKey;
    }

    /**
     * Reads back the bytes previously stored under the given key — used to make an
     * independent physical copy of a CV (e.g. an application-time snapshot) rather than
     * sharing a storage key that a later profile CV replacement would delete out from under it.
     */
    public byte[] read(String storageKey) {
        try {
            return Files.readAllBytes(resolveWithinBaseDirectory(storageKey));
        } catch (IOException exception) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unable to read the stored file"
            );
        }
    }

    /** Best-effort delete of a previously stored file; never throws if it's already gone. */
    public void delete(String storageKey) {
        if (storageKey == null) {
            return;
        }
        try {
            Files.deleteIfExists(resolveWithinBaseDirectory(storageKey));
        } catch (IOException ignored) {
            // A stray orphaned file is not worth failing the request over.
        }
    }

    /**
     * Strict, retryable cleanup for committed account deletions. A task is generated ONLY
     * server-side. Unlike the legacy best-effort delete(), IO failures propagate so the
     * durable SQL queue entry stays pending and can be retried.
     */
    public void deleteAccountCleanupTask(String task) throws IOException {
        if (task.startsWith("profile:")) {
            UUID profileId = UUID.fromString(task.substring("profile:".length()));
            Path directory = resolveWithinBaseDirectory(profileId.toString());
            if (!Files.exists(directory)) {
                return;
            }
            // No FOLLOW_LINKS: a symlink inside the profile folder is deleted, never traversed.
            Files.walkFileTree(directory, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.deleteIfExists(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException error) throws IOException {
                    if (error != null) {
                        throw error;
                    }
                    Files.deleteIfExists(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } else if (task.startsWith("file:")) {
            String storageKey = task.substring("file:".length());
            String[] parts = storageKey.split("/", -1);
            if (parts.length != 2 || !parts[1].endsWith(".pdf")) {
                throw new IllegalArgumentException("Invalid CV deletion task");
            }
            UUID.fromString(parts[0]);
            UUID.fromString(parts[1].substring(0, parts[1].length() - 4));
            Files.deleteIfExists(resolveWithinBaseDirectory(storageKey));
        } else {
            throw new IllegalArgumentException("Invalid CV deletion task");
        }
    }

    private Path resolveWithinBaseDirectory(String storageKey) {
        Path resolved = baseDirectory.resolve(storageKey).normalize();
        if (!resolved.startsWith(baseDirectory)) {
            throw new IllegalArgumentException("Invalid storage key");
        }
        return resolved;
    }
}
