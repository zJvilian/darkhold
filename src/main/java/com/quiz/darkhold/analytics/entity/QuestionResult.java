package com.quiz.darkhold.analytics.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Entity representing statistics for a specific question in a game.
 */
@Entity
@Table(name = "question_result")
public class QuestionResult {

    private static final double PERCENTAGE_MULTIPLIER = 100.0;
    private static final double EASY_THRESHOLD = 75.0;
    private static final double MEDIUM_THRESHOLD = 50.0;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "game_result_id", nullable = false)
    private GameResult gameResult;

    @Column(name = "question_number", nullable = false)
    private Integer questionNumber;

    @Column(name = "question_text", nullable = false, length = 1000)
    private String questionText;

    @Column(name = "question_type", nullable = false)
    private String questionType;

    @Column(name = "correct_count", nullable = false)
    private Integer correctCount;

    @Column(name = "incorrect_count", nullable = false)
    private Integer incorrectCount;

    @Column(name = "timeout_count", nullable = false)
    private Integer timeoutCount;

    @Column(name = "average_answer_time_seconds")
    private Integer averageAnswerTimeSeconds;

    @Column(name = "fastest_answer_time_seconds")
    private Integer fastestAnswerTimeSeconds;

    // Constructors
    /**
     * Default constructor required by JPA for entity instantiation.
     */
    public QuestionResult() {
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(final Long id) {
        this.id = id;
    }

    public GameResult getGameResult() {
        return gameResult;
    }

    public void setGameResult(final GameResult gameResult) {
        this.gameResult = gameResult;
    }

    public Integer getQuestionNumber() {
        return questionNumber;
    }

    public void setQuestionNumber(final Integer questionNumber) {
        this.questionNumber = questionNumber;
    }

    public String getQuestionText() {
        return questionText;
    }

    public void setQuestionText(final String questionText) {
        this.questionText = questionText;
    }

    public String getQuestionType() {
        return questionType;
    }

    public void setQuestionType(final String questionType) {
        this.questionType = questionType;
    }

    public Integer getCorrectCount() {
        return correctCount;
    }

    public void setCorrectCount(final Integer correctCount) {
        this.correctCount = correctCount;
    }

    public Integer getIncorrectCount() {
        return incorrectCount;
    }

    public void setIncorrectCount(final Integer incorrectCount) {
        this.incorrectCount = incorrectCount;
    }

    public Integer getTimeoutCount() {
        return timeoutCount;
    }

    public void setTimeoutCount(final Integer timeoutCount) {
        this.timeoutCount = timeoutCount;
    }

    public Integer getAverageAnswerTimeSeconds() {
        return averageAnswerTimeSeconds;
    }

    public void setAverageAnswerTimeSeconds(final Integer averageAnswerTimeSeconds) {
        this.averageAnswerTimeSeconds = averageAnswerTimeSeconds;
    }

    public Integer getFastestAnswerTimeSeconds() {
        return fastestAnswerTimeSeconds;
    }

    public void setFastestAnswerTimeSeconds(final Integer fastestAnswerTimeSeconds) {
        this.fastestAnswerTimeSeconds = fastestAnswerTimeSeconds;
    }

    /**
     * Get total number of participants who saw this question.
     *
     * @return total participants
     */
    public int getTotalParticipants() {
        return correctCount + incorrectCount + timeoutCount;
    }

    /**
     * Calculate success rate as percentage.
     *
     * @return success rate (0-100)
     */
    public double getSuccessRatePercentage() {
        int total = getTotalParticipants();
        if (total == 0) {
            return 0.0;
        }
        return (correctCount * PERCENTAGE_MULTIPLIER) / total;
    }

    /**
     * Determine difficulty level based on success rate.
     *
     * @return difficulty level (EASY, MEDIUM, HARD)
     */
    public String getDifficultyLevel() {
        double successRate = getSuccessRatePercentage();
        if (successRate >= EASY_THRESHOLD) {
            return "EASY";
        } else if (successRate >= MEDIUM_THRESHOLD) {
            return "MEDIUM";
        } else {
            return "HARD";
        }
    }
}
