package com.quiz.darkhold.challenge.dto;

import com.quiz.darkhold.challenge.entity.Challenge;
import com.quiz.darkhold.challenge.entity.QuestionSet;
import com.quiz.darkhold.challenge.entity.QuestionType;

import java.util.ArrayList;
import java.util.List;

/**
 * DTO for exporting/importing challenges as JSON.
 */
public class ChallengeExportDto {

    private String title;
    private String description;
    private List<QuestionExportDto> questions = new ArrayList<>();

    public static ChallengeExportDto fromEntity(final Challenge challenge) {
        ChallengeExportDto dto = new ChallengeExportDto();
        dto.setTitle(challenge.getTitle());
        dto.setDescription(challenge.getDescription());
        if (challenge.getQuestionSets() != null) {
            for (QuestionSet questionSet : challenge.getQuestionSets()) {
                dto.getQuestions().add(QuestionExportDto.fromEntity(questionSet));
            }
        }
        return dto;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(final String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(final String description) {
        this.description = description;
    }

    public List<QuestionExportDto> getQuestions() {
        return questions;
    }

    public void setQuestions(final List<QuestionExportDto> questions) {
        this.questions = questions;
    }

    /**
     * Nested DTO for question export.
     */
    public static class QuestionExportDto {
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
        private QuestionType questionType;
        private Integer timeLimit;
        private Integer points;
        private String acceptableAnswers;
        private String imageUrl;
        private String videoUrl;

        @SuppressWarnings("checkstyle:MethodLength")
        public static QuestionExportDto fromEntity(final QuestionSet questionSet) {
            QuestionExportDto dto = new QuestionExportDto();
            dto.setQuestion(questionSet.getQuestion());
            dto.setAnswer1(questionSet.getAnswer1());
            dto.setAnswer2(questionSet.getAnswer2());
            dto.setAnswer3(questionSet.getAnswer3());
            dto.setAnswer4(questionSet.getAnswer4());
            dto.setAnswer5(questionSet.getAnswer5());
            dto.setAnswer6(questionSet.getAnswer6());
            dto.setAnswer7(questionSet.getAnswer7());
            dto.setAnswer8(questionSet.getAnswer8());
            dto.setAnswer9(questionSet.getAnswer9());
            dto.setAnswer10(questionSet.getAnswer10());
            dto.setCorrectOptions(questionSet.getCorrectOptions());
            dto.setQuestionType(questionSet.getQuestionType());
            dto.setTimeLimit(questionSet.getTimeLimit());
            dto.setPoints(questionSet.getPoints());
            dto.setAcceptableAnswers(questionSet.getAcceptableAnswers());
            dto.setImageUrl(questionSet.getImageUrl());
            dto.setVideoUrl(questionSet.getVideoUrl());
            return dto;
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
}
