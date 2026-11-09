package com.quiz.darkhold.options.service;

import com.quiz.darkhold.challenge.entity.Challenge;
import com.quiz.darkhold.challenge.repository.ChallengeRepository;
import com.quiz.darkhold.user.entity.Role;
import com.quiz.darkhold.user.entity.User;
import com.quiz.darkhold.user.repository.UserRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
class OptionsServiceTest {
    private final ChallengeRepository challengeRepository = Mockito.mock(ChallengeRepository.class);
    private final UserRepository userRepository = Mockito.mock(UserRepository.class);
    private final OptionsService optionsService = new OptionsService(challengeRepository, userRepository);

    @BeforeEach
    public void setup() {
        var authentication = Mockito.mock(Authentication.class);
        // Mockito.whens() for your authorization object
        var securityContext = Mockito.mock(SecurityContext.class);
        Mockito.when(securityContext.getAuthentication()).thenReturn(authentication);
        Mockito.when(authentication.getName()).thenReturn("admin12345");
        SecurityContextHolder.setContext(securityContext);
    }

    @Test
    void populate() {
        when(userRepository.findByEmail(anyString())).thenReturn(mockUser());
        when(challengeRepository.findAll()).thenReturn(mockChallenges());
        var challengeInfo = optionsService.populateChallengeInfo("admin@admin.com");
        Assertions.assertNotNull(challengeInfo);
    }

    @Test
    void adminSeesAllChallengesWithOwners() {
        var admin = mockUser();
        admin.addRole(new Role(1L, "ADMIN"));
        var other = otherUser();
        when(userRepository.findByEmail(anyString())).thenReturn(admin);
        when(challengeRepository.findAll()).thenReturn(ownedChallenges());
        when(userRepository.findAllById(Mockito.anyIterable())).thenReturn(List.of(admin, other));
        var list = optionsService.populateChallengeInfo("admin@admin.com").getChallengeSummaryList();
        Assertions.assertEquals(2, list.size());
        Assertions.assertTrue(list.get(0).isOwned());
        Assertions.assertEquals("admin12345", list.get(0).getOwnerName());
        Assertions.assertFalse(list.get(1).isOwned());
        Assertions.assertEquals("Jane Doe", list.get(1).getOwnerName());
    }

    @Test
    void regularUserSeesOnlyOwnChallenges() {
        var user = mockUser();
        var challenges = mockChallenges();
        challenges.get(0).setChallengeOwner(1L);
        when(userRepository.findByEmail(anyString())).thenReturn(user);
        when(challengeRepository.findByChallengeOwner(1L)).thenReturn(List.of(challenges.get(0)));
        var list = optionsService.populateChallengeInfo("user@user.com").getChallengeSummaryList();
        Assertions.assertEquals(1, list.size());
        Assertions.assertTrue(list.get(0).isOwned());
        Mockito.verify(challengeRepository, Mockito.never()).findAll();
    }

    @Test
    void summarizeFillsListFields() {
        var challenge = mockChallenges().get(0);
        challenge.setDescription("About hello");
        challenge.setChallengeOwner(2L);
        var created = java.time.LocalDateTime.of(2026, 1, 2, 3, 4);
        challenge.setCreatedOn(created);
        when(userRepository.findAllById(Mockito.anyIterable())).thenReturn(List.of(otherUser()));
        var row = optionsService.summarize(List.of(challenge), 2L).get(0);
        Assertions.assertEquals("About hello", row.getDescription());
        Assertions.assertEquals(created, row.getCreatedOn());
        Assertions.assertEquals(0, row.getQuestionCount());
        Assertions.assertTrue(row.isOwned());
    }

    private List<Challenge> ownedChallenges() {
        var challenges = mockChallenges();
        challenges.get(0).setChallengeOwner(1L);
        challenges.get(1).setChallengeOwner(2L);
        return challenges;
    }

    private User otherUser() {
        var other = new User();
        other.setId(2L);
        other.setFirstName("Jane");
        other.setLastName("Doe");
        return other;
    }

    private User mockUser() {
        var user = new User();
        user.setId(1L);
        user.setFirstName("admin12345");
        return user;
    }

    private List<Challenge> mockChallenges() {
        var challenge1 = new Challenge();
        challenge1.setId(1234L);
        challenge1.setTitle("Hello");
        Challenge challenge2 = new Challenge();
        challenge2.setId(1235L);
        challenge2.setTitle("Hello1");
        List<Challenge> challenges = new ArrayList<>();
        challenges.add(challenge1);
        challenges.add(challenge2);
        return challenges;
    }
}