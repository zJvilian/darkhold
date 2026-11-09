package com.quiz.darkhold.game.service;

import com.quiz.darkhold.challenge.entity.QuestionSet;
import com.quiz.darkhold.challenge.entity.QuestionType;
import com.quiz.darkhold.challenge.entity.SortingAnswer;
import org.apache.commons.text.similarity.LevenshteinDistance;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;


/**
 * Service for validating answers based on question type.
 * Supports fuzzy matching for TYPE_ANSWER and WORD_CLOUD questions using Levenshtein distance.
 */
@Service
public class AnswerValidationService {

    private final Logger logger = LogManager.getLogger(AnswerValidationService.class);
    private final LevenshteinDistance levenshtein = new LevenshteinDistance();

    private static final int SHORT_ANSWER_TOLERANCE = 1;
    private static final int MEDIUM_ANSWER_TOLERANCE = 2;
    private static final double LONG_ANSWER_TOLERANCE_RATIO = 0.2;
    private static final int SHORT_ANSWER_MAX_LENGTH = 5;
    private static final int MEDIUM_ANSWER_MAX_LENGTH = 10;

    /**
     * Validate a user's answer against the correct answer(s) for a question.
     *
     * @param question   the question being answered
     * @param userAnswer the user's submitted answer
     * @return true if the answer is correct (or close enough for TYPE_ANSWER)
     */
    public boolean validateAnswer(final QuestionSet question, final String userAnswer) {
        if (question == null || userAnswer == null) {
            return false;
        }
        QuestionType questionType = question.getQuestionType() == null
                ? QuestionType.MULTIPLE_CHOICE : question.getQuestionType();
        return switch (questionType) {
            case MULTIPLE_CHOICE -> validateMultipleChoice(question, userAnswer);
            case SORTING -> validateSorting(question, userAnswer);
            case TRUE_FALSE -> validateTrueFalse(question, userAnswer);
            case TYPE_ANSWER -> validateTypeAnswer(question, userAnswer);
            case POLL, WORD_CLOUD -> !userAnswer.isBlank();
        };
    }

    /**
     * Validate a single chosen option (A-D, TRUE or FALSE) as posted by the click-to-answer flow.
     * With several correct options, picking any one of them is correct. Typed and sorting answers
     * are validated through {@link #validateAnswer} instead, so they never match here.
     *
     * @param question the question being answered
     * @param selected the chosen option, blank when the player timed out
     * @return true if the choice is correct
     */
    public boolean validateSelection(final QuestionSet question, final String selected) {
        if (question == null || selected == null || selected.isBlank()) {
            return false;
        }
        QuestionType questionType = question.getQuestionType() == null
                ? QuestionType.MULTIPLE_CHOICE : question.getQuestionType();
        return switch (questionType) {
            case MULTIPLE_CHOICE -> question.getCorrectOptions() != null
                    && optionSet(question.getCorrectOptions()).contains(selected.strip().toUpperCase(Locale.ROOT));
            case TRUE_FALSE -> validateTrueFalse(question, selected);
            default -> false;
        };
    }

    /**
     * Check if this question type should be scored.
     */
    public boolean shouldScore(final QuestionSet question) {
        if (question == null || question.getQuestionType() == null) {
            return true;
        }
        return question.getQuestionType() != QuestionType.POLL
                && question.getQuestionType() != QuestionType.WORD_CLOUD;
    }

