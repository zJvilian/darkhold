package com.quiz.darkhold.live;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.quiz.darkhold.challenge.entity.Challenge;
import com.quiz.darkhold.challenge.entity.QuestionSet;
import com.quiz.darkhold.challenge.entity.QuestionType;
import com.quiz.darkhold.challenge.repository.ChallengeRepository;
import com.quiz.darkhold.user.repository.UserRepository;
import com.quiz.darkhold.user.entity.DarkholdUserDetails;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:live-quiz-test;DB_CLOSE_DELAY=-1",
        "darkhold.public-base-url=http://quiz.example.test:8181"})
@AutoConfigureMockMvc
class LiveQuizIntegrationTest {
    private static final String HOST = "admin@admin.com";
    @Autowired
    private MockMvc mvc;
    @Autowired
    private LiveRoomRepository rooms;
    @Autowired
    private ChallengeRepository challenges;
    @Autowired
    private UserRepository users;
    @Autowired
    private ObjectMapper mapper;
    private Long challengeId;
    private String pin;

    @BeforeEach
    void setUp() throws Exception {
        Challenge challenge = new Challenge();
        challenge.setTitle("Live test " + UUID.randomUUID());
        challenge.setDescription("Integration fixture");
        challenge.setChallengeOwner(users.findByEmail(HOST).getId());
        QuestionSet choice = question("Which letters?", QuestionType.MULTIPLE_CHOICE, "A,C", "Alpha");
        choice.setAnswer2("Beta");
        choice.setAnswer3("Gamma");
        QuestionSet typed = question("Name our planet", QuestionType.TYPE_ANSWER, null, "Earth");
        choice.setChallenge(challenge);
        typed.setChallenge(challenge);
        challenge.setQuestionSets(new ArrayList<>(List.of(choice, typed)));
        challengeId = challenges.saveAndFlush(challenge).getId();
        var response = mvc.perform(post("/live/host")
                        .with(user(new DarkholdUserDetails(users.findByEmail(HOST)))).with(csrf())
                        .param("challenge_id", challengeId.toString()))
                .andExpect(status().is3xxRedirection()).andReturn().getResponse();
        pin = response.getRedirectedUrl().substring("/play/".length());
        assertTrue(pin.matches("[0-9]{6}"));
    }

    private QuestionSet question(final String text, final QuestionType type, final String correct,
                                 final String answer) {
        QuestionSet question = new QuestionSet();
        question.setQuestion(text);
        question.setQuestionType(type);
        question.setCorrectOptions(correct);
        question.setAnswer1(answer);
        question.setPoints(1000);
        question.setTimeLimit(20);
        return question;
    }

    @AfterEach
    void cleanUp() {
        rooms.deleteById(pin);
        challenges.deleteById(challengeId);
    }

    // A guest is whatever cookies the server handed out when joining (stored session + player cookie).
    private Cookie[] join(final String name, final String avatar) throws Exception {
        return mvc.perform(post("/join").with(csrf()).param("pin", pin)
                        .param("name", name).param("avatar", avatar))
                .andExpect(redirectedUrl("/play/" + pin)).andReturn().getResponse().getCookies();
    }

    private Cookie[] joinRoom(final String roomPin, final String name, final String avatar) throws Exception {
        return mvc.perform(post("/join").with(csrf()).param("pin", roomPin)
                        .param("name", name).param("avatar", avatar))
                .andExpect(status().is3xxRedirection()).andReturn().getResponse().getCookies();
    }

    private JsonNode snapshot(final Cookie[] session) throws Exception {
        return json(mvc.perform(get("/api/live/" + pin).cookie(session))
                .andExpect(status().isOk()).andReturn());
    }

    private JsonNode json(final MvcResult result) throws Exception {
        return mapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode control(final String action, final int index) throws Exception {
        return json(mvc.perform(post("/api/live/" + pin + "/control").with(user(HOST)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new LiveQuizController.Control(action, index, null))))
                .andExpect(status().isOk()).andReturn());
    }

