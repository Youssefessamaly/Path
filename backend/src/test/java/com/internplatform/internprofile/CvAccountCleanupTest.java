package com.internplatform.internprofile;

import com.internplatform.storage.InMemoryObjectStorage;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CvAccountCleanupTest {
    @Test
    void deletingProfileRemovesProfileCvSnapshotsAndOrphansButNotOtherProfiles() throws Exception {
        var objects = new InMemoryObjectStorage();
        var storage = new CvStorageService(objects);
        UUID owner = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        String first = storage.store(owner, new byte[]{1, 2, 3});
        String snapshot = storage.storeSnapshot(owner, new byte[]{4, 5, 6});
        String unrelated = storage.store(other, new byte[]{7});
        assertThat(first).startsWith("students/cvs/");
        assertThat(snapshot).startsWith("applications/cv-snapshots/");
        storage.deleteAccountCleanupTask("profile:" + owner);
        assertThat(objects.contains(first)).isFalse();
        assertThat(objects.contains(snapshot)).isFalse();
        assertThat(objects.contains(unrelated)).isTrue();
        storage.deleteAccountCleanupTask("profile:" + owner);
    }

    @Test
    void deletingApplicationSnapshotDoesNotRemoveProfileCv() throws Exception {
        var objects = new InMemoryObjectStorage();
        var storage = new CvStorageService(objects);
        UUID profile = UUID.randomUUID();
        String current = storage.store(profile, new byte[]{1});
        String snapshot = storage.storeSnapshot(profile, new byte[]{2});
        storage.deleteAccountCleanupTask("file:" + snapshot);
        assertThat(objects.contains(snapshot)).isFalse();
        assertThat(storage.read(current)).containsExactly((byte) 1);
        storage.deleteAccountCleanupTask("file:" + snapshot);
    }

    @Test
    void legacyStorageKeysRemainReadableAfterImport() throws Exception {
        var objects = new InMemoryObjectStorage();
        var storage = new CvStorageService(objects);
        String key = UUID.randomUUID() + "/" + UUID.randomUUID() + ".pdf";
        objects.put("legacy/cvs/" + key, new byte[]{42}, "application/pdf");
        assertThat(storage.read(key)).containsExactly((byte) 42);
        storage.deleteAccountCleanupTask("file:" + key);
        assertThat(objects.contains("legacy/cvs/" + key)).isFalse();
    }

    @Test
    void rejectsArbitraryObjectKeys() {
        var storage = new CvStorageService(new InMemoryObjectStorage());
        assertThatThrownBy(() -> storage.read("../../secret"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
