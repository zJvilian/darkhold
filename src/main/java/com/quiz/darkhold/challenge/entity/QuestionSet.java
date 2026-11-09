package com.quiz.darkhold.challenge.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.util.StringJoiner;

@Entity
@Table(name = "question_set")
public class QuestionSet implements Serializable {

    public static final long serialVersionUID = 4328743;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 425)
    private String question;

    @Column(length = 255)
    private String answer1;

    @Column(length = 255)
    private String answer2;

    @Column(length = 255)
    private String answer3;

    @Column(length = 255)
    private String answer4;

    @Column(length = 255)
    private String answer5;

    @Column(length = 255)
    private String answer6;

    @Column(length = 255)
    private String answer7;

    @Column(length = 255)
    private String answer8;

    @Column(length = 255)
    private String answer9;

    @Column(length = 255)
    private String answer10;

    @Column(length = 255)
    private String correctOptions;

    @Column(name = "display_order")
    private Integer displayOrder = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "question_type")
    private QuestionType questionType = QuestionType.MULTIPLE_CHOICE;

    @Column(name = "time_limit")
    private Integer timeLimit;

    @Column
    private Integer points = 1000;

    @Column(name = "acceptable_answers", length = 500)
    private String acceptableAnswers;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "video_url", length = 500)
    private String videoUrl;

    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "challenge_id", nullable = true)
    private Challenge challenge;

    public Long getId() {
        return id;
    }

    public void setId(final Long id) {
        this.id = id;
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

    @JsonIgnore
    public String[] getSortingAnswers() {
        return new String[]{answer1, answer2, answer3, answer4, answer5,
                answer6, answer7, answer8, answer9, answer10};
    }

    public String getCorrectOptions() {
        return correctOptions;
    }

    public void setCorrectOptions(final String correctOptions) {
        this.correctOptions = correctOptions;
    }

    public Challenge getChallenge() {
        return challenge;
    }

    public void setChallenge(final Challenge challenge) {
        this.challenge = challenge;
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

    @Override
    public String toString() {
        return new StringJoiner(", ", QuestionSet.class.getSimpleName() + "[", "]")
                .add("id=" + id)
                .add("question='" + question + "'")
                .add("answer1='" + answer1 + "'")
                .add("answer2='" + answer2 + "'")
                .add("answer3='" + answer3 + "'")
                .add("answer4='" + answer4 + "'")
                .add("correctOptions='" + correctOptions + "'")
                .add("questionType=" + questionType)
                .add("timeLimit=" + timeLimit)
                .add("points=" + points)
                .toString();
    }
}
