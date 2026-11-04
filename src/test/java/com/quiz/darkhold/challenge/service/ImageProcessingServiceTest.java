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

import static org.junit.jupiter.api.Assertions.assertFalse;
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
        var mp3 = new MockMultipartFile("image", "clip.mp3", "audio/mpeg", "ID3".getBytes(StandardCharsets.UTF_8));
        var ogg = new MockMultipartFile("image", "fx.ogg", "audio/ogg", "OggS".getBytes(StandardCharsets.UTF_8));
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

    private ImageProcessingService serviceWithMediaDir(final Path mediaDir) {
        ImageProcessingService service = new ImageProcessingService();
        ReflectionTestUtils.setField(service, "questionImagesDir", mediaDir.toString());
        return service;
    }
}
