package com.quiz.darkhold.challenge.dto;

import com.quiz.darkhold.challenge.entity.QuestionSet;
import com.quiz.darkhold.challenge.entity.QuestionType;

/**
 * DTO for returning question data.
 */
public class QuestionResponse {

    private Long id;
    private Long challengeId;
    private String question;
    private String answer1;
    private String answer2;
    private String answer3;
    private String answer4;

    private String answer5;

    private String answer6;

    private String answer7;

    private String answer8;

    private String answer9;

    private String answer10;
    private String correctOptions;
    private Integer displayOrder;
    private QuestionType questionType;
    private Integer timeLimit;
    private Integer points;
    private String acceptableAnswers;
    private String imageUrl;
    private String videoUrl;

    /**
     * Default constructor for DTO initialization.
     */
    public QuestionResponse() {
    }

    /**
     * Create a response from a QuestionSet entity.
     *
     * @param questionSet the entity
     * @return the response DTO
     */
    public static QuestionResponse fromEntity(final QuestionSet questionSet) {
        QuestionResponse response = new QuestionResponse();
        mapBasicFields(response, questionSet);
        mapExtendedFields(response, questionSet);
        return response;
    }

    private static void mapBasicFields(final QuestionResponse resp, final QuestionSet qs) {
        resp.id = qs.getId();
        resp.challengeId = qs.getChallenge() != null ? qs.getChallenge().getId() : null;
        resp.question = qs.getQuestion();
        resp.answer1 = qs.getAnswer1();
        resp.answer2 = qs.getAnswer2();
        resp.answer3 = qs.getAnswer3();
        resp.answer4 = qs.getAnswer4();
        resp.answer5 = qs.getAnswer5();
        resp.answer6 = qs.getAnswer6();
        resp.answer7 = qs.getAnswer7();
        resp.answer8 = qs.getAnswer8();
        resp.answer9 = qs.getAnswer9();
        resp.answer10 = qs.getAnswer10();
    }

    private static void mapExtendedFields(final QuestionResponse resp, final QuestionSet qs) {
        resp.correctOptions = qs.getCorrectOptions();
        resp.displayOrder = qs.getDisplayOrder();
        resp.questionType = qs.getQuestionType();
        resp.timeLimit = qs.getTimeLimit();
        resp.points = qs.getPoints();
        resp.acceptableAnswers = qs.getAcceptableAnswers();
        resp.imageUrl = qs.getImageUrl();
        resp.videoUrl = qs.getVideoUrl();
    }

    public Long getId() {
        return id;
    }

    public void setId(final Long id) {
        this.id = id;
    }

    public Long getChallengeId() {
        return challengeId;
    }

    public void setChallengeId(final Long challengeId) {
        this.challengeId = challengeId;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(final String question) {
        this.question = question;
    }

    public String getAnswer1() {
        return answer1;
    }

    public void setAnswer1(final String answer1) {
        this.answer1 = answer1;
    }

    public String getAnswer2() {
        return answer2;
    }

    public void setAnswer2(final String answer2) {
        this.answer2 = answer2;
    }

    public String getAnswer3() {
        return answer3;
    }

    public void setAnswer3(final String answer3) {
        this.answer3 = answer3;
    }

    public String getAnswer4() {
        return answer4;
    }

    public void setAnswer4(final String answer4) {
        this.answer4 = answer4;
    }

    public String getAnswer5() {
        return answer5;
    }

    public void setAnswer5(final String answer5) {
        this.answer5 = answer5;
    }

    public String getAnswer6() {
        return answer6;
    }

    public void setAnswer6(final String answer6) {
        this.answer6 = answer6;
    }

    public String getAnswer7() {
        return answer7;
    }

    public void setAnswer7(final String answer7) {
        this.answer7 = answer7;
    }

    public String getAnswer8() {
        return answer8;
    }

    public void setAnswer8(final String answer8) {
        this.answer8 = answer8;
    }

    public String getAnswer9() {
        return answer9;
    }

    public void setAnswer9(final String answer9) {
        this.answer9 = answer9;
    }

    public String getAnswer10() {
        return answer10;
    }

    public void setAnswer10(final String answer10) {
        this.answer10 = answer10;
    }

    public String getCorrectOptions() {
        return correctOptions;
    }

    public void setCorrectOptions(final String correctOptions) {
        this.correctOptions = correctOptions;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(final Integer displayOrder) {
        this.displayOrder = displayOrder;
    }

    public QuestionType getQuestionType() {
        return questionType;
    }

    public void setQuestionType(final QuestionType questionType) {
        this.questionType = questionType;
    }

    public Integer getTimeLimit() {
        return timeLimit;
    }

    public void setTimeLimit(final Integer timeLimit) {
        this.timeLimit = timeLimit;
    }

    public Integer getPoints() {
        return points;
    }

    public void setPoints(final Integer points) {
        this.points = points;
    }

    public String getAcceptableAnswers() {
        return acceptableAnswers;
    }

    public void setAcceptableAnswers(final String acceptableAnswers) {
        this.acceptableAnswers = acceptableAnswers;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(final String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getVideoUrl() {
        return videoUrl;
    }

    public void setVideoUrl(final String videoUrl) {
        this.videoUrl = videoUrl;
    }
}
