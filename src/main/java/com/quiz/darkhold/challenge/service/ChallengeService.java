package com.quiz.darkhold.challenge.service;

import com.quiz.darkhold.challenge.dto.ChallengeExportDto;
import com.quiz.darkhold.challenge.entity.Challenge;
import com.quiz.darkhold.challenge.entity.Options;
import com.quiz.darkhold.challenge.entity.QuestionSet;
import com.quiz.darkhold.challenge.entity.QuestionType;
import com.quiz.darkhold.challenge.entity.SortingAnswer;
import com.quiz.darkhold.challenge.exception.ChallengeException;
import com.quiz.darkhold.challenge.repository.ChallengeRepository;
import com.quiz.darkhold.challenge.repository.QuestionSetRepository;
import com.quiz.darkhold.user.entity.DarkholdUserDetails;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.poi.ooxml.POIXMLException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Objects;

@Service
public class ChallengeService {
    private static final int MAX_POINTS = 10_000;
    private final Logger logger = LogManager.getLogger(ChallengeService.class);
    private final ChallengeRepository challengeRepository;
    private final QuestionSetRepository questionSetRepository;
    private final ImageProcessingService imageProcessingService;
    private final DataFormatter cellFormatter = new DataFormatter();

    public ChallengeService(final ChallengeRepository challengeRepository,
                            final QuestionSetRepository questionSetRepository,
                            final ImageProcessingService imageProcessingService) {
        this.challengeRepository = challengeRepository;
        this.questionSetRepository = questionSetRepository;
        this.imageProcessingService = imageProcessingService;
    }

    /**
     * Read the Excel, process it, extract data and save it to the database.
     *
     * @param upload      the Excel file
     * @param title       challenge name
     * @param description challenge desc
     * @throws ChallengeException on error
     */
    @Transactional
    public Long readProcessAndSaveChallenge(final MultipartFile upload,
                                            final String title,
                                            final String description)
            throws ChallengeException {
        if (challengeRepository.existsByTitle(title)) {
            throw new ChallengeException("A quiz with this title already exists. Please choose a different title.");
        }
        Deque<QuestionSet> questionSets = readAndProcessChallenge(upload);
        Challenge challenge = new Challenge();
        challenge.setTitle(title);
        challenge.setDescription(description);
        challenge.setQuestionSets(questionSets.stream().toList());
        challenge.setChallengeOwner(currentUserId());
        final Challenge savedChallenge = challengeRepository.save(challenge);
        questionSets.forEach(q -> q.setChallenge(savedChallenge));
        questionSetRepository.saveAll(questionSets);
        return savedChallenge.getId();
    }

    @Transactional
    public Boolean deleteChallenge(final Long challengeId) {
        Boolean response = Boolean.FALSE;
        java.util.Optional<Challenge> challenge = challengeRepository.findById(challengeId);
        if (challenge.isPresent()) {
            if (!canEdit(challenge.get())) {
                logger.warn("User {} attempted to delete challenge {} owned by {}",
                        currentUserId(), challengeId, challenge.get().getChallengeOwner());
                return Boolean.FALSE;
            }
            logger.info("Challenge present in database");
            java.util.List<QuestionSet> questionSets = challenge.get().getQuestionSets();
            questionSetRepository.deleteAll(questionSets);
            challengeRepository.delete(challenge.get());
            imageProcessingService.deleteQuestionImage(challenge.get().getCoverImageUrl());
            response = Boolean.TRUE;
        }
        return response;
    }

    /**
     * Create an empty challenge (without questions from Excel).
     *
     * @param title       challenge title
     * @param description challenge description
     * @return the created challenge
     */
    public Challenge createEmptyChallenge(final String title, final String description) {
        Challenge challenge = new Challenge();
        challenge.setTitle(title);
        challenge.setDescription(description);
        challenge.setChallengeOwner(currentUserId());
        return challengeRepository.save(challenge);
    }

    /**
     * Update an existing challenge's title and description.
     *
     * @param challengeId the challenge ID
     * @param title       new title
     * @param description new description
     * @return the updated challenge or null if not found/not editable
     */
    public Challenge updateChallenge(final Long challengeId, final String title, final String description) {
        java.util.Optional<Challenge> challengeOpt = challengeRepository.findById(challengeId);
        if (challengeOpt.isPresent()) {
            Challenge challenge = challengeOpt.get();
            if (!canEdit(challenge)) {
                logger.warn("User {} attempted to update challenge {} owned by {}",
                        currentUserId(), challengeId, challenge.getChallengeOwner());
                return null;
            }
            challenge.setTitle(title);
            challenge.setDescription(description);
            return challengeRepository.save(challenge);
        }
        return null;
    }

