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
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
public class ChallengeService {
    private static final int MAX_POINTS = 10_000;
    private static final int MAX_TITLE_LENGTH = 100;
    private static final int MAX_DESCRIPTION_LENGTH = 250;
    private static final int DEFAULT_POINTS = 1000;
    private static final int MAX_MEDIA_URL_LENGTH = 500;
    private static final java.util.Set<String> HEADER_NAMES = java.util.Set.of("question", "questiontext");
    private static final String DUPLICATE_TITLE =
            "A quiz with this title already exists. Please choose a different title.";
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
            throw new ChallengeException(DUPLICATE_TITLE);
        }
        Deque<QuestionSet> questionSets = readAndProcessChallenge(upload);
        Challenge challenge = new Challenge();
        challenge.setTitle(title);
        challenge.setDescription(description);
        challenge.setQuestionSets(questionSets.stream().toList());
        challenge.setChallengeOwner(currentUserId());
        challenge.setDraft(true);
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
            imageProcessingService.deleteChallengeImages(challengeId); // question images, audio and cover
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
     * @throws ChallengeException when the title is already taken
     */
    public Challenge createEmptyChallenge(final String title, final String description)
            throws ChallengeException {
        if (challengeRepository.existsByTitle(title)) {
            throw new ChallengeException(DUPLICATE_TITLE);
        }
        Challenge challenge = new Challenge();
        challenge.setTitle(title);
        challenge.setDescription(description);
        challenge.setChallengeOwner(currentUserId());
        challenge.setDraft(true);
        return challengeRepository.save(challenge);
    }

    /**
     * Finish the creation flow: a draft becomes a playable quiz once it has at least one question.
     *
     * @param challengeId the challenge ID
     * @return the finished challenge, or null if not found/not editable
     * @throws ChallengeException when the quiz has no questions yet
     */
    @Transactional
    public Challenge finishChallenge(final Long challengeId) throws ChallengeException {
        Challenge challenge = getChallengeForEdit(challengeId);
        if (challenge == null) {
            return null;
        }
        if (challenge.getQuestionSets() == null || challenge.getQuestionSets().isEmpty()) {
            throw new ChallengeException("Add at least one question before finishing the quiz.");
        }
        challenge.setDraft(false);
        return challengeRepository.save(challenge);
    }

    /**
     * Update an existing challenge's title and description.
     *
     * @param challengeId the challenge ID
     * @param title       new title
     * @param description new description
     * @return the updated challenge or null if not found/not editable
     * @throws ChallengeException when another quiz already has the title
     */
    public Challenge updateChallenge(final Long challengeId, final String title, final String description)
            throws ChallengeException {
        java.util.Optional<Challenge> challengeOpt = challengeRepository.findById(challengeId);
        if (challengeOpt.isPresent()) {
            Challenge challenge = challengeOpt.get();
            if (!canEdit(challenge)) {
                logger.warn("User {} attempted to update challenge {} owned by {}",
                        currentUserId(), challengeId, challenge.getChallengeOwner());
                return null;
            }
            if (!title.equals(challenge.getTitle()) && challengeRepository.existsByTitle(title)) {
                throw new ChallengeException(DUPLICATE_TITLE);
            }
            challenge.setTitle(title);
            challenge.setDescription(description);
            return challengeRepository.save(challenge);
        }
        return null;
    }

    // Titles are unique, so a second copy of the same quiz needs its own name
    private String copyTitle(final String title) {
        String copy = withSuffix(title, " (Copy)");
        for (int n = 2; challengeRepository.existsByTitle(copy); n++) {
            copy = withSuffix(title, " (Copy " + n + ")");
        }
        return copy;
    }

    // The title column holds 100 characters, so a long title gives up its own tail to keep the suffix
    private String withSuffix(final String title, final String suffix) {
        return clip(title, MAX_TITLE_LENGTH - suffix.length()) + suffix;
    }

    private static String clip(final String value, final int max) {
        return value.length() > max ? value.substring(0, max) : value;
    }

    /**
     * Duplicate a challenge with all its questions.
     *
     * @param challengeId the challenge to duplicate
     * @return the new challenge or null if not found/not editable by the current user
     */
    @Transactional
    public Challenge duplicateChallenge(final Long challengeId) {
        java.util.Optional<Challenge> challengeOpt = challengeRepository.findById(challengeId);
        if (challengeOpt.isEmpty()) {
            return null;
        }
        Challenge original = challengeOpt.get();
        if (!canEdit(original)) {
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
        newChallenge.setDraft(original.isDraft());
        Challenge savedChallenge = challengeRepository.save(newChallenge);
        // the copy gets its own picture files, so deleting one quiz never breaks the other
        savedChallenge.setCoverImageUrl(imageProcessingService.copyMedia(
                original.getCoverImageUrl(), savedChallenge.getId()));

        // Duplicate all questions
        if (original.getQuestionSets() != null) {
            Deque<QuestionSet> newQuestions = new ArrayDeque<>();
            int order = 0;
            for (QuestionSet origQ : original.getQuestionSets()) {
                QuestionSet newQ = copyQuestionFields(origQ, savedChallenge.getId());
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
     * Import a challenge from JSON DTO. A title that is already taken gets a "(Copy)" suffix.
     *
     * @param dto the parsed export
     * @return the created challenge
     * @throws ChallengeException when the export has no title or a question is unusable
     */
    @Transactional
    public Challenge importFromJson(final ChallengeExportDto dto) throws ChallengeException {
        if (dto == null || dto.getTitle() == null || dto.getTitle().isBlank()) {
            throw new ChallengeException("The file is not a valid quiz export: the quiz has no title.");
        }
        String title = clip(dto.getTitle().trim(), MAX_TITLE_LENGTH);
        Challenge challenge = new Challenge();
        challenge.setTitle(challengeRepository.existsByTitle(title) ? copyTitle(title) : title);
        challenge.setDescription(dto.getDescription() == null
                ? "" : clip(dto.getDescription(), MAX_DESCRIPTION_LENGTH));
        challenge.setChallengeOwner(currentUserId());
        challenge.setDraft(true);
        Challenge savedChallenge = challengeRepository.save(challenge);
        savedChallenge.setCoverImageUrl(imageProcessingService.copyMedia(
                safeMediaUrl(dto.getCoverImageUrl()), savedChallenge.getId()));

        if (dto.getQuestions() != null) {
            Deque<QuestionSet> questions = new ArrayDeque<>();
            int order = 0;
            for (ChallengeExportDto.QuestionExportDto qDto : dto.getQuestions()) {
                QuestionSet qs = copyQuestionFieldsFromDto(qDto, savedChallenge.getId());
                try {
                    validateQuestion(qs);
                } catch (ChallengeException questionError) {
                    throw new ChallengeException("Question " + (order + 1) + ": " + questionError.getErrorMessage());
                }
                qs.setDisplayOrder(order++);
                qs.setChallenge(savedChallenge);
                questions.add(qs);
            }
            questionSetRepository.saveAll(questions);
        }
        return savedChallenge;
    }

    // Imported files may name anything: only our own uploads and plain web links are kept
    private static String safeMediaUrl(final String url) {
        if (url == null || url.length() > MAX_MEDIA_URL_LENGTH) {
            return null;
        }
        boolean local = url.startsWith("/question-images/") && !url.contains("..");
        return local || url.startsWith("https://") || url.startsWith("http://") ? url : null;
    }

    private QuestionSet copyQuestionFields(final QuestionSet src, final Long newChallengeId) {
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
        dest.setVideoUrl(src.getVideoUrl());
        dest.setImageUrl(imageProcessingService.copyMedia(src.getImageUrl(), newChallengeId));
        dest.setAnswerImageUrl(imageProcessingService.copyMedia(src.getAnswerImageUrl(), newChallengeId));
        return dest;
    }

    private QuestionSet copyQuestionFieldsFromDto(final ChallengeExportDto.QuestionExportDto src,
                                                  final Long newChallengeId) {
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
        if (src.getQuestionType() != null) {
            dest.setQuestionType(src.getQuestionType());
        }
        dest.setTimeLimit(src.getTimeLimit() != null && src.getTimeLimit() > 0 ? src.getTimeLimit() : null);
        dest.setPoints(src.getPoints() == null ? DEFAULT_POINTS : src.getPoints());
        dest.setAcceptableAnswers(src.getAcceptableAnswers());
        dest.setVideoUrl(safeMediaUrl(src.getVideoUrl()));
        dest.setImageUrl(imageProcessingService.copyMedia(safeMediaUrl(src.getImageUrl()), newChallengeId));
        dest.setAnswerImageUrl(imageProcessingService.copyMedia(safeMediaUrl(src.getAnswerImageUrl()), newChallengeId));
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
        String filename = upload.getOriginalFilename();
        if (filename != null && filename.toLowerCase(Locale.ROOT).endsWith(".csv")) {
            return readCsvChallenge(upload);
        }
        return readExcelChallenge(upload);
    }

    private Deque<QuestionSet> readExcelChallenge(final MultipartFile upload) throws ChallengeException {
        Deque<QuestionSet> questionSets = new ArrayDeque<>();
        try (XSSFWorkbook workbook = new XSSFWorkbook(upload.getInputStream());) {
            if (workbook.getNumberOfSheets() == 0) {
                throw new ChallengeException("The Excel file has no sheets.");
            }
            Sheet sheet = workbook.getSheetAt(0);
            int order = 0;
            for (Row currentRow : sheet) {
                if (fetchCellValue(currentRow.getCell(0)) == null
                        || (currentRow.getRowNum() == sheet.getFirstRowNum() && looksLikeHeader(currentRow))) {
                    continue; // blank or trailing row, or the column titles
                }
                try {
                    QuestionSet questionSet = populateQuestionSet(currentRow);
                    questionSet.setDisplayOrder(order++);
                    questionSets.add(questionSet);
                } catch (ChallengeException rowError) {
                    throw new ChallengeException("Row " + (currentRow.getRowNum() + 1) + ": "
                            + rowError.getErrorMessage());
                }
            }
        } catch (IOException | IllegalArgumentException | POIXMLException exception) {
            // POI throws unchecked exceptions on corrupt files
            logger.error("Unable to read the uploaded Excel file", exception);
            throw new ChallengeException("Unable to read the file. Please upload a valid .xlsx or .csv file.");
        }
        return questionSets;
    }

    private Deque<QuestionSet> readCsvChallenge(final MultipartFile upload) throws ChallengeException {
        Deque<QuestionSet> questionSets = new ArrayDeque<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(upload.getInputStream(), StandardCharsets.UTF_8))) {
            int rowNum = 0;
            int order = 0;
            String line;
            while ((line = reader.readLine()) != null) {
                if (rowNum == 0 && !line.isEmpty() && line.charAt(0) == '\uFEFF') {
                    line = line.substring(1); // strip UTF-8 BOM from Excel-exported CSVs
                }
                rowNum++;
                if (line.isBlank()) {
                    continue;
                }
                List<String> cols = parseCsvLine(line);
                String first = csvCell(cols, 0);
                if (first == null || (rowNum == 1 && looksLikeHeader(first, csvCell(cols, 5)))) {
                    continue;
                }
                try {
                    QuestionSet questionSet = populateQuestionSetFromCells(cols);
                    questionSet.setDisplayOrder(order++);
                    questionSets.add(questionSet);
                } catch (ChallengeException rowError) {
                    throw new ChallengeException("Row " + rowNum + ": " + rowError.getErrorMessage());
                }
            }
        } catch (IOException exception) {
            logger.error("Unable to read the uploaded CSV file", exception);
            throw new ChallengeException("Unable to read the file. Please upload a valid .xlsx or .csv file.");
        }
        return questionSets;
    }

    private static List<String> parseCsvLine(final String line) {
        List<String> cells = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (inQuotes) {
                if (ch == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else if (ch == '"') {
                    inQuotes = false;
                } else {
                    current.append(ch);
                }
            } else if (ch == '"') {
                inQuotes = true;
            } else if (ch == ',') {
                cells.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        cells.add(current.toString());
        return cells;
    }

    private static String csvCell(final List<String> cols, final int index) {
        if (index >= cols.size()) {
            return null;
        }
        String trimmed = cols.get(index).trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    // Exports and hand-made sheets often start with column titles such as Question / CorrectOptions
    private boolean looksLikeHeader(final Row row) {
        return looksLikeHeader(fetchCellValue(row.getCell(0)), fetchCellValue(row.getCell(5)));
    }

    private boolean looksLikeHeader(final String first, final String sixth) {
        return HEADER_NAMES.contains(headerKey(first)) || "correctoptions".equals(headerKey(sixth));
    }

    private static String headerKey(final String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[\\s_]", "");
    }

    @SuppressWarnings("checkstyle:MethodLength")
    private QuestionSet populateQuestionSetFromCells(final List<String> cols) throws ChallengeException {
        QuestionSet questionSet = new QuestionSet();
        questionSet.setQuestion(csvCell(cols, 0));
        questionSet.setAnswer1(csvCell(cols, 1));
        questionSet.setAnswer2(csvCell(cols, 2));
        questionSet.setAnswer3(csvCell(cols, 3));
        questionSet.setAnswer4(csvCell(cols, 4));
        questionSet.setQuestionType(parseQuestionTypeString(csvCell(cols, 6)));
        questionSet.setTimeLimit(parseTimeLimitString(csvCell(cols, 7)));
        questionSet.setPoints(parsePointsString(csvCell(cols, 8)));
        questionSet.setAcceptableAnswers(csvCell(cols, 9));
        questionSet.setImageUrl(safeMediaUrl(csvCell(cols, 10)));
        questionSet.setVideoUrl(safeMediaUrl(csvCell(cols, 11)));
        questionSet.setAnswer5(csvCell(cols, 12));
        questionSet.setAnswer6(csvCell(cols, 13));
        questionSet.setAnswer7(csvCell(cols, 14));
        questionSet.setAnswer8(csvCell(cols, 15));
        questionSet.setAnswer9(csvCell(cols, 16));
        questionSet.setAnswer10(csvCell(cols, 17));
        questionSet.setAnswerImageUrl(safeMediaUrl(csvCell(cols, 18)));
        String correctOptions = csvCell(cols, 5);
        if (correctOptions != null) {
            questionSet.setCorrectOptions(populateOptionsFromString(correctOptions, questionSet.getQuestionType()));
        }
        validateQuestion(questionSet);
        return questionSet;
    }

    @SuppressWarnings("checkstyle:MethodLength")
    private QuestionSet populateQuestionSet(final Row currentRow) throws ChallengeException {
        java.util.Iterator<Cell> cellIterator = currentRow.iterator();
        QuestionSet questionSet = new QuestionSet();
        String correctOptions = null;
        while (cellIterator.hasNext()) {
            Cell currentCell = cellIterator.next();
            switch (currentCell.getColumnIndex()) {
                case 0 -> questionSet.setQuestion(fetchCellValue(currentCell));
                case 1 -> questionSet.setAnswer1(fetchCellValue(currentCell));
                case 2 -> questionSet.setAnswer2(fetchCellValue(currentCell));
                case 3 -> questionSet.setAnswer3(fetchCellValue(currentCell));
                case 4 -> questionSet.setAnswer4(fetchCellValue(currentCell));
                case 5 -> correctOptions = fetchCellValue(currentCell); // parsed below, it depends on the type
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
                case 18 -> questionSet.setAnswerImageUrl(safeMediaUrl(fetchCellValue(currentCell)));
                default -> logger.debug("Extra column at {}", currentCell.getColumnIndex());
            }
        }
        if (correctOptions != null) {
            questionSet.setCorrectOptions(populateOptionsFromString(correctOptions, questionSet.getQuestionType()));
        }
        validateQuestion(questionSet);
        logger.info("Current questions : {}", questionSet);
        return questionSet;
    }

    private void validateQuestion(final QuestionSet questionSet) throws ChallengeException {
        if (questionSet.getQuestion() == null || questionSet.getQuestion().isBlank()) {
            throw new ChallengeException("The question text is missing");
        }
        if (questionSet.getPoints() != null && (questionSet.getPoints() < 0 || questionSet.getPoints() > MAX_POINTS)) {
            throw new ChallengeException("Points must be between 0 and " + MAX_POINTS);
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
    }

    private QuestionType parseQuestionType(final Cell cell) {
        return parseQuestionTypeString(fetchCellValue(cell));
    }

    private QuestionType parseQuestionTypeString(final String value) {
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

    /** Seconds, or null (the game default) when blank, invalid or not above zero. */
    private Integer parseTimeLimit(final Cell cell) {
        if (cell.getCellType() == CellType.NUMERIC) {
            int seconds = (int) cell.getNumericCellValue();
            return seconds > 0 ? seconds : null;
        }
        return parseTimeLimitString(fetchCellValue(cell));
    }

    private Integer parseTimeLimitString(final String value) {
        if (value == null) {
            return null;
        }
        try {
            int seconds = Integer.parseInt(value.trim());
            return seconds > 0 ? seconds : null;
        } catch (NumberFormatException ex) {
            logger.warn("Invalid time limit: {}", value);
            return null;
        }
    }

    // out-of-range values are rejected later by validateQuestion
    private Integer parsePoints(final Cell cell) {
        return parsePointsString(fetchCellValue(cell));
    }

    private Integer parsePointsString(final String value) {
        if (value == null) {
            return DEFAULT_POINTS;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            logger.warn("Invalid points: {}, defaulting to 1000", value);
            return DEFAULT_POINTS;
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

    /** Letters A-J, or TRUE/FALSE for True/False and EXACT/FUZZY for Word Cloud questions. */
    private String populateOptionsFromString(final String options, final QuestionType type)
            throws ChallengeException {
        String single = options.trim().toUpperCase(Locale.ROOT);
        if ((type == QuestionType.TRUE_FALSE && ("TRUE".equals(single) || "FALSE".equals(single)))
                || (type == QuestionType.WORD_CLOUD && ("EXACT".equals(single) || "FUZZY".equals(single)))) {
            return single;
        }
        java.util.List<String> parsed = new java.util.ArrayList<>();
        for (String raw : options.split(",")) {
            try {
                parsed.add(Options.valueOf(raw.trim().toUpperCase(Locale.ROOT)).name());
            } catch (IllegalArgumentException ex) {
                throw new ChallengeException("Invalid correct option '" + raw.trim()
                        + "'. Use letters A-J separated by commas, TRUE or FALSE for True/False questions, "
                        + "or EXACT or FUZZY for Word Cloud questions.");
            }
        }
        return String.join(",", parsed);
    }

}
