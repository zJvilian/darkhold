package com.quiz.darkhold.challenge.service;

import com.quiz.darkhold.challenge.dto.ChallengeExportDto;
import com.quiz.darkhold.challenge.entity.Challenge;
import com.quiz.darkhold.challenge.entity.QuestionSet;
import com.quiz.darkhold.challenge.entity.QuestionType;
import com.quiz.darkhold.challenge.exception.ChallengeException;
import com.quiz.darkhold.challenge.repository.ChallengeRepository;
import com.quiz.darkhold.challenge.repository.QuestionSetRepository;
import com.quiz.darkhold.user.entity.DarkholdUserDetails;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith({SpringExtension.class, MockitoExtension.class})
@DisplayName("ChallengeService Tests")
class ChallengeServiceTest {

    @Mock
    private ChallengeRepository challengeRepository;

    @Mock
    private QuestionSetRepository questionSetRepository;

    @Mock
    private SecurityContext securityContext;

    @Mock
    private Authentication authentication;

    @Mock
    private DarkholdUserDetails darkholdUserDetails;

    private ChallengeService challengeService;
    private Long testUserId = 1L;

    @BeforeEach
    void setUp() {
        challengeService = new ChallengeService(challengeRepository, questionSetRepository,
                new ImageProcessingService());
        setupSecurityContext();
    }

