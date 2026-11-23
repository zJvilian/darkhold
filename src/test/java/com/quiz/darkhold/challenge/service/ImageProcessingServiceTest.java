package com.quiz.darkhold.challenge.service;

import com.quiz.darkhold.challenge.entity.QuestionType;
import com.quiz.darkhold.challenge.exception.ImageValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("ImageProcessingService Tests")
class ImageProcessingServiceTest {

    @TempDir
    private Path tempDir;

    @Test
    @DisplayName("Should never delete a file outside the media directory")
    void shouldRefusePathTraversal() throws IOException {
        Path mediaDir = Files.createDirectories(tempDir.resolve("question-images"));
        Path victim = Files.writeString(tempDir.resolve("victim.txt"), "keep me");
        ImageProcessingService service = serviceWithMediaDir(mediaDir);

        service.deleteQuestionImage("/question-images/../victim.txt");
        service.deleteQuestionImage("../../victim.txt");

        assertTrue(Files.exists(victim), "traversal must not delete the file");
    }

    @Test
    @DisplayName("Should delete an image that lives under the media directory")
    void shouldDeleteOwnImage() throws IOException {
        Path mediaDir = tempDir.resolve("question-images");
        Path image = Files.createDirectories(mediaDir.resolve("1/2")).resolve("image_1.png");
        Files.writeString(image, "png");
        ImageProcessingService service = serviceWithMediaDir(mediaDir);

        service.deleteQuestionImage("/question-images/1/2/image_1.png");

        assertFalse(Files.exists(image), "the image should be deleted");
    }

    @Test
    @DisplayName("Should save valid mp3 and general audio files and restrict supported question types")
    void shouldSaveAudioFilesAndCheckSupportedTypes() {
        ImageProcessingService service = serviceWithMediaDir(tempDir.resolve("question-images"));
        var mp3 = new MockMultipartFile("image", "clip.mp3", "audio/mpeg", latin1("ID3\u0004\0\0"));
        var ogg = new MockMultipartFile("image", "fx.ogg", "audio/ogg", latin1("OggS\0\2"));
        assertTrue(service.saveQuestionImage(1L, 2L, mp3).matches("/question-images/1/2/audio_\\d+\\.mp3"));
        assertTrue(service.saveQuestionImage(1L, 3L, ogg).endsWith(".ogg"));
        var bad = new MockMultipartFile("image", "bad.exe", "audio/mpeg", "x".getBytes(StandardCharsets.UTF_8));
        assertThrows(ImageValidationException.class, () -> service.saveQuestionImage(1L, 4L, bad));
        assertTrue(ImageProcessingService.supportsAudio(QuestionType.MULTIPLE_CHOICE)
                && ImageProcessingService.supportsAudio(QuestionType.TRUE_FALSE)
                && ImageProcessingService.supportsAudio(QuestionType.TYPE_ANSWER));
        assertFalse(ImageProcessingService.supportsAudio(QuestionType.POLL)
                || ImageProcessingService.supportsAudio(QuestionType.SORTING)
                || ImageProcessingService.supportsAudio(QuestionType.WORD_CLOUD));
    }

    @Test
    @DisplayName("Should refuse a file that only claims to be audio")
    void shouldRefuseDisguisedAudio() {
        ImageProcessingService service = serviceWithMediaDir(tempDir.resolve("question-images"));
        var script = new MockMultipartFile("image", "evil.mp3", "audio/mpeg",
                "<?php echo 1; ?>".getBytes(StandardCharsets.UTF_8));
        assertThrows(ImageValidationException.class, () -> service.validateImage(script));
    }