    // Titles are unique, so a second copy of the same quiz needs its own name
    private String copyTitle(final String title) {
        String copy = title + " (Copy)";
        for (int n = 2; challengeRepository.existsByTitle(copy); n++) {
            copy = title + " (Copy " + n + ")";
        }
        return copy;
    }

    /**
     * Duplicate a challenge with all its questions.
     *
     * @param challengeId the challenge to duplicate
     * @return the new challenge or null if not found/not owned
     */
    public Challenge duplicateChallenge(final Long challengeId) {
        java.util.Optional<Challenge> challengeOpt = challengeRepository.findById(challengeId);
        if (challengeOpt.isEmpty()) {
            return null;
        }
        Challenge original = challengeOpt.get();
        if (!Objects.equals(original.getChallengeOwner(), currentUserId())) {
            logger.warn("User {} attempted to duplicate challenge {} owned by {}",
                    currentUserId(), challengeId, original.getChallengeOwner());
            return null;
        }

        // Create new challenge with copied title
        Challenge newChallenge = new Challenge();
        newChallenge.setTitle(copyTitle(original.getTitle()));
        newChallenge.setDescription(original.getDescription());
        newChallenge.setWordCloudFuzzy(original.isWordCloudFuzzy());
        newChallenge.setChallengeOwner(currentUserId());
        Challenge savedChallenge = challengeRepository.save(newChallenge);

        // Duplicate all questions
        if (original.getQuestionSets() != null) {
            Deque<QuestionSet> newQuestions = new ArrayDeque<>();
            int order = 0;
            for (QuestionSet origQ : original.getQuestionSets()) {
                QuestionSet newQ = copyQuestionFields(origQ);
                newQ.setDisplayOrder(order++);
                newQ.setChallenge(savedChallenge);
                newQuestions.add(newQ);
            }
            questionSetRepository.saveAll(newQuestions);
        }

        return savedChallenge;
    }

    /**
     * Set or replace the cover (preview) picture of a challenge.
     *
     * @param challengeId the challenge ID
     * @param file        the image file
     * @return the new image URL, or null if not found/not editable
     */
    public String updateCoverImage(final Long challengeId, final MultipartFile file) {
        Challenge challenge = getChallengeForEdit(challengeId);
        if (challenge == null) {
            return null;
        }
        String imageUrl = imageProcessingService.saveChallengeCover(challengeId, file);
        imageProcessingService.deleteQuestionImage(challenge.getCoverImageUrl());
        challenge.setCoverImageUrl(imageUrl);
        challengeRepository.save(challenge);
        return imageUrl;
    }

    /**
     * Remove the cover (preview) picture of a challenge.
     *
     * @param challengeId the challenge ID
     * @return true if a cover was removed
     */
    public boolean removeCoverImage(final Long challengeId) {
        Challenge challenge = getChallengeForEdit(challengeId);
        if (challenge == null || challenge.getCoverImageUrl() == null) {
            return false;
        }
        imageProcessingService.deleteQuestionImage(challenge.getCoverImageUrl());
        challenge.setCoverImageUrl(null);
        challengeRepository.save(challenge);
        return true;
    }

    /**
     * Get a challenge by ID if the current user owns it or may manage all challenges.
     *
     * @param challengeId the challenge ID
     * @return the challenge or null if not found/not editable
     */
    public Challenge getChallengeForEdit(final Long challengeId) {
        java.util.Optional<Challenge> challengeOpt = challengeRepository.findById(challengeId);
        if (challengeOpt.isPresent()) {
            Challenge challenge = challengeOpt.get();
            if (canEdit(challenge)) {
                return challenge;
            }
            logger.warn("User {} attempted to access challenge {} owned by {}",
                    currentUserId(), challengeId, challenge.getChallengeOwner());
        }
        return null;
    }

    /**
     * Import a challenge from JSON DTO.
     */
    public Challenge importFromJson(final ChallengeExportDto dto) {
        Challenge challenge = new Challenge();
        challenge.setTitle(dto.getTitle());
        challenge.setDescription(dto.getDescription());
        challenge.setChallengeOwner(currentUserId());
        Challenge savedChallenge = challengeRepository.save(challenge);

        if (dto.getQuestions() != null) {
            Deque<QuestionSet> questions = new ArrayDeque<>();
            int order = 0;
            for (ChallengeExportDto.QuestionExportDto qDto : dto.getQuestions()) {
                QuestionSet qs = copyQuestionFieldsFromDto(qDto);
                qs.setDisplayOrder(order++);
                qs.setChallenge(savedChallenge);
                questions.add(qs);
            }
            questionSetRepository.saveAll(questions);
        }
        return savedChallenge;
    }

