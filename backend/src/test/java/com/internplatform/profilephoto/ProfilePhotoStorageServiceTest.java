package com.internplatform.profilephoto;

import com.internplatform.storage.InMemoryObjectStorage;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProfilePhotoStorageServiceTest {
    @Test
    void rejectsNonImageAndOverLimitUploads() {
        var storage = new ProfilePhotoStorageService(new InMemoryObjectStorage());
        assertThatThrownBy(() -> storage.normalize("not an image".getBytes()))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> storage.normalize(new byte[ProfilePhotoStorageService.MAX_INPUT_BYTES + 1]))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void stripsMetadataResizesAndPreservesTransparency() throws Exception {
        var storage = new ProfilePhotoStorageService(new InMemoryObjectStorage());
        var original = new BufferedImage(1200, 800, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = original.createGraphics();
        graphics.setColor(new Color(10, 80, 70, 120));
        graphics.fillRect(0, 0, 1200, 800);
        graphics.dispose();
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(original, "png", bytes);
        var normalized = ImageIO.read(new ByteArrayInputStream(storage.normalize(bytes.toByteArray())));
        assertThat(normalized.getWidth()).isEqualTo(640);
        assertThat(normalized.getHeight()).isEqualTo(427);
        assertThat(normalized.getColorModel().hasAlpha()).isTrue();
    }

    @Test
    void createsUniqueKeysForStudentAndCompanyAndSupportsCleanup() throws Exception {
        var objects = new InMemoryObjectStorage();
        var storage = new ProfilePhotoStorageService(objects);
        UUID profile = UUID.randomUUID();
        String first = storage.storeIntern(profile, new byte[]{1, 2, 3});
        String second = storage.storeCompany(profile, new byte[]{4, 5});
        assertThat(first).startsWith("students/profile-photos/");
        assertThat(second).startsWith("companies/logos/");
        assertThat(storage.read(first)).containsExactly((byte) 1, (byte) 2, (byte) 3);
        storage.delete(first);
        assertThatThrownBy(() -> storage.read(first)).isInstanceOf(ResponseStatusException.class);
        storage.deleteProfileDirectory(profile);
        assertThat(objects.contains(second)).isFalse();
    }

    @Test
    void legacyStorageKeysRemainReadableAfterImport() {
        var objects = new InMemoryObjectStorage();
        var storage = new ProfilePhotoStorageService(objects);
        String oldKey = UUID.randomUUID() + "/" + UUID.randomUUID() + ".png";
        objects.put("legacy/profile-photos/" + oldKey, new byte[]{7}, "image/png");
        assertThat(storage.read(oldKey)).containsExactly((byte) 7);
        storage.delete(oldKey);
        assertThat(objects.contains("legacy/profile-photos/" + oldKey)).isFalse();
    }
}
