package com.internplatform.profilephoto;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProfilePhotoStorageServiceTest {
    @TempDir Path directory;

    @Test
    void rejectsNonImageAndOverLimitUploads() {
        var storage = new ProfilePhotoStorageService(directory.toString());
        assertThatThrownBy(() -> storage.normalize("not an image".getBytes()))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> storage.normalize(new byte[ProfilePhotoStorageService.MAX_INPUT_BYTES + 1]))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void stripsMetadataResizesAndPreservesTransparency() throws Exception {
        var storage = new ProfilePhotoStorageService(directory.toString());
        var original = new BufferedImage(1200, 800, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = original.createGraphics();
        g.setColor(new Color(10, 80, 70, 120));
        g.fillRect(0, 0, 1200, 800);
        g.dispose();
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(original, "png", bytes);
        var normalized = ImageIO.read(new ByteArrayInputStream(storage.normalize(bytes.toByteArray())));
        assertThat(normalized.getWidth()).isEqualTo(640);
        assertThat(normalized.getHeight()).isEqualTo(427);
        assertThat(normalized.getColorModel().hasAlpha()).isTrue();
    }

    @Test
    void createsUniqueKeysAndSupportsCleanup() throws Exception {
        var storage = new ProfilePhotoStorageService(directory.toString());
        UUID profile = UUID.randomUUID();
        String first = storage.store(profile, new byte[]{1, 2, 3});
        String second = storage.store(profile, new byte[]{4, 5});
        assertThat(first).isNotEqualTo(second);
        assertThat(storage.read(first)).containsExactly(new byte[]{1, 2, 3});
        storage.delete(first);
        assertThatThrownBy(() -> storage.read(first)).isInstanceOf(ResponseStatusException.class);
        storage.deleteProfileDirectory(profile);
        assertThatThrownBy(() -> storage.read(second)).isInstanceOf(ResponseStatusException.class);
    }
}