    private QuestionSet copyQuestionFields(final QuestionSet src) {
        QuestionSet dest = new QuestionSet();
        dest.setQuestion(src.getQuestion());
        dest.setAnswer1(src.getAnswer1());
        dest.setAnswer2(src.getAnswer2());
        dest.setAnswer3(src.getAnswer3());
        dest.setAnswer4(src.getAnswer4());
        dest.setAnswer5(src.getAnswer5());
        dest.setAnswer6(src.getAnswer6());
        dest.setAnswer7(src.getAnswer7());
        dest.setAnswer8(src.getAnswer8());
        dest.setAnswer9(src.getAnswer9());
        dest.setAnswer10(src.getAnswer10());
        dest.setCorrectOptions(src.getCorrectOptions());
        dest.setQuestionType(src.getQuestionType());
        dest.setTimeLimit(src.getTimeLimit());
        dest.setPoints(src.getPoints());
        dest.setAcceptableAnswers(src.getAcceptableAnswers());
        return dest;
    }

    private QuestionSet copyQuestionFieldsFromDto(final ChallengeExportDto.QuestionExportDto src) {
        QuestionSet dest = new QuestionSet();
        dest.setQuestion(src.getQuestion());
        dest.setAnswer1(src.getAnswer1());
        dest.setAnswer2(src.getAnswer2());
        dest.setAnswer3(src.getAnswer3());
        dest.setAnswer4(src.getAnswer4());
        dest.setAnswer5(src.getAnswer5());
        dest.setAnswer6(src.getAnswer6());
        dest.setAnswer7(src.getAnswer7());
        dest.setAnswer8(src.getAnswer8());
        dest.setAnswer9(src.getAnswer9());
        dest.setAnswer10(src.getAnswer10());
        dest.setCorrectOptions(src.getCorrectOptions());
        dest.setQuestionType(src.getQuestionType());
        dest.setTimeLimit(src.getTimeLimit());
        dest.setPoints(src.getPoints());
        dest.setAcceptableAnswers(src.getAcceptableAnswers());
        return dest;
    }

    private com.quiz.darkhold.user.entity.User currentUser() {
        org.springframework.security.core.Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return ((DarkholdUserDetails) auth.getPrincipal()).getUser();
    }

    private Long currentUserId() {
        return currentUser().getId();
    }

    private boolean canEdit(final Challenge challenge) {
        return Objects.equals(challenge.getChallengeOwner(), currentUserId()) || currentUser().canManageAllChallenges();
    }

    //replace LinkedList with ArrayDeque as the return type
    private Deque<QuestionSet> readAndProcessChallenge(final MultipartFile upload) throws ChallengeException {
        Deque<QuestionSet> questionSets = new ArrayDeque<>();
        try (XSSFWorkbook workbook = new XSSFWorkbook(upload.getInputStream());) {
            if (workbook.getNumberOfSheets() == 0) {
                throw new ChallengeException("The Excel file has no sheets.");
            }
            for (Row currentRow : workbook.getSheetAt(0)) {
                if (fetchCellValue(currentRow.getCell(0)) == null) {
                    continue; // blank or trailing row
                }
                try {
                    questionSets.add(populateQuestionSet(currentRow));
                } catch (ChallengeException rowError) {
                    throw new ChallengeException("Row " + (currentRow.getRowNum() + 1) + ": "
                            + rowError.getErrorMessage());
                }
            }
        } catch (IOException | IllegalArgumentException | POIXMLException exception) {
            // POI throws unchecked exceptions on corrupt files
            logger.error("Unable to read the uploaded Excel file", exception);
            throw new ChallengeException("Unable to read the file. Please upload a valid .xlsx file.");
        }
        return questionSets;
    }

