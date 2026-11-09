package com.quiz.darkhold.options.model;

import java.time.LocalDateTime;

public class ChallengeSummary {

    private int challengeId;
    private String challengeName;
    private String ownerName;
    private boolean owned;
    private String coverImageUrl;
    private String description;
    private LocalDateTime createdOn;
    private int questionCount;

    public int getChallengeId() {
        return challengeId;
    }

    public void setChallengeId(final int challengeId) {
        this.challengeId = challengeId;
    }

    public String getChallengeName() {
        return challengeName;
    }

    public void setChallengeName(final String challengeName) {
        this.challengeName = challengeName;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(final String ownerName) {
        this.ownerName = ownerName;
    }

    public boolean isOwned() {
        return owned;
    }

    public void setOwned(final boolean owned) {
        this.owned = owned;
    }

    public String getCoverImageUrl() {
        return coverImageUrl;
    }

    public void setCoverImageUrl(final String coverImageUrl) {
        this.coverImageUrl = coverImageUrl;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(final String description) {
        this.description = description;
    }

    public LocalDateTime getCreatedOn() {
        return createdOn;
    }

    public void setCreatedOn(final LocalDateTime createdOn) {
        this.createdOn = createdOn;
    }

    public int getQuestionCount() {
        return questionCount;
    }

    public void setQuestionCount(final int questionCount) {
        this.questionCount = questionCount;
    }
}