    @Test
    @DisplayName("Should accept the audio containers by their signatures")
    void shouldAcceptAudioSignatures() {
        ImageProcessingService service = serviceWithMediaDir(tempDir.resolve("question-images"));
        byte[][] signatures = {
            {(byte) 0xFF, (byte) 0xFB, 0x10, 0x00},
            {(byte) 0xFF, (byte) 0xF3, 0x10, 0x00},
            latin1("RIFF\0\0\0\0WAVEfmt "),
            latin1("\0\0\0\u0018ftypM4A "),
            {0x1A, 0x45, (byte) 0xDF, (byte) 0xA3, 0x01},
            latin1("fLaC\0\0\0\0")};
        for (byte[] bytes : signatures) {
            var file = new MockMultipartFile("image", "clip.mp3", "audio/mpeg", bytes);
            service.validateImage(file);
        }
    }

    @Test
    @DisplayName("Should keep PNG transparency and format when shrinking")
    void shouldKeepPngTransparency() throws IOException {
        ImageProcessingService service = serviceWithMediaDir(tempDir.resolve("question-images"));
        ReflectionTestUtils.setField(service, "maxImageWidth", 10);
        ReflectionTestUtils.setField(service, "maxImageHeight", 10);
        var image = new java.awt.image.BufferedImage(40, 40, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        var png = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, "png", png);

        byte[] resized = service.resizeAndCompressImage(
                new MockMultipartFile("image", "logo.png", "image/png", png.toByteArray()));

        var result = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(resized));
        assertEquals(10, result.getWidth());
        assertTrue(result.getColorModel().hasAlpha(), "transparency must survive");
        assertEquals(0x89, resized[0] & 0xFF, "still PNG bytes, not JPEG");
    }

    @Test
    @DisplayName("Should leave a GIF untouched so it keeps animating")
    void shouldNotReencodeGif() throws IOException {
        ImageProcessingService service = serviceWithMediaDir(tempDir.resolve("question-images"));
        ReflectionTestUtils.setField(service, "maxImageWidth", 10);
        ReflectionTestUtils.setField(service, "maxImageHeight", 10);
        var gif = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(40, 40,
                java.awt.image.BufferedImage.TYPE_INT_RGB), "gif", gif);

        byte[] result = service.resizeAndCompressImage(
                new MockMultipartFile("image", "anim.gif", "image/gif", gif.toByteArray()));

        org.junit.jupiter.api.Assertions.assertArrayEquals(gif.toByteArray(), result);
    }

    @Test
    @DisplayName("Should clone a local media file for a copy and leave other URLs alone")
    void shouldCopyLocalMedia() throws IOException {
        Path mediaDir = tempDir.resolve("question-images");
        Path original = Files.createDirectories(mediaDir.resolve("1/2")).resolve("image_1.png");
        Files.writeString(original, "png");
        ImageProcessingService service = serviceWithMediaDir(mediaDir);

        String copy = service.copyMedia("/question-images/1/2/image_1.png", 9L);

        assertTrue(copy.matches("/question-images/9/[0-9a-f-]{36}/image_1\\.png"));
        assertEquals("png", Files.readString(mediaDir.resolve(copy.substring("/question-images/".length()))));
        service.deleteQuestionImage("/question-images/1/2/image_1.png");
        assertTrue(Files.exists(mediaDir.resolve(copy.substring("/question-images/".length()))),
                "deleting the original must not break the copy");
    }

    @Test
    @DisplayName("Should not clone external, missing or out-of-tree media")
    void shouldNotCopyOtherUrls() {
        ImageProcessingService service = serviceWithMediaDir(tempDir.resolve("question-images"));

        assertEquals("https://example.com/a.png", service.copyMedia("https://example.com/a.png", 9L));
        assertNull(service.copyMedia("/question-images/1/2/missing.png", 9L));
        assertNull(service.copyMedia("/question-images/../../etc/passwd", 9L));
        assertNull(service.copyMedia(null, 9L));
    }

    private static byte[] latin1(final String text) {
        return text.getBytes(StandardCharsets.ISO_8859_1);
    }

    private ImageProcessingService serviceWithMediaDir(final Path mediaDir) {
        ImageProcessingService service = new ImageProcessingService();
        ReflectionTestUtils.setField(service, "questionImagesDir", mediaDir.toString());
        return service;
    }
}
