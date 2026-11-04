package com.quiz.darkhold.challenge.service;

import com.quiz.darkhold.challenge.dto.QuestionRequest;
import com.quiz.darkhold.challenge.dto.QuestionResponse;
import com.quiz.darkhold.challenge.dto.ReorderRequest;
import com.quiz.darkhold.challenge.entity.Challenge;
import com.quiz.darkhold.challenge.entity.QuestionSet;
import com.quiz.darkhold.challenge.exception.ImageValidationException;
import com.quiz.darkhold.challenge.repository.ChallengeRepository;
import com.quiz.darkhold.challenge.repository.QuestionSetRepository;
import com.quiz.darkhold.user.entity.DarkholdUserDetails;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
public class QuestionService {

    private final Logger logger = LogManager.getLogger(QuestionService.class);
    private final QuestionSetRepository questionSetRepository;
    private final ChallengeRepository challengeRepository;
    private final ImageProcessingService imageProcessingService;

    public QuestionService(final QuestionSetRepository questionSetRepository,
                           final ChallengeRepository challengeRepository,
                           final ImageProcessingService imageProcessingService) {
        this.questionSetRepository = questionSetRepository;
        this.challengeRepository = challengeRepository;
        this.imageProcessingService = imageProcessingService;
    }

    @Transactional
    public QuestionResponse createQuestion(final QuestionRequest request) {
        Challenge challenge = getOwnedChallenge(request.getChallengeId());
        if (challenge == null) {
            return null;
        }
        QuestionSet questionSet = buildQuestionSet(request, challenge);
        questionSet.setDisplayOrder(getNextDisplayOrder(challenge));
        QuestionSet saved = questionSetRepository.save(questionSet);
        logger.info("Created question {} for challenge {}", saved.getId(), request.getChallengeId());
        return QuestionResponse.fromEntity(saved);
    }

    @Transactional
    public QuestionResponse updateQuestion(final Long questionId, final QuestionRequest request) {
        QuestionSet question = getOwnedQuestion(questionId);
        if (question == null) {
            return null;
        }
        updateQuestionFields(question, request);
        QuestionSet saved = questionSetRepository.save(question);
        logger.info("Updated question {}", questionId);
        return QuestionResponse.fromEntity(saved);
    }

    @Transactional
    public boolean deleteQuestion(final Long questionId) {
        QuestionSet question = getOwnedQuestion(questionId);
        if (question == null) {
            return false;
        }
        // Delete associated image if exists
        if (question.getImageUrl() != null) {
            imageProcessingService.deleteQuestionImage(question.getImageUrl());
        }
        questionSetRepository.delete(question);
        logger.info("Deleted question {}", questionId);
        return true;
    }

    public QuestionResponse getQuestion(final Long questionId) {
        QuestionSet question = getOwnedQuestion(questionId);
        return question == null ? null : QuestionResponse.fromEntity(question);
    }

    @Transactional
    public boolean reorderQuestions(final ReorderRequest request) {
        Challenge challenge = getOwnedChallenge(request.getChallengeId());
        if (challenge == null) {
            return false;
        }
        updateQuestionOrders(request.getChallengeId(), request.getQuestionIds());
        logger.info("Reordered {} questions", request.getQuestionIds().size());
        return true;
    }

    @Transactional
    public QuestionResponse duplicateQuestion(final Long questionId) {
        QuestionSet original = getOwnedQuestion(questionId);
        if (original == null) {
            return null;
        }
        QuestionSet newQuestion = copyQuestion(original);
        QuestionSet saved = questionSetRepository.save(newQuestion);
        logger.info("Duplicated question {} as {}", questionId, saved.getId());
        return QuestionResponse.fromEntity(saved);
    }

    @Transactional
    public int bulkDeleteQuestions(final List<Long> questionIds) {
        int deleted = 0;
        for (Long questionId : questionIds) {
            if (deleteQuestion(questionId)) {
                deleted++;
            }
        }
        logger.info("Bulk deleted {} questions", deleted);
        return deleted;
    }

    @Transactional
    @SuppressWarnings("checkstyle:MethodLength")
    public String uploadQuestionImage(final Long questionId, final MultipartFile file) {
        QuestionSet question = getOwnedQuestion(questionId);
        if (question == null) {
            throw new ImageValidationException("Question not found or access denied");
        }
        if (imageProcessingService.isAudioFile(file)
                && !ImageProcessingService.supportsAudio(question.getQuestionType())) {
            throw new ImageValidationException(
                    "Audio is only available for Multiple Choice, True/False, and Type Answer questions");
        }

        // Delete old image if exists
        if (question.getImageUrl() != null) {
            imageProcessingService.deleteQuestionImage(question.getImageUrl());
        }

        // Save new image
        Long challengeId = question.getChallenge().getId();
        String imageUrl = imageProcessingService.saveQuestionImage(challengeId, questionId, file);

        // Update question entity
        question.setImageUrl(imageUrl);
        questionSetRepository.save(question);

        logger.info("Uploaded image for question {}: {}", questionId, imageUrl);
        return imageUrl;
    }

    @Transactional
    public boolean deleteQuestionImage(final Long questionId) {
        QuestionSet question = getOwnedQuestion(questionId);
        if (question == null || question.getImageUrl() == null) {
            return false;
        }

        // Delete image file
        imageProcessingService.deleteQuestionImage(question.getImageUrl());

        // Clear imageUrl field
        question.setImageUrl(null);
        questionSetRepository.save(question);

        logger.info("Deleted image for question {}", questionId);
        return true;
    }

