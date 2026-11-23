package com.quiz.darkhold.init;

import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

public class FileUploadUtil {
    private static final Set<String> IMAGE_EXTENSIONS = Set.of("png", "jpg", "jpeg", "gif", "webp");

    private FileUploadUtil() {
    }

    /**
     * Detects a supported image format from the file's magic bytes and decodes non-WebP images with ImageIO.
     *
     * @return "png", "jpg", "gif", or "webp" if valid, or null otherwise
     */
    public static String detectImageExtension(final MultipartFile multipartFile) throws IOException {
        if (multipartFile == null || multipartFile.isEmpty()) {
            return null;
        }
        byte[] bytes = multipartFile.getBytes();
        String ext = matchImageMagic(bytes);
        if (ext == null) {
            return null;
        }
        if (!"webp".equals(ext) && ImageIO.read(new ByteArrayInputStream(bytes)) == null) {
            return null;
        }
        return ext;
    }

    private static String matchImageMagic(final byte[] bytes) {
        if (isPng(bytes)) {
            return "png";
        }
        if (isJpeg(bytes)) {
            return "jpg";
        }
        if (isGif(bytes)) {
            return "gif";
        }
        return isWebp(bytes) ? "webp" : null;
    }

    private static boolean isPng(final byte[] bytes) {
        return bytes.length >= 8 && (bytes[0] & 0xFF) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N'
                && bytes[3] == 'G' && bytes[4] == 0x0D && bytes[5] == 0x0A && bytes[6] == 0x1A && bytes[7] == 0x0A;
    }

    private static boolean isJpeg(final byte[] bytes) {
        return bytes.length >= 3 && (bytes[0] & 0xFF) == 0xFF
                && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF;
    }

    private static boolean isGif(final byte[] bytes) {
        return bytes.length >= 6 && bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F'
                && bytes[3] == '8' && (bytes[4] == '7' || bytes[4] == '9') && bytes[5] == 'a';
    }

    private static boolean isWebp(final byte[] bytes) {
        return bytes.length >= 12 && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P';
    }

    /** Drops any directory part of a client-supplied file name. */
    public static String baseName(final String name) {
        final Path fileName = Paths.get(name).getFileName();
        return fileName == null ? "" : fileName.toString();
    }

    /**
     * Client-supplied image name reduced to its base name.
     *
     * @throws IllegalArgumentException if it isn't a png/jpg/jpeg/gif/webp file
     */
    public static String imageFileName(final String original) {
        final String name = baseName(original == null ? "" : original);
        final int dot = name.lastIndexOf('.');
        if (dot < 1 || !IMAGE_EXTENSIONS.contains(name.substring(dot + 1).toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Only png, jpg, jpeg, gif or webp images are allowed");
        }
        return name;
    }

    public static void saveFile(final String uploadDir,
                                final String fileName,
                                final MultipartFile multipartFile) throws IOException {
        final Path uploadPath = Paths.get(uploadDir);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }
        try (final InputStream inputStream = multipartFile.getInputStream()) {
            final Path filePath = uploadPath.resolve(fileName).normalize();
            if (!filePath.startsWith(uploadPath.normalize())) {
                throw new IOException("File name escapes the upload directory");
            }
            Files.copy(inputStream, filePath, StandardCopyOption.REPLACE_EXISTING);
        } catch (final IOException ioe) {
            throw new IOException("Could not save image file: " + fileName, ioe);
        }
    }

    public static void cleanDir(final String dir) {
        try (Stream<Path> stream = Files.list(Paths.get(dir))) {
            stream.forEach(file -> {
                if (!Files.isDirectory(file)) {
                    try {
                        Files.delete(file);
                    } catch (final IOException ioe) {
                        System.out.println("Could not delete file: " + file);
                    }
                }
            });
        } catch (final IOException ioe) {
            System.out.println("Could not list directory: " + dir);
        }
    }

    public static void deleteDir(final String dir) {
        Path path = Paths.get(dir);
        if (!Files.exists(path)) {
            return;
        }
        cleanDir(dir);
        try {
            Files.deleteIfExists(path);
        } catch (final IOException ioe) {
            System.out.println("Could not delete directory: " + path);
        }
    }
}