    /**
     * Group free-text answers into word-cloud entries sorted by frequency descending.
     * Every submitted answer is represented; when fuzzyMatch is enabled, typos merge into
     * the most frequent similar word.
     */
    public List<Map<String, Object>> buildWordCloud(final List<String> rawAnswers, final boolean fuzzyMatch) {
        if (rawAnswers == null || rawAnswers.isEmpty()) {
            return List.of();
        }
        Map<String, Integer> exact = countExactAnswers(rawAnswers);
        Map<String, Integer> clusters = fuzzyMatch ? mergeWordCloudTypos(exact) : exact;
        return clusters.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder()))
                .map(entry -> Map.<String, Object>of("text", entry.getKey(), "count", entry.getValue()))
                .toList();
    }

    private Map<String, Integer> countExactAnswers(final List<String> rawAnswers) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String answer : rawAnswers) {
            String normalized = normalizeAnswer(answer);
            if (!normalized.isEmpty()) {
                counts.merge(normalized, 1, Integer::sum);
            }
        }
        return counts;
    }

    private Map<String, Integer> mergeWordCloudTypos(final Map<String, Integer> exact) {
        List<Map.Entry<String, Integer>> sorted = exact.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder())).toList();
        Map<String, Integer> clusters = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : sorted) {
            String match = findClosestCluster(clusters.keySet(), entry.getKey());
            clusters.merge(match != null ? match : entry.getKey(), entry.getValue(), Integer::sum);
        }
        return clusters;
    }

    private String findClosestCluster(final java.util.Set<String> clusters, final String word) {
        String best = null;
        int bestDist = Integer.MAX_VALUE;
        for (String canonical : clusters) {
            int dist = typoDistanceIfMatch(word, canonical);
            if (dist >= 0 && dist < bestDist) {
                bestDist = dist;
                best = canonical;
            }
        }
        return best;
    }

    private int typoDistanceIfMatch(final String word, final String canonical) {
        int maxLen = Math.max(word.length(), canonical.length());
        if (maxLen < 4 || Math.abs(word.length() - canonical.length()) > 2) {
            return -1;
        }
        if (maxLen == 4 && word.charAt(0) != canonical.charAt(0)) {
            return -1;
        }
        int dist = levenshtein.apply(word, canonical);
        int limit = maxLen >= 9 ? 2 : 1;
        return dist <= limit ? dist : -1;
    }

    /**
     * Get the points value for a question (defaults to 1000).
     */
    public int getPoints(final QuestionSet question) {
        if (question == null || question.getPoints() == null) {
            return 1000;
        }
        return question.getPoints();
    }

    private boolean validateSorting(final QuestionSet question, final String userAnswer) {
        return SortingAnswer.isValid(userAnswer, question.getSortingAnswers())
                && SortingAnswer.normalize(userAnswer).equals(SortingAnswer.normalize(question.getCorrectOptions()));
    }

    private boolean validateMultipleChoice(final QuestionSet question, final String userAnswer) {
        String correctOptions = question.getCorrectOptions();
        if (correctOptions == null || correctOptions.isBlank()) {
            return false;
        }
        return optionSet(correctOptions).equals(optionSet(userAnswer));
    }

    private java.util.Set<String> optionSet(final String options) {
        return Arrays.stream(options.toUpperCase(Locale.ROOT).split(",", -1))
                .map(String::strip).collect(Collectors.toSet());
    }

    private boolean validateTrueFalse(final QuestionSet question, final String userAnswer) {
        String correctAnswer = question.getCorrectOptions();
        if (correctAnswer == null || correctAnswer.isBlank()) {
            return false;
        }
        return correctAnswer.equalsIgnoreCase(userAnswer.trim());
    }

    private boolean validateTypeAnswer(final QuestionSet question, final String userAnswer) {
        String correctAnswer = question.getAnswer1();
        if (correctAnswer == null || correctAnswer.isBlank()) {
            logger.warn("TYPE_ANSWER question has no correct answer set");
            return false;
        }
        String normalizedUserAnswer = normalizeAnswer(userAnswer);
        if (isFuzzyMatch(normalizedUserAnswer, normalizeAnswer(correctAnswer))) {
            return true;
        }
        return checkAlternatives(question.getAcceptableAnswers(), normalizedUserAnswer);
    }

    private boolean checkAlternatives(final String acceptableAnswers, final String userAnswer) {
        if (acceptableAnswers == null || acceptableAnswers.isBlank()) {
            return false;
        }
        for (String alt : acceptableAnswers.split(",", -1)) {
            if (isFuzzyMatch(userAnswer, normalizeAnswer(alt))) {
                return true;
            }
        }
        return false;
    }

    private String normalizeAnswer(final String answer) {
        if (answer == null) {
            return "";
        }
        return answer.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private boolean isFuzzyMatch(final String userAnswer, final String correctAnswer) {
        if (userAnswer.equals(correctAnswer)) {
            return true;
        }
        int distance = levenshtein.apply(userAnswer, correctAnswer);
        int maxLength = Math.max(userAnswer.length(), correctAnswer.length());
        int tolerance = calculateTolerance(maxLength);
        boolean isMatch = distance <= tolerance;
        if (isMatch && distance > 0) {
            logger.debug("Fuzzy match: '{}' -> '{}' (distance: {})", userAnswer, correctAnswer, distance);
        }
        return isMatch;
    }

    private int calculateTolerance(final int length) {
        if (length <= SHORT_ANSWER_MAX_LENGTH) {
            return SHORT_ANSWER_TOLERANCE;
        } else if (length <= MEDIUM_ANSWER_MAX_LENGTH) {
            return MEDIUM_ANSWER_TOLERANCE;
        } else {
            return (int) Math.ceil(length * LONG_ANSWER_TOLERANCE_RATIO);
        }
    }
}
