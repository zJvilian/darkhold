package com.quiz.darkhold.options.service;

import com.quiz.darkhold.challenge.entity.Challenge;
import com.quiz.darkhold.challenge.repository.ChallengeRepository;
import com.quiz.darkhold.options.model.ChallengeInfo;
import com.quiz.darkhold.options.model.ChallengeSummary;
import com.quiz.darkhold.user.entity.User;
import com.quiz.darkhold.user.repository.UserRepository;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class OptionsService {
    private final Log log = LogFactory.getLog(OptionsService.class);

    private final ChallengeRepository challengeRepository;

    private final UserRepository userRepository;

    public OptionsService(final ChallengeRepository challengeRepository, final UserRepository userRepository) {
        this.challengeRepository = challengeRepository;
        this.userRepository = userRepository;
    }

    /**
     * get all challenges and display it there.
     *
     * @param email user email
     * @return challenges
     */
    public ChallengeInfo populateChallengeInfo(final String email) {
        User user = email != null ? userRepository.findByEmail(email) : null;
        if (user == null) {
            ChallengeInfo empty = new ChallengeInfo();
            empty.setChallengeSummaryList(Collections.emptyList());
            return empty;
        }
        log.info(user.getId());
        List<ChallengeSummary> summaries = findVisibleSummaries(user);
        ChallengeInfo challengeInfo = new ChallengeInfo();
        challengeInfo.setChallengeSummaryList(summaries);
        return challengeInfo;
    }

    private List<ChallengeSummary> findVisibleSummaries(final User user) {
        List<Challenge> challenges = user.canManageAllChallenges() ? challengeRepository.findAll()
                : challengeRepository.findByChallengeOwner(user.getId());
        log.info("total challenges visible to the user is " + challenges.size());
        return summarize(challenges, user.getId());
    }

    /**
     * Build list rows for the given quizzes.
     *
     * @param challenges quizzes to list
     * @param viewerId id of the user viewing the list; rows owned by this user are flagged as owned
     * @return one summary per quiz
     */
    public List<ChallengeSummary> summarize(final List<Challenge> challenges, final Long viewerId) {
        Map<Long, String> ownerNames = loadOwnerNames(challenges);
        return challenges.stream().map(c -> getChallengeSummary(c, viewerId, ownerNames)).toList();
    }

    private Map<Long, String> loadOwnerNames(final List<Challenge> challenges) {
        Set<Long> ownerIds = challenges.stream().map(Challenge::getChallengeOwner).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> ownerNames = new HashMap<>();
        userRepository.findAllById(ownerIds).forEach(owner -> ownerNames.put(owner.getId(), displayName(owner)));
        return ownerNames;
    }

    private ChallengeSummary getChallengeSummary(final Challenge challenge, final Long viewerId,
                                                 final Map<Long, String> ownerNames) {
        ChallengeSummary summary = new ChallengeSummary();
        summary.setChallengeId(challenge.getId().intValue());
        summary.setChallengeName(challenge.getTitle());
        summary.setCoverImageUrl(challenge.getCoverImageUrl());
        summary.setOwnerName(ownerNames.getOrDefault(challenge.getChallengeOwner(), ""));
        summary.setOwned(Objects.equals(challenge.getChallengeOwner(), viewerId));
        summary.setDescription(challenge.getDescription());
        summary.setCreatedOn(challenge.getCreatedOn());
        summary.setQuestionCount(challenge.getQuestionSets() == null ? 0 : challenge.getQuestionSets().size());
        return summary;
    }

    private static String displayName(final User owner) {
        String name = ((owner.getFirstName() == null ? "" : owner.getFirstName()) + " "
                + (owner.getLastName() == null ? "" : owner.getLastName())).trim();
        return name.isEmpty() && owner.getEmail() != null ? owner.getEmail() : name;
    }
}
