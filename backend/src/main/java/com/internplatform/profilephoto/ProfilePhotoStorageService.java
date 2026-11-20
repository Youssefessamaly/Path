package com.internplatform.profilephoto;

import com.internplatform.storage.ObjectStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.services.s3.model.S3Exception;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.UUID;

/** Normalizes profile pictures locally in memory, then persists objects privately in R2. */
@Service
public class ProfilePhotoStorageService {
    public static final int MAX_INPUT_BYTES = 5 * 1024 * 1024;
    private static final int MAX_EDGE = 640;
    private static final long MAX_PIXELS = 20_000_000;
    private static final String STUDENT_PREFIX = "students/profile-photos/";
    private static final String COMPANY_PREFIX = "companies/logos/";
    private static final String LEGACY_PREFIX = "legacy/profile-photos/";
    private static final Logger log = LoggerFactory.getLogger(ProfilePhotoStorageService.class);
    private final ObjectStorage objects;

    public ProfilePhotoStorageService(ObjectStorage objects) {
        this.objects = objects;
    }

    /** Accept only actual JPEG/PNG data; strip EXIF and resize before storing PNG. */
    public byte[] normalize(byte[] input) {
        if (input == null || input.length == 0 || input.length > MAX_INPUT_BYTES) {
            throw badImage();
        }
        boolean jpeg = input.length >= 3 && (input[0] & 255) == 255
                && (input[1] & 255) == 216 && (input[2] & 255) == 255;
        boolean png = input.length >= 8 && (input[0] & 255) == 137
                && input[1] == 80 && input[2] == 78 && input[3] == 71
                && input[4] == 13 && input[5] == 10 && (input[6] & 255) == 26 && input[7] == 10;
        if (!jpeg && !png) throw badImage();
        try (ImageInputStream stream = ImageIO.createImageInputStream(new ByteArrayInputStream(input))) {
            if (stream == null) throw badImage();
            Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) throw badImage();
            ImageReader reader = readers.next();
            try {
                reader.setInput(stream, true, true);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width < 1 || height < 1 || (long) width * height > MAX_PIXELS) throw badImage();
                BufferedImage source = reader.read(0);
                if (source == null) throw badImage();
                double scale = Math.min(1.0, (double) MAX_EDGE / Math.max(width, height));
                int outputWidth = Math.max(1, (int) Math.round(width * scale));
                int outputHeight = Math.max(1, (int) Math.round(height * scale));
                BufferedImage output = new BufferedImage(outputWidth, outputHeight, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = output.createGraphics();
                try {
                    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                    g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                    g.drawImage(source, 0, 0, outputWidth, outputHeight, null);
                } finally { g.dispose(); }
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                if (!ImageIO.write(output, "png", bytes)) throw badImage();
                return bytes.toByteArray();
            } finally { reader.dispose(); }
        } catch (IOException | IllegalArgumentException e) { throw badImage(); }
    }


    public String storeIntern(UUID profileId, byte[] png) {
        return store(STUDENT_PREFIX, profileId, png);
    }

    public String storeCompany(UUID profileId, byte[] png) {
        return store(COMPANY_PREFIX, profileId, png);
    }

    private String store(String prefix, UUID profileId, byte[] png) {
        String key = prefix + profileId + "/" + UUID.randomUUID() + ".png";
        try {
            objects.put(key, png, "image/png");
            return key;
        } catch (RuntimeException ex) {
            log.warn("Unable to upload profile image to R2", ex);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Image storage is currently unavailable");
        }
    }

    public byte[] read(String key) {
        try {
            return objects.get(validate(key));
        } catch (S3Exception ex) {
            if (ex.statusCode() == 404) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Photo not found");
            }
            log.warn("R2 could not retrieve an image (status={})", ex.statusCode());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Image storage is currently unavailable");
        } catch (RuntimeException ex) {
            log.warn("R2 could not retrieve an image", ex);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Image storage is currently unavailable");
        }
    }

    /** Best-effort removal, used after a successful profile update / on rollback. */
    public void delete(String key) {
        if (key == null) return;
        try {
            objects.delete(validate(key));
        } catch (RuntimeException ex) {
            log.warn("Unable to delete old image from private storage; cleanup may be needed");
        }
    }

    /** Strict cleanup for the existing retryable account-deletion queue. */
    public void deleteProfileDirectory(UUID profileId) throws IOException {
        objects.deletePrefix(STUDENT_PREFIX + profileId + "/");
        objects.deletePrefix(COMPANY_PREFIX + profileId + "/");
        objects.deletePrefix(LEGACY_PREFIX + profileId + "/");
    }

    private String validate(String key) {
        if (key == null) throw new IllegalArgumentException("Missing photo key");
        if (key.matches("[a-fA-F0-9-]{36}/[a-fA-F0-9-]{36}\\.png")) {
            String[] parts = key.split("/");
            UUID.fromString(parts[0]);
            UUID.fromString(parts[1].substring(0, 36));
            return LEGACY_PREFIX + key;
        }
        String prefix = key.startsWith(STUDENT_PREFIX) ? STUDENT_PREFIX
                : key.startsWith(COMPANY_PREFIX) ? COMPANY_PREFIX
                : key.startsWith(LEGACY_PREFIX) ? LEGACY_PREFIX : null;
        if (prefix == null) throw new IllegalArgumentException("Invalid photo key");
        String[] parts = key.substring(prefix.length()).split("/", -1);
        if (parts.length != 2 || parts[1].length() != 40 || !parts[1].endsWith(".png")) {
            throw new IllegalArgumentException("Invalid photo key");
        }
        UUID.fromString(parts[0]);
        UUID.fromString(parts[1].substring(0, 36));
        return key;
    }

    private ResponseStatusException badImage() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a JPEG or PNG image up to 5 MB (max 20 megapixels)");
    }
}