    private void setupSecurityContext() {
        var mockUser = mock(com.quiz.darkhold.user.entity.User.class);
        lenient().when(mockUser.getId()).thenReturn(testUserId);
        lenient().when(darkholdUserDetails.getUser()).thenReturn(mockUser);
        lenient().when(authentication.getPrincipal()).thenReturn(darkholdUserDetails);
        lenient().when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);
    }

    @Test
    void spreadsheetImportReadsAnswersThroughJWithoutMovingExistingColumns() throws Exception {
        byte[] data;
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var row = workbook.createSheet().createRow(0);
            row.createCell(0).setCellValue("Sort ten");
            for (int i = 1; i <= 10; i++) {
                row.createCell(i <= 4 ? i : i + 7).setCellValue("Answer " + i);
            }
            row.createCell(5).setCellValue("J,I,H,G,F,E,D,C,B,A");
            row.createCell(6).setCellValue("SORTING");
            workbook.write(output);
            data = output.toByteArray();
        }
        when(challengeRepository.save(any(Challenge.class))).thenAnswer(inv -> inv.getArgument(0));
        challengeService.readProcessAndSaveChallenge(
                new MockMultipartFile("upload", "sorting.xlsx", "application/octet-stream", data), "Sorting", "");
        var captured = ArgumentCaptor.forClass(Challenge.class);
        verify(challengeRepository).save(captured.capture());
        QuestionSet question = captured.getValue().getQuestionSets().getFirst();
        for (int i = 1; i <= 10; i++) {
            assertEquals("Answer " + i, question.getSortingAnswers()[i - 1]);
        }
        assertEquals("J,I,H,G,F,E,D,C,B,A", question.getCorrectOptions());
    }

    @Test
    void jsonImportRetainsAdditionalSortingAnswers() {
        var question = new QuestionSet();
        question.setQuestionType(QuestionType.SORTING);
        question.setAnswer1("First");
        question.setAnswer10("Tenth");
        question.setCorrectOptions("J,A");
        var dto = new ChallengeExportDto();
        dto.setQuestions(List.of(ChallengeExportDto.QuestionExportDto.fromEntity(question)));
        when(challengeRepository.save(any(Challenge.class))).thenAnswer(inv -> inv.getArgument(0));
        when(questionSetRepository.saveAll(any())).thenAnswer(inv -> {
            Iterable<QuestionSet> saved = inv.getArgument(0);
            var imported = saved.iterator().next();
            org.junit.jupiter.api.Assertions.assertArrayEquals(
                    question.getSortingAnswers(), imported.getSortingAnswers());
            assertEquals("J,A", imported.getCorrectOptions());
            return List.of(imported);
        });
        challengeService.importFromJson(dto);
        verify(questionSetRepository).saveAll(any());
    }

    @Nested
    @DisplayName("readProcessAndSaveChallenge tests")
    class ReadProcessAndSaveChallengeTests {

        private MultipartFile validExcelFile;
        private String testTitle;
        private String testDescription;

        @BeforeEach
        void setUp() throws IOException {
            testTitle = "Test Challenge";
            testDescription = "Test Description";
            validExcelFile = createValidExcelFile();
        }

        @Test
        @DisplayName("Should successfully read, process and save challenge")
        void shouldSuccessfullyReadProcessAndSaveChallenge() throws ChallengeException {
            Challenge savedChallenge = new Challenge();
            savedChallenge.setId(100L);
            when(challengeRepository.save(any(Challenge.class))).thenReturn(savedChallenge);

            Long result = challengeService.readProcessAndSaveChallenge(validExcelFile, testTitle, testDescription);

            assertEquals(100L, result);

            ArgumentCaptor<Challenge> challengeCaptor = ArgumentCaptor.forClass(Challenge.class);
            verify(challengeRepository).save(challengeCaptor.capture());
            Challenge savedChallengeArg = challengeCaptor.getValue();

            assertEquals(testTitle, savedChallengeArg.getTitle());
            assertEquals(testDescription, savedChallengeArg.getDescription());
            assertEquals(testUserId, savedChallengeArg.getChallengeOwner());

            // Verify question sets were saved
            verify(questionSetRepository).saveAll(any());
        }

        @Test
        @DisplayName("Should throw ChallengeException for invalid Excel file")
        void shouldThrowChallengeExceptionForInvalidExcelFile() {
            MultipartFile invalidFile = new MockMultipartFile("file", "test.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    "invalid data".getBytes(UTF_8));

            assertThrows(ChallengeException.class, () ->
                    challengeService.readProcessAndSaveChallenge(invalidFile, testTitle, testDescription)
            );
        }

        @Test
        @DisplayName("Should populate question sets from Excel")
        void shouldPopulateQuestionSetsFromExcel() throws ChallengeException {
            Challenge savedChallenge = new Challenge();
            savedChallenge.setId(200L);
            when(challengeRepository.save(any(Challenge.class))).thenReturn(savedChallenge);

            challengeService.readProcessAndSaveChallenge(validExcelFile, testTitle, testDescription);

            // Verify question sets were saved
            verify(questionSetRepository).saveAll(any());
        }

        @Test
        @DisplayName("Should set challenge owner to current user ID")
        void shouldSetChallengeOwnerToCurrentUserId() throws ChallengeException {
            Challenge savedChallenge = new Challenge();
            savedChallenge.setId(300L);
            when(challengeRepository.save(any(Challenge.class))).thenReturn(savedChallenge);

            challengeService.readProcessAndSaveChallenge(validExcelFile, testTitle, testDescription);

            ArgumentCaptor<Challenge> captor = ArgumentCaptor.forClass(Challenge.class);
            verify(challengeRepository).save(captor.capture());
            Challenge savedChallengeArg = captor.getValue();
            assertEquals(testUserId, savedChallengeArg.getChallengeOwner());
        }

        @Test
        @DisplayName("Should throw ChallengeException when Excel parsing fails")
        void shouldThrowChallengeExceptionWhenExcelParsingFails() {
            MultipartFile malformedFile = new MockMultipartFile("file", "test.txt",
                    "text/plain", "not an excel file".getBytes(UTF_8));

            assertThrows(ChallengeException.class, () ->
                    challengeService.readProcessAndSaveChallenge(malformedFile, testTitle, testDescription)
            );
        }

        @ParameterizedTest
        @ValueSource(strings = {"Challenge with <special> chars", "Challenge & Co.", "Challenge 123", ""})
        @DisplayName("Should handle various challenge titles")
        void shouldHandleVariousChallengeeTitles(final String title) throws ChallengeException {
            Challenge savedChallenge = new Challenge();
            savedChallenge.setId(600L);
            when(challengeRepository.save(any(Challenge.class))).thenReturn(savedChallenge);

            Long result = challengeService.readProcessAndSaveChallenge(validExcelFile, title, testDescription);

            assertEquals(600L, result);

            ArgumentCaptor<Challenge> captor = ArgumentCaptor.forClass(Challenge.class);
            verify(challengeRepository).save(captor.capture());
            assertEquals(title, captor.getValue().getTitle());
        }

        @Test
        @DisplayName("Should handle empty Excel file")
        void shouldHandleEmptyExcelFile() throws IOException, ChallengeException {
            MultipartFile emptyExcelFile = createEmptyExcelFile();
            Challenge savedChallenge = new Challenge();
            savedChallenge.setId(500L);
            when(challengeRepository.save(any(Challenge.class))).thenReturn(savedChallenge);

            Long result = challengeService.readProcessAndSaveChallenge(emptyExcelFile, testTitle, testDescription);

            assertEquals(500L, result);
            verify(challengeRepository).save(any(Challenge.class));
        }
    }

    @Nested
    @DisplayName("deleteChallenge tests")
    class DeleteChallengeTests {

        @Test
        @DisplayName("Should successfully delete existing challenge")
        void shouldSuccessfullyDeleteExistingChallenge() {
            Long challengeId = 1L;
            Challenge challenge = new Challenge();
            challenge.setId(challengeId);
            challenge.setChallengeOwner(testUserId);
            challenge.setQuestionSets(new ArrayList<>());

            when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(challenge));

            Boolean result = challengeService.deleteChallenge(challengeId);

            assertTrue(result);
            verify(challengeRepository).findById(challengeId);
            verify(questionSetRepository).deleteAll(challenge.getQuestionSets());
            verify(challengeRepository).delete(challenge);
        }

        @Test
        @DisplayName("Should refuse to delete a challenge owned by another user")
        void shouldRefuseToDeleteAnotherUsersChallenge() {
            Long challengeId = 4L;
            Challenge challenge = new Challenge();
            challenge.setId(challengeId);
            challenge.setChallengeOwner(testUserId + 1);
            challenge.setQuestionSets(new ArrayList<>());

            when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(challenge));

            assertEquals(false, challengeService.deleteChallenge(challengeId));
            verify(questionSetRepository, never()).deleteAll(any());
            verify(challengeRepository, never()).delete(any());
        }

        @Test
        @DisplayName("Should return false when challenge not found")
        void shouldReturnFalseWhenChallengeNotFound() {
            Long challengeId = 999L;
            when(challengeRepository.findById(challengeId)).thenReturn(Optional.empty());

            Boolean result = challengeService.deleteChallenge(challengeId);

            assertEquals(false, result);
            verify(challengeRepository).findById(challengeId);
            verify(questionSetRepository, never()).deleteAll(any());
            verify(challengeRepository, never()).delete(any());
        }

        @Test
        @DisplayName("Should delete associated question sets before deleting challenge")
        void shouldDeleteAssociatedQuestionSetsBeforeDeletingChallenge() {
            Long challengeId = 2L;
            Challenge challenge = new Challenge();
            challenge.setId(challengeId);
            challenge.setChallengeOwner(testUserId);

            QuestionSet qs1 = new QuestionSet();
            QuestionSet qs2 = new QuestionSet();
            List<QuestionSet> questionSets = new ArrayList<>();
            questionSets.add(qs1);
            questionSets.add(qs2);
            challenge.setQuestionSets(questionSets);

            when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(challenge));

            challengeService.deleteChallenge(challengeId);

            verify(questionSetRepository).deleteAll(questionSets);
            verify(challengeRepository).delete(challenge);
        }

        @ParameterizedTest
        @ValueSource(longs = {0L, -1L, 999999L, Long.MAX_VALUE})
        @DisplayName("Should handle various challenge IDs")
        void shouldHandleVariousChallengeIds(final Long challengeId) {
            when(challengeRepository.findById(challengeId)).thenReturn(Optional.empty());

            Boolean result = challengeService.deleteChallenge(challengeId);

            assertEquals(false, result);
            verify(challengeRepository).findById(challengeId);
        }

        @Test
        @DisplayName("Should successfully delete challenge with multiple question sets")
        void shouldSuccessfullyDeleteChallengeWithMultipleQuestionSets() {
            Long challengeId = 3L;
            Challenge challenge = new Challenge();
            challenge.setId(challengeId);
            challenge.setChallengeOwner(testUserId);

            List<QuestionSet> questionSets = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                QuestionSet qs = new QuestionSet();
                qs.setId((long) i);
                questionSets.add(qs);
            }
            challenge.setQuestionSets(questionSets);

            when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(challenge));

            Boolean result = challengeService.deleteChallenge(challengeId);

            assertTrue(result);
            verify(questionSetRepository).deleteAll(questionSets);
            verify(challengeRepository).delete(challenge);
        }
    }

    @Nested
    @DisplayName("createEmptyChallenge tests")
    class CreateEmptyChallengeTests {

        @Test
        @DisplayName("Should create challenge with title and description")
        void shouldCreateChallengeWithTitleAndDescription() {
            Challenge savedChallenge = new Challenge();
            savedChallenge.setId(10L);
            when(challengeRepository.save(any(Challenge.class))).thenReturn(savedChallenge);

            Challenge result = challengeService.createEmptyChallenge("New Quiz", "A new quiz");

            ArgumentCaptor<Challenge> captor = ArgumentCaptor.forClass(Challenge.class);
            verify(challengeRepository).save(captor.capture());
            assertEquals("New Quiz", captor.getValue().getTitle());
            assertEquals("A new quiz", captor.getValue().getDescription());
        }

        @Test
        @DisplayName("Should set owner to current user")
        void shouldSetOwnerToCurrentUser() {
            when(challengeRepository.save(any(Challenge.class))).thenAnswer(inv -> inv.getArgument(0));

            challengeService.createEmptyChallenge("Quiz", "Desc");

            ArgumentCaptor<Challenge> captor = ArgumentCaptor.forClass(Challenge.class);
            verify(challengeRepository).save(captor.capture());
            assertEquals(testUserId, captor.getValue().getChallengeOwner());
        }
    }

    @Nested
    @DisplayName("updateChallenge tests")
    class UpdateChallengeTests {

        @Test
        @DisplayName("Should update title and description")
        void shouldUpdateTitleAndDescription() {
            Challenge existing = new Challenge();
            existing.setId(1L);
            existing.setTitle("Old Title");
            existing.setDescription("Old Desc");
            existing.setChallengeOwner(testUserId);
            when(challengeRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(challengeRepository.save(any(Challenge.class))).thenAnswer(inv -> inv.getArgument(0));

            Challenge result = challengeService.updateChallenge(1L, "New Title", "New Desc");

            assertNotNull(result);
            assertEquals("New Title", result.getTitle());
            assertEquals("New Desc", result.getDescription());
        }

        @Test
        @DisplayName("Should return null when not found")
        void shouldReturnNullWhenNotFound() {
            when(challengeRepository.findById(999L)).thenReturn(Optional.empty());

            Challenge result = challengeService.updateChallenge(999L, "Title", "Desc");

            assertNull(result);
        }

        @Test
        @DisplayName("Should return null when not owned by current user")
        void shouldReturnNullWhenNotOwnedByCurrentUser() {
            Challenge existing = new Challenge();
            existing.setId(1L);
            existing.setChallengeOwner(999L);
            when(challengeRepository.findById(1L)).thenReturn(Optional.of(existing));

            Challenge result = challengeService.updateChallenge(1L, "Title", "Desc");

            assertNull(result);
            verify(challengeRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("manager edit permissions")
    class ManagerEditTests {

        private Challenge othersChallenge() {
            Challenge challenge = new Challenge();
            challenge.setId(7L);
            challenge.setChallengeOwner(testUserId + 100);
            challenge.setQuestionSets(new ArrayList<>());
            when(challengeRepository.findById(7L)).thenReturn(Optional.of(challenge));
            return challenge;
        }

        private void signInWithRole(final String roleName) {
            var user = new com.quiz.darkhold.user.entity.User();
            user.setId(testUserId);
            user.addRole(new com.quiz.darkhold.user.entity.Role(1L, roleName));
            when(darkholdUserDetails.getUser()).thenReturn(user);
        }

        @ParameterizedTest
        @ValueSource(strings = {"ADMIN", "GAME_MANAGER"})
        @DisplayName("Managers can get and update another user's challenge")
        void managersCanEdit(final String role) {
            signInWithRole(role);
            othersChallenge();
            when(challengeRepository.save(any(Challenge.class))).thenAnswer(inv -> inv.getArgument(0));

            assertNotNull(challengeService.getChallengeForEdit(7L));
            assertEquals("T", challengeService.updateChallenge(7L, "T", "D").getTitle());
        }

        @Test
        @DisplayName("Plain user cannot get or update another user's challenge")
        void plainUserCannotEdit() {
            signInWithRole("USER");
            othersChallenge();

            assertNull(challengeService.getChallengeForEdit(7L));
            assertNull(challengeService.updateChallenge(7L, "T", "D"));
        }

        @Test
        @DisplayName("ADMIN still cannot delete another user's challenge")
        void adminCannotDelete() {
            signInWithRole("ADMIN");
            othersChallenge();

            assertEquals(false, challengeService.deleteChallenge(7L));
            verify(challengeRepository, never()).delete(any());
        }
    }

    @Nested
    @DisplayName("duplicateChallenge tests")
    class DuplicateChallengeTests {

        @Test
        @DisplayName("Should create copy with Copy suffix")
        void shouldCreateCopyWithCopySuffix() {
            Challenge original = createChallengeWithQuestions(1L, testUserId);
            when(challengeRepository.findById(1L)).thenReturn(Optional.of(original));
            when(challengeRepository.save(any(Challenge.class))).thenAnswer(inv -> inv.getArgument(0));

            Challenge result = challengeService.duplicateChallenge(1L);

            assertNotNull(result);
            assertEquals("Test Challenge (Copy)", result.getTitle());
        }

        @Test
        @DisplayName("Should deep copy questions")
        void shouldDeepCopyQuestions() {
            Challenge original = createChallengeWithQuestions(1L, testUserId);
            when(challengeRepository.findById(1L)).thenReturn(Optional.of(original));
            when(challengeRepository.save(any(Challenge.class))).thenAnswer(inv -> inv.getArgument(0));

            challengeService.duplicateChallenge(1L);

            verify(questionSetRepository).saveAll(any());
        }

        @Test
        @DisplayName("Should return null when not found")
        void shouldReturnNullWhenNotFound() {
            when(challengeRepository.findById(999L)).thenReturn(Optional.empty());

            Challenge result = challengeService.duplicateChallenge(999L);

            assertNull(result);
        }

        @Test
        @DisplayName("Should return null when not owned")
        void shouldReturnNullWhenNotOwned() {
            Challenge original = createChallengeWithQuestions(1L, 999L);
            when(challengeRepository.findById(1L)).thenReturn(Optional.of(original));

            Challenge result = challengeService.duplicateChallenge(1L);

            assertNull(result);
        }
    }

    @Nested
    @DisplayName("getChallengeForEdit tests")
    class GetChallengeForEditTests {

        @Test
        @DisplayName("Should return challenge when owned")
        void shouldReturnChallengeWhenOwned() {
            Challenge challenge = new Challenge();
            challenge.setId(1L);
            challenge.setChallengeOwner(testUserId);
            when(challengeRepository.findById(1L)).thenReturn(Optional.of(challenge));

            Challenge result = challengeService.getChallengeForEdit(1L);

            assertNotNull(result);
            assertEquals(1L, result.getId());
        }

        @Test
        @DisplayName("Should return null when not found")
        void shouldReturnNullWhenNotFound() {
            when(challengeRepository.findById(999L)).thenReturn(Optional.empty());

            Challenge result = challengeService.getChallengeForEdit(999L);

            assertNull(result);
        }

        @Test
        @DisplayName("Should return null when owned by other user")
        void shouldReturnNullWhenOwnedByOtherUser() {
            Challenge challenge = new Challenge();
            challenge.setId(1L);
            challenge.setChallengeOwner(999L);
            when(challengeRepository.findById(1L)).thenReturn(Optional.of(challenge));

            Challenge result = challengeService.getChallengeForEdit(1L);

            assertNull(result);
        }
    }

    @Nested
    @DisplayName("importFromJson tests")
    class ImportFromJsonTests {

        @Test
        @DisplayName("Should create challenge from DTO")
        void shouldCreateChallengeFromDto() {
            ChallengeExportDto dto = new ChallengeExportDto();
            dto.setTitle("Imported Quiz");
            dto.setDescription("Imported Description");
            when(challengeRepository.save(any(Challenge.class))).thenAnswer(inv -> inv.getArgument(0));

            Challenge result = challengeService.importFromJson(dto);

            assertNotNull(result);
            ArgumentCaptor<Challenge> captor = ArgumentCaptor.forClass(Challenge.class);
            verify(challengeRepository).save(captor.capture());
            assertEquals("Imported Quiz", captor.getValue().getTitle());
        }

        @Test
        @DisplayName("Should import questions with display order")
        void shouldImportQuestionsWithDisplayOrder() {
            ChallengeExportDto dto = new ChallengeExportDto();
            dto.setTitle("Quiz");
            dto.setDescription("Desc");
            ChallengeExportDto.QuestionExportDto questionDto = new ChallengeExportDto.QuestionExportDto();
            questionDto.setQuestion("Q1");
            questionDto.setAnswer1("A1");
            questionDto.setAnswer2("A2");
            dto.setQuestions(List.of(questionDto));
            when(challengeRepository.save(any(Challenge.class))).thenAnswer(inv -> inv.getArgument(0));

            challengeService.importFromJson(dto);

            verify(questionSetRepository).saveAll(any());
        }

        @Test
        @DisplayName("Should handle null questions in DTO")
        void shouldHandleNullQuestionsInDto() {
            ChallengeExportDto dto = new ChallengeExportDto();
            dto.setTitle("Quiz");
            dto.setDescription("Desc");
            dto.setQuestions(null);
            when(challengeRepository.save(any(Challenge.class))).thenAnswer(inv -> inv.getArgument(0));

            Challenge result = challengeService.importFromJson(dto);

            assertNotNull(result);
            verify(questionSetRepository, never()).saveAll(any());
        }
    }

    // Helper methods

    private Challenge createChallengeWithQuestions(final Long id, final Long ownerId) {
        Challenge challenge = new Challenge();
        challenge.setId(id);
        challenge.setTitle("Test Challenge");
        challenge.setDescription("Test Desc");
        challenge.setChallengeOwner(ownerId);
        QuestionSet qs = new QuestionSet();
        qs.setQuestion("Q1");
        qs.setAnswer1("A1");
        qs.setAnswer2("A2");
        qs.setCorrectOptions("A");
        qs.setQuestionType(QuestionType.MULTIPLE_CHOICE);
        challenge.setQuestionSets(List.of(qs));
        return challenge;
    }

    private MultipartFile createValidExcelFile() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet();
            Row row = sheet.createRow(0);

            row.createCell(0).setCellValue("Question 1");
            row.createCell(1).setCellValue("Answer 1");
            row.createCell(2).setCellValue("Answer 2");
            row.createCell(3).setCellValue("Answer 3");
            row.createCell(4).setCellValue("Answer 4");
            row.createCell(5).setCellValue("A");

            workbook.write(baos);
        }
        byte[] bytes = baos.toByteArray();
        return new MockMultipartFile("file", "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                bytes);
    }

    private MultipartFile createEmptyExcelFile() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            workbook.createSheet();
            workbook.write(baos);
        }
        byte[] bytes = baos.toByteArray();
        return new MockMultipartFile("file", "empty.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                bytes);
    }

    @Test
    void coverImageCannotBeSetOnSomeoneElsesQuiz() {
        Challenge foreign = new Challenge();
        foreign.setId(9L);
        foreign.setChallengeOwner(2L);
        when(challengeRepository.findById(9L)).thenReturn(Optional.of(foreign));
        var image = new MockMultipartFile("image", "cover.png", "image/png", new byte[]{1});

        assertNull(challengeService.updateCoverImage(9L, image));
        assertNull(foreign.getCoverImageUrl());
        verify(challengeRepository, never()).save(any(Challenge.class));
    }
}