    private JsonNode answer(final Cookie[] session, final int index, final String value) throws Exception {
        return json(mvc.perform(post("/api/live/" + pin + "/answer").cookie(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new LiveQuizController.Answer(index, value))))
                .andExpect(status().isOk()).andReturn());
    }

    private void setClock(final boolean expired) {
        LiveRoom room = rooms.findById(pin).orElseThrow();
        LiveState state = mapper.readValue(room.getStateJson(), LiveState.class);
        state.startedAt = System.currentTimeMillis() - 2000;
        state.endsAt = System.currentTimeMillis() + (expired ? -100 : 18000);
        room.setStateJson(mapper.writeValueAsString(state));
        rooms.saveAndFlush(room);
    }

    @Test
    void sortingKeepsOrderAndRejectsIncompleteSubmissions() throws Exception {
        LiveRoom room = rooms.findById(pin).orElseThrow();
        LiveState state = mapper.readValue(room.getStateJson(), LiveState.class);
        state.questions.getFirst().setQuestionType(QuestionType.SORTING);
        state.questions.getFirst().setCorrectOptions("J,I,H,G,F,E,D,C,A,B");
        state.questions.getFirst().setAnswer4("Answer 4");
        state.questions.getFirst().setAnswer5("Answer 5");
        state.questions.getFirst().setAnswer6("Answer 6");
        state.questions.getFirst().setAnswer7("Answer 7");
        state.questions.getFirst().setAnswer8("Answer 8");
        state.questions.getFirst().setAnswer9("Answer 9");
        state.questions.getFirst().setAnswer10("Answer 10");
        room.setStateJson(mapper.writeValueAsString(state));
        rooms.saveAndFlush(room);
        var correct = join("Correct", "fox");
        final var wrong = join("Wrong", "robot");
        control("start", -1);
        setClock(false);
        for (String invalid : List.of("A,B", "A,A,B", "A,B,C,D")) {
            mvc.perform(post("/api/live/" + pin + "/answer").cookie(correct).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(new LiveQuizController.Answer(0, invalid))))
                    .andExpect(status().isBadRequest());
        }
        assertEquals(10, snapshot(correct).path("question").path("options").size());
        answer(correct, 0, "J,I,H,G,F,E,D,C,A,B");
        answer(wrong, 0, "A,B,C,D,E,F,G,H,I,J");
        assertTrue(snapshot(correct).path("feedback").path("correct").asBoolean());
        assertFalse(snapshot(wrong).path("feedback").path("correct").asBoolean());
        assertEquals("J,I,H,G,F,E,D,C,A,B", snapshot(correct).path("correctAnswer").asText());
    }

    @Test
    void tenSortingAnswersSurviveCreateUpdateAndDuplicate() throws Exception {
        final var owner = user(new DarkholdUserDetails(users.findByEmail(HOST)));
        var payload = new java.util.LinkedHashMap<String, Object>();
        payload.put("challengeId", challengeId);
        payload.put("question", "Sort ten answers");
        payload.put("questionType", "SORTING");
        payload.put("correctOptions", "J,I,H,G,F,E,D,C,B,A");
        for (int i = 1; i <= 10; i++) {
            payload.put("answer" + i, "Answer " + i);
        }
        var created = json(mvc.perform(post("/api/question").with(owner).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(payload)))
                .andExpect(status().isOk()).andReturn());
        String id = created.path("id").asText();
        payload.put("answer10", "Updated tenth answer");
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/question/" + id)
                        .with(owner).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(payload)))
                .andExpect(status().isOk());
        var loaded = json(mvc.perform(get("/api/question/" + id).with(owner))
                .andExpect(status().isOk()).andReturn());
        var copied = json(mvc.perform(post("/api/question/" + id + "/duplicate").with(owner).with(csrf()))
                .andExpect(status().isOk()).andReturn());
        for (int i = 1; i <= 10; i++) {
            assertEquals(payload.get("answer" + i), loaded.path("answer" + i).asText());
            assertEquals(loaded.path("answer" + i), copied.path("answer" + i));
        }
        mvc.perform(get("/edit_challenge/" + challengeId).with(owner))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"answer10\"")))
                .andExpect(content().string(containsString("J: Updated tenth answer")));
        var exported = json(mvc.perform(get("/export_challenge/" + challengeId + "/json").with(owner))
                .andExpect(status().isOk()).andReturn());
        var exportedQuestion = exported.path("questions").get(2);
        for (int i = 1; i <= 10; i++) {
            assertEquals(payload.get("answer" + i), exportedQuestion.path("answer" + i).asText());
        }
        mvc.perform(get("/export_challenge/" + challengeId + "/csv").with(owner))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Answer5,Answer6,Answer7,Answer8,Answer9,Answer10")))
                .andExpect(content().string(containsString(
                        "Answer 5,Answer 6,Answer 7,Answer 8,Answer 9,Updated tenth answer")));
        payload.put("answer10", "x".repeat(226));
        mvc.perform(post("/api/question").with(owner).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void guestsJoinWithNoAccountsAndKeepIdentityOnRefresh() throws Exception {
        long accountCount = users.count();
        var guest = join("Player One", "fox");
        var other = join("Player Two", "robot");
        assertEquals(accountCount, users.count());
        assertEquals("Player One", snapshot(guest).path("me").path("name").asText());
        assertEquals("robot", snapshot(other).path("me").path("avatar").asText());
        assertEquals(2, snapshot(guest).path("players").size());
        mvc.perform(get("/play/" + pin).cookie(guest)).andExpect(status().isOk())
                .andExpect(content().string(containsString("/scripts/live-quiz.js")));
        mvc.perform(post("/join").cookie(guest).with(csrf()).param("pin", pin)
                        .param("name", "Player One").param("avatar", "fox"))
                .andExpect(redirectedUrl("/play/" + pin));
        assertEquals(2, snapshot(guest).path("players").size());
        mvc.perform(get("/challenge/create").cookie(guest)).andExpect(status().is3xxRedirection());
    }

    @Test
    void joinValidationStaysOnHtmlForm() throws Exception {
        join("Player One", "fox");
        mvc.perform(post("/join").with(csrf()).param("pin", pin)
                        .param("name", "player one").param("avatar", "panda"))
                .andExpect(redirectedUrl("/join?pin=" + pin))
                .andExpect(flash().attribute("joinError", containsString("taken")));
        mvc.perform(post("/join").with(csrf()).param("pin", pin)
                        .param("name", "<script>alert(1)</script>").param("avatar", "fox"))
                .andExpect(flash().attribute("joinError", containsString("2–20")));
        mvc.perform(post("/join").with(csrf()).param("pin", pin)
                        .param("name", "Valid name").param("avatar", "invalid"))
                .andExpect(flash().attribute("joinError", containsString("avatar")));
        mvc.perform(get("/join").param("pin", pin)).andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"avatar\"")))
                .andExpect(content().string(not(containsString("type=\"email\""))));
    }

    @Test
    void onlyHostControlsTheRoomAndGuestCannotReadAnotherRoom() throws Exception {
        var guest = join("Guest", "owl");
        mvc.perform(get("/api/live/" + pin)).andExpect(status().isForbidden());
        mvc.perform(get("/api/live/000000").cookie(guest)).andExpect(status().isNotFound());
        String command = "{\"action\":\"start\",\"questionIndex\":-1}";
        mvc.perform(post("/api/live/" + pin + "/control").cookie(guest).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(command)).andExpect(status().isForbidden());
        mvc.perform(post("/api/live/" + pin + "/control").with(user("different-host")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(command)).andExpect(status().isForbidden());
        mvc.perform(post("/api/live/" + pin + "/control").with(user(HOST))
                        .contentType(MediaType.APPLICATION_JSON).content(command)).andExpect(status().isForbidden());
        mvc.perform(post("/live/host").with(user("different-host")).with(csrf())
                        .param("challenge_id", challengeId.toString()))
                .andExpect(flash().attribute("liveError", containsString("owner")));
    }

    @Test
    void completeGameGradesAnswersOnceAndKeepsResultsForEveryPlayer() throws Exception {
        var first = join("First", "panda");
        final var second = join("Second", "cat");
        assertEquals("QUESTION", control("start", -1).path("phase").asText());
        assertFalse(snapshot(first).has("correctAnswer"));
        assertFalse(snapshot(first).path("question").has("correctOptions"));
        setClock(false);
        answer(first, 0, "C,A");
        int score = mapper.readValue(rooms.findById(pin).orElseThrow().getStateJson(), LiveState.class)
                .players.values().stream().filter(player -> player.name.equals("First"))
                .findFirst().orElseThrow().score;
        assertTrue(score >= 500 && score <= 1000);
        answer(first, 0, "B");
        var results = answer(second, 0, "B");
        assertEquals("RESULTS", results.path("phase").asText());
        assertEquals(score, results.path("players").get(0).path("score").asInt());
        assertEquals("A,C", results.path("correctAnswer").asText());
        control("next", 0);
        assertFalse(snapshot(second).toString().contains("Earth"));
        setClock(false);
        answer(first, 1, "Earth");
        answer(second, 1, "Mars");
        assertEquals("FINISHED", control("next", 1).path("phase").asText());
        assertEquals("FINISHED", snapshot(first).path("phase").asText());
        assertEquals("FINISHED", snapshot(second).path("phase").asText());
        assertTrue(snapshot(first).path("me").path("score").asInt() > score);
    }

    @Test
    void rejectsEarlyLateAndStaleAnswers() throws Exception {
        var guest = join("Guest", "lion");
        control("start", -1);
        String payload = "{\"questionIndex\":0,\"answer\":\"A,C\"}";
        mvc.perform(post("/api/live/" + pin + "/answer").cookie(guest).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isConflict());
        setClock(true);
        mvc.perform(post("/api/live/" + pin + "/answer").cookie(guest).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isConflict());
        assertEquals("RESULTS", snapshot(guest).path("phase").asText());
        control("next", 0);
        mvc.perform(post("/api/live/" + pin + "/answer").cookie(guest).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isConflict());
        assertEquals(0, snapshot(guest).path("me").path("score").asInt());
    }

    @Test
    void lockedStartedAndRemovedPlayersCannotJoin() throws Exception {
        final var guest = join("Guest", "frog");
        control("lock", -1);
        mvc.perform(post("/join").with(csrf()).param("pin", pin).param("name", "Late").param("avatar", "fox"))
                .andExpect(flash().attribute("joinError", containsString("locked")));
        control("lock", -1);
        mvc.perform(post("/api/live/" + pin + "/control").with(user(HOST)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"kick\",\"questionIndex\":-1,\"playerName\":\"Guest\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/live/" + pin).cookie(guest)).andExpect(status().isForbidden());
        mvc.perform(post("/join").cookie(guest).with(csrf()).param("pin", pin)
                        .param("name", "New name").param("avatar", "fox"))
                .andExpect(flash().attribute("joinError", containsString("removed")));
        join("Ready", "robot");
        control("start", -1);
        mvc.perform(post("/join").with(csrf()).param("pin", pin).param("name", "Late").param("avatar", "fox"))
                .andExpect(flash().attribute("joinError", containsString("started")));
    }

    @Test
    void qrDecodesToPrefilledJoinPage() throws Exception {
        byte[] png = mvc.perform(get("/api/live/" + pin + "/qr").with(user(HOST)))
                .andExpect(status().isOk()).andExpect(content().contentType("image/png"))
                .andReturn().getResponse().getContentAsByteArray();
        var image = ImageIO.read(new ByteArrayInputStream(png));
        int[] pixels = image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
        var source = new RGBLuminanceSource(image.getWidth(), image.getHeight(), pixels);
        var decoded = new MultiFormatReader().decode(new BinaryBitmap(new HybridBinarizer(source)));
        assertEquals("http://quiz.example.test:8181/join?pin=" + pin, decoded.getText());
        mvc.perform(get("/play/" + pin).with(user(HOST))).andExpect(status().isOk())
                .andExpect(content().string(containsString(decoded.getText())));
    }

    @Test
    void guestCookieRestoresIdentityAfterServerSessionIsLost() throws Exception {
        var response = mvc.perform(post("/join").with(csrf()).param("pin", pin)
                        .param("name", "Returning player").param("avatar", "fox"))
                .andExpect(redirectedUrl("/play/" + pin)).andReturn().getResponse();
        var cookie = response.getCookie("darkhold_player_" + pin);
        assertNotNull(cookie);
        assertTrue(cookie.isHttpOnly());
        assertEquals("Lax", cookie.getAttribute("SameSite"));
        var state = json(mvc.perform(get("/api/live/" + pin).cookie(cookie))
                .andExpect(status().isOk()).andReturn());
        assertEquals("Returning player", state.path("me").path("name").asText());
        mvc.perform(get("/play/" + pin).cookie(cookie)).andExpect(status().isOk());
    }

    @Test
    void simultaneousJoinsDoNotLosePlayers() throws Exception {
        try (var executor = Executors.newFixedThreadPool(4)) {
            var tasks = new ArrayList<java.util.concurrent.Callable<Cookie[]>>();
            for (int index = 0; index < 8; index++) {
                String name = "Player " + index;
                tasks.add(() -> join(name, "fox"));
            }
            var results = executor.invokeAll(tasks);
            for (var result : results) {
                assertNotNull(result.get());
            }
            assertEquals(8, snapshot(results.get(0).get()).path("players").size());
        }
    }

    @Test
    void roomsKeepPlayersAndGameProgressSeparate() throws Exception {
        final var first = join("Same nickname", "fox");
        String otherPin = mvc.perform(post("/live/host").with(user(HOST)).with(csrf())
                        .param("challenge_id", challengeId.toString()))
                .andExpect(status().is3xxRedirection()).andReturn().getResponse().getRedirectedUrl()
                .substring("/play/".length());
        try {
            mvc.perform(get("/api/live/" + otherPin).cookie(first)).andExpect(status().isForbidden());
            var second = mvc.perform(post("/join").with(csrf()).param("pin", otherPin)
                            .param("name", "Same nickname").param("avatar", "robot"))
                    .andExpect(redirectedUrl("/play/" + otherPin)).andReturn().getResponse().getCookies();
            control("start", -1);
            JsonNode other = json(mvc.perform(get("/api/live/" + otherPin).cookie(second))
                    .andExpect(status().isOk()).andReturn());
            assertEquals("LOBBY", other.path("phase").asText());
            assertEquals(1, other.path("players").size());
            assertEquals("QUESTION", snapshot(first).path("phase").asText());
        } finally {
            rooms.deleteById(otherPin);
        }
    }

    @Test
    void wordCloudGrowsWithRepeatedAnswersAndRespectsTypoToggle() throws Exception {
        var hostPrincipal = user(new DarkholdUserDetails(users.findByEmail(HOST)));
        var created = json(mvc.perform(post("/save_challenge").with(hostPrincipal).with(csrf())
                        .param("title", "Cloud " + UUID.randomUUID()).param("description", ""))
                .andExpect(status().isOk()).andReturn());
        long wcChallengeId = created.path("challengeId").asLong();
        mvc.perform(post("/api/question").with(hostPrincipal).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"challengeId\":" + wcChallengeId
                                + ",\"question\":\"Trait of a leader?\",\"questionType\":\"WORD_CLOUD\","
                                + "\"correctOptions\":\"FUZZY\",\"points\":0}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/question").with(hostPrincipal).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"challengeId\":" + wcChallengeId
                                + ",\"question\":\"Exact cloud?\",\"questionType\":\"WORD_CLOUD\","
                                + "\"correctOptions\":\"EXACT\",\"points\":0}"))
                .andExpect(status().isOk());
        String wcPin = mvc.perform(post("/live/host").with(hostPrincipal).with(csrf())
                        .param("challenge_id", String.valueOf(wcChallengeId)))
                .andExpect(status().is3xxRedirection()).andReturn().getResponse()
                .getRedirectedUrl().substring("/play/".length());
        try {
            final var p1 = joinRoom(wcPin, "Ana", "fox");
            final var p2 = joinRoom(wcPin, "Ben", "owl");
            final var p3 = joinRoom(wcPin, "Cal", "cat");
            mvc.perform(post("/api/live/" + wcPin + "/control").with(user(HOST)).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"action\":\"start\",\"questionIndex\":-1}")).andExpect(status().isOk());
            var wcRoom = rooms.findById(wcPin).orElseThrow();
            var wcState = mapper.readValue(wcRoom.getStateJson(), LiveState.class);
            wcState.startedAt = System.currentTimeMillis() - 2000;
            wcState.endsAt = System.currentTimeMillis() + 18000;
            wcRoom.setStateJson(mapper.writeValueAsString(wcState));
            rooms.saveAndFlush(wcRoom);

            // Question 0 has FUZZY override: typo "creativ" merges with "Creative" + "bold" also shows
            mvc.perform(post("/api/live/" + wcPin + "/answer").cookie(p1).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"questionIndex\":0,\"answer\":\"Creative\"}")).andExpect(status().isOk());
            assertTrue(json(mvc.perform(get("/api/live/" + wcPin).cookie(p2)).andReturn())
                    .path("wordCloud").isMissingNode());
            mvc.perform(post("/api/live/" + wcPin + "/answer").cookie(p2).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"questionIndex\":0,\"answer\":\"creativ\"}")).andExpect(status().isOk());
            var hostLive = json(mvc.perform(get("/api/live/" + wcPin).with(user(HOST))).andReturn());
            assertEquals(1, hostLive.path("wordCloud").size());
            assertEquals(2, hostLive.path("wordCloud").get(0).path("count").asInt());
            mvc.perform(post("/api/live/" + wcPin + "/answer").cookie(p3).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"questionIndex\":0,\"answer\":\"bold\"}")).andExpect(status().isOk());
            var q0Results = json(mvc.perform(get("/api/live/" + wcPin).cookie(p1)).andReturn());
            assertEquals("RESULTS", q0Results.path("phase").asText());
            assertEquals(2, q0Results.path("wordCloud").size());
            assertEquals("creative", q0Results.path("wordCloud").get(0).path("text").asText());
            assertEquals(2, q0Results.path("wordCloud").get(0).path("count").asInt());
            assertEquals("bold", q0Results.path("wordCloud").get(1).path("text").asText());
            assertEquals(0, q0Results.path("me").path("score").asInt());

            // Question 1 is set to EXACT: "creativ" and "Creative" stay separate
            mvc.perform(post("/api/live/" + wcPin + "/control").with(user(HOST)).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"action\":\"next\",\"questionIndex\":0}")).andExpect(status().isOk());
            wcRoom = rooms.findById(wcPin).orElseThrow();
            wcState = mapper.readValue(wcRoom.getStateJson(), LiveState.class);
            wcState.startedAt = System.currentTimeMillis() - 2000;
            wcState.endsAt = System.currentTimeMillis() + 18000;
            wcRoom.setStateJson(mapper.writeValueAsString(wcState));
            rooms.saveAndFlush(wcRoom);
            mvc.perform(post("/api/live/" + wcPin + "/answer").cookie(p1).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"questionIndex\":1,\"answer\":\"Creative\"}")).andExpect(status().isOk());
            mvc.perform(post("/api/live/" + wcPin + "/answer").cookie(p2).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"questionIndex\":1,\"answer\":\"creativ\"}")).andExpect(status().isOk());
            var q1Host = json(mvc.perform(get("/api/live/" + wcPin).with(user(HOST))).andReturn());
            assertEquals(2, q1Host.path("wordCloud").size());
        } finally {
            rooms.deleteById(wcPin);
            challenges.deleteById(wcChallengeId);
        }
    }
}