    private Challenge getOwnedChallenge(final Long challengeId) {
        java.util.Optional<Challenge> challengeOpt = challengeRepository.findById(challengeId);
        if (challengeOpt.isEmpty() || !isOwner(challengeOpt.get())) {
            logger.warn("Challenge {} not found or not owned", challengeId);
            return null;
        }
        return challengeOpt.get();
    }

    private QuestionSet getOwnedQuestion(final Long questionId) {
        java.util.Optional<QuestionSet> questionOpt = questionSetRepository.findById(questionId);
        if (questionOpt.isEmpty()) {
            logger.warn("Question not found: {}", questionId);
            return null;
        }
        QuestionSet question = questionOpt.get();
        if (question.getChallenge() == null || !isOwner(question.getChallenge())) {
            logger.warn("Question {} not owned by user", questionId);
            return null;
        }
        return question;
    }

    private boolean isOwner(final Challenge challenge) {
        return challenge.getChallengeOwner().equals(currentUserId());
    }

    private int getNextDisplayOrder(final Challenge challenge) {
        if (challenge.getQuestionSets() == null || challenge.getQuestionSets().isEmpty()) {
            return 0;
        }
        return challenge.getQuestionSets().stream()
                .mapToInt(q -> q.getDisplayOrder() != null ? q.getDisplayOrder() : 0)
                .max().orElse(0) + 1;
    }

    @SuppressWarnings("checkstyle:MethodLength")
    private QuestionSet buildQuestionSet(final QuestionRequest request, final Challenge challenge) {
        QuestionSet qs = new QuestionSet();
        qs.setQuestion(request.getQuestion());
        updateAnswers(qs, request);
        qs.setCorrectOptions(request.getCorrectOptions());
        qs.setQuestionType(request.getQuestionType());
        qs.setTimeLimit(request.getTimeLimit());
        qs.setPoints(request.getPoints());
        qs.setAcceptableAnswers(request.getAcceptableAnswers());
        qs.setImageUrl(request.getImageUrl());
        qs.setVideoUrl(request.getVideoUrl());
        qs.setChallenge(challenge);
        return qs;
    }

    private void updateAnswers(final QuestionSet question, final QuestionRequest request) {
        question.setAnswer1(request.getAnswer1());
        question.setAnswer2(request.getAnswer2());
        question.setAnswer3(request.getAnswer3());
        question.setAnswer4(request.getAnswer4());
        question.setAnswer5(request.getAnswer5());
        question.setAnswer6(request.getAnswer6());
        question.setAnswer7(request.getAnswer7());
        question.setAnswer8(request.getAnswer8());
        question.setAnswer9(request.getAnswer9());
        question.setAnswer10(request.getAnswer10());
    }

    @SuppressWarnings("checkstyle:MethodLength")
    private void updateQuestionFields(final QuestionSet question, final QuestionRequest request) {
        question.setQuestion(request.getQuestion());
        updateAnswers(question, request);
        question.setCorrectOptions(request.getCorrectOptions());
        question.setQuestionType(request.getQuestionType());
        question.setTimeLimit(request.getTimeLimit());
        question.setPoints(request.getPoints());
        question.setAcceptableAnswers(request.getAcceptableAnswers());
        question.setVideoUrl(request.getVideoUrl());
        if (!ImageProcessingService.supportsAudio(request.getQuestionType())
                && ImageProcessingService.isAudioUrl(question.getImageUrl())) {
            imageProcessingService.deleteQuestionImage(question.getImageUrl());
            question.setImageUrl(null);
        }
    }

    private void updateQuestionOrders(final Long challengeId, final List<Long> questionIds) {
        for (int i = 0; i < questionIds.size(); i++) {
            java.util.Optional<QuestionSet> questionOpt = questionSetRepository.findById(questionIds.get(i));
            if (questionOpt.isPresent() && belongsToChallenge(questionOpt.get(), challengeId)) {
                questionOpt.get().setDisplayOrder(i);
                questionSetRepository.save(questionOpt.get());
            }
        }
    }

    private boolean belongsToChallenge(final QuestionSet question, final Long challengeId) {
        return question.getChallenge() != null && question.getChallenge().getId().equals(challengeId);
    }

    private QuestionSet copyQuestion(final QuestionSet original) {
        QuestionSet newQuestion = new QuestionSet();
        copyBasicFields(newQuestion, original);
        copyExtendedFields(newQuestion, original);
        newQuestion.setDisplayOrder(getNextDisplayOrder(original.getChallenge()));
        newQuestion.setChallenge(original.getChallenge());
        return newQuestion;
    }

    private void copyBasicFields(final QuestionSet dest, final QuestionSet src) {
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
    }

    private void copyExtendedFields(final QuestionSet dest, final QuestionSet src) {
        dest.setQuestionType(src.getQuestionType());
        dest.setTimeLimit(src.getTimeLimit());
        dest.setPoints(src.getPoints());
        dest.setAcceptableAnswers(src.getAcceptableAnswers());
        dest.setVideoUrl(src.getVideoUrl());
        // Note: imageUrl is NOT copied - each question should have unique images
    }

    private Long currentUserId() {
        org.springframework.security.core.Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Object principal = auth.getPrincipal();
        return ((DarkholdUserDetails) principal).getUser().getId();
    }
}
