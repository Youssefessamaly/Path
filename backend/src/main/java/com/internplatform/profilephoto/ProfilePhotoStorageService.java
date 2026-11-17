package com.internplatform.profilephoto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.FileVisitResult;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Iterator;
import java.util.UUID;

/** Local, private, normalized profile images (not user-supplied filenames). */
@Service
public class ProfilePhotoStorageService {
    public static final int MAX_INPUT_BYTES = 5 * 1024 * 1024;
    private static final int MAX_EDGE = 640;
    private static final long MAX_PIXELS = 20_000_000;
    private final Path root;

    public ProfilePhotoStorageService(@Value("${app.photo-storage.directory:./data/profile-photos}") String directory) {
        root = Path.of(directory).toAbsolutePath().normalize();
        try { Files.createDirectories(root); }
        catch (IOException e) { throw new IllegalStateException("Unable to create photo storage directory", e); }
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

    public String store(UUID profileId, byte[] png) {
        String key = profileId + "/" + UUID.randomUUID() + ".png";
        Path target = resolved(key);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, png, StandardOpenOption.CREATE_NEW);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to store photo");
        }
        return key;
    }

    public byte[] read(String key) {
        try { return Files.readAllBytes(resolved(key)); }
        catch (IOException e) { throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Photo not found"); }
    }

    public void delete(String key) {
        if (key == null) return;
        try { Files.deleteIfExists(resolved(key)); }
        catch (IOException ignored) { /* A delayed cleanup can remove an orphaned file. */ }
    }

    /** Retryable account deletion: deletes only this profile's generated image directory. */
    public void deleteProfileDirectory(UUID profileId) throws IOException {
        Path directory = root.resolve(profileId.toString()).normalize();
        if (!directory.startsWith(root) || !Files.exists(directory)) return;
        Files.walkFileTree(directory, new SimpleFileVisitor<>() {
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult postVisitDirectory(Path dir, IOException error) throws IOException {
                if (error != null) throw error;
                Files.deleteIfExists(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private Path resolved(String key) {
        String[] segments = key.split("/", -1);
        if (segments.length != 2 || !segments[1].endsWith(".png")) throw new IllegalArgumentException("Invalid photo key");
        UUID.fromString(segments[0]);
        UUID.fromString(segments[1].substring(0, segments[1].length() - 4));
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root)) throw new IllegalArgumentException("Invalid photo key");
        return path;
    }

    private ResponseStatusException badImage() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a JPEG or PNG image up to 5 MB (max 20 megapixels)");
    }
}