    @SuppressWarnings("checkstyle:MethodLength")
    private QuestionSet populateQuestionSet(final Row currentRow) throws ChallengeException {
        java.util.Iterator<Cell> cellIterator = currentRow.iterator();
        QuestionSet questionSet = new QuestionSet();
        while (cellIterator.hasNext()) {
            Cell currentCell = cellIterator.next();
            switch (currentCell.getColumnIndex()) {
                case 0 -> questionSet.setQuestion(fetchCellValue(currentCell));
                case 1 -> questionSet.setAnswer1(fetchCellValue(currentCell));
                case 2 -> questionSet.setAnswer2(fetchCellValue(currentCell));
                case 3 -> questionSet.setAnswer3(fetchCellValue(currentCell));
                case 4 -> questionSet.setAnswer4(fetchCellValue(currentCell));
                case 5 -> questionSet.setCorrectOptions(parseCorrectOptions(currentCell));
                case 6 -> questionSet.setQuestionType(parseQuestionType(currentCell));
                case 7 -> questionSet.setTimeLimit(parseTimeLimit(currentCell));
                case 8 -> questionSet.setPoints(parsePoints(currentCell));
                case 9 -> questionSet.setAcceptableAnswers(fetchCellValue(currentCell));
                case 10 -> questionSet.setImageUrl(fetchCellValue(currentCell));
                case 11 -> questionSet.setVideoUrl(fetchCellValue(currentCell));
                case 12 -> questionSet.setAnswer5(fetchCellValue(currentCell));
                case 13 -> questionSet.setAnswer6(fetchCellValue(currentCell));
                case 14 -> questionSet.setAnswer7(fetchCellValue(currentCell));
                case 15 -> questionSet.setAnswer8(fetchCellValue(currentCell));
                case 16 -> questionSet.setAnswer9(fetchCellValue(currentCell));
                case 17 -> questionSet.setAnswer10(fetchCellValue(currentCell));
                default -> logger.debug("Extra column at {}", currentCell.getColumnIndex());
            }
        }
        if (questionSet.getQuestionType() == QuestionType.SORTING
                && !SortingAnswer.isValid(questionSet.getCorrectOptions(), questionSet.getSortingAnswers())) {
            throw new ChallengeException("Sorting requires each answer exactly once in the correct order");
        }
        if (ImageProcessingService.isAudioUrl(questionSet.getImageUrl())
                && !ImageProcessingService.supportsAudio(questionSet.getQuestionType())) {
            throw new ChallengeException(
                    "Audio is only available for Multiple Choice, True/False, and Type Answer questions");
        }
        logger.info("Current questions : {}", questionSet);
        return questionSet;
    }

    private String parseCorrectOptions(final Cell cell) throws ChallengeException {
        String options = fetchCellValue(cell);
        return options == null ? null : populateOptionsFromString(options);
    }

    private QuestionType parseQuestionType(final Cell cell) {
        String value = fetchCellValue(cell);
        if (value == null || value.isBlank()) {
            return QuestionType.MULTIPLE_CHOICE;
        }
        try {
            return QuestionType.valueOf(value.trim().toUpperCase(Locale.ROOT).replace(" ", "_"));
        } catch (IllegalArgumentException ex) {
            logger.warn("Unknown question type: {}, defaulting to MULTIPLE_CHOICE", value);
            return QuestionType.MULTIPLE_CHOICE;
        }
    }

    private Integer parseTimeLimit(final Cell cell) {
        if (cell.getCellType() == CellType.NUMERIC) {
            return (int) cell.getNumericCellValue();
        }
        String value = fetchCellValue(cell);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            logger.warn("Invalid time limit: {}", value);
            return null;
        }
    }

    private Integer parsePoints(final Cell cell) throws ChallengeException {
        String value = fetchCellValue(cell);
        if (value == null) {
            return 1000; // Default points
        }
        try {
            int points = Integer.parseInt(value.trim());
            if (points < 0 || points > MAX_POINTS) {
                throw new ChallengeException("Points must be between 0 and " + MAX_POINTS);
            }
            return points;
        } catch (NumberFormatException ex) {
            logger.warn("Invalid points: {}, defaulting to 1000", value);
            return 1000;
        }
    }

    /** Cell text as displayed in Excel (numbers without ".0"), or null when blank/missing. */
    private String fetchCellValue(final Cell cell) {
        if (cell == null) {
            return null;
        }
        String value = cellFormatter.formatCellValue(cell).trim();
        return value.isEmpty() ? null : value;
    }

    private String populateOptionsFromString(final String options) throws ChallengeException {
        java.util.List<String> parsed = new java.util.ArrayList<>();
        for (String raw : options.split(",")) {
            try {
                parsed.add(Options.valueOf(raw.trim().toUpperCase(Locale.ROOT)).name());
            } catch (IllegalArgumentException ex) {
                throw new ChallengeException("Invalid correct option '" + raw.trim()
                        + "'. Use letters A-J separated by commas.");
            }
        }
        return String.join(",", parsed);
    }

}
