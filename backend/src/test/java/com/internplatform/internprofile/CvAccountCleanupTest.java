package com.internplatform.internprofile;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CvAccountCleanupTest {
    @TempDir Path tempDir;

    @Test
    void deletingProfileRemovesSnapshotsAndOrphansButNotOtherProfiles() throws Exception {
        CvStorageService storage = new CvStorageService(tempDir.toString());
        UUID owner = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        String first = storage.store(owner, new byte[]{1, 2, 3});
        String second = storage.store(owner, new byte[]{4, 5, 6});
        String unrelated = storage.store(other, new byte[]{7});
        storage.deleteAccountCleanupTask("profile:" + owner);
        assertThat(Files.exists(tempDir.resolve(first))).isFalse();
        assertThat(Files.exists(tempDir.resolve(second))).isFalse();
        assertThat(Files.exists(tempDir.resolve(unrelated))).isTrue();
        storage.deleteAccountCleanupTask("profile:" + owner); // idempotent
    }

    @Test
    void deletingApplicationSnapshotDoesNotRemoveProfileCv() throws Exception {
        CvStorageService storage = new CvStorageService(tempDir.toString());
        UUID profile = UUID.randomUUID();
        String currentCv = storage.store(profile, new byte[]{1});
        String submittedSnapshot = storage.store(profile, new byte[]{2});
        storage.deleteAccountCleanupTask("file:" + submittedSnapshot);
        assertThat(Files.exists(tempDir.resolve(submittedSnapshot))).isFalse();
        assertThat(Files.exists(tempDir.resolve(currentCv))).isTrue();
        storage.deleteAccountCleanupTask("file:" + submittedSnapshot); // idempotent
    }
}
