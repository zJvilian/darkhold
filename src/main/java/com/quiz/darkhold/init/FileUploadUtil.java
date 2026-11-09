package com.quiz.darkhold.init;

import org.springframework.web.multipart.MultipartFile;

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
}
