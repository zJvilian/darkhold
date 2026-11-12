package com.quiz.darkhold.live;

import com.quiz.darkhold.analytics.entity.GameResult;
import com.quiz.darkhold.analytics.entity.ParticipantResult;
import com.quiz.darkhold.analytics.repository.GameResultRepository;
import com.quiz.darkhold.challenge.entity.Challenge;
import com.quiz.darkhold.challenge.entity.QuestionSet;
import com.quiz.darkhold.challenge.entity.QuestionType;
import com.quiz.darkhold.challenge.entity.SortingAnswer;
import com.quiz.darkhold.challenge.repository.ChallengeRepository;
import com.quiz.darkhold.challenge.service.ImageProcessingService;
import com.quiz.darkhold.game.repository.GameRepository;
import com.quiz.darkhold.game.service.AnswerValidationService;
import com.quiz.darkhold.init.GameConfig;
import com.quiz.darkhold.user.repository.UserRepository;
import com.quiz.darkhold.user.entity.DarkholdUserDetails;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.security.SecureRandom;
import java.security.Principal;
import org.springframework.security.core.Authentication;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@Transactional
public class LiveQuizService {
    public static final Map<String, String> AVATARS = Map.ofEntries(
            Map.entry("fox", "🦊"), Map.entry("panda", "🐼"), Map.entry("cat", "🐱"),
            Map.entry("frog", "🐸"), Map.entry("owl", "🦉"), Map.entry("penguin", "🐧"),
            Map.entry("lion", "🦁"), Map.entry("unicorn", "🦄"), Map.entry("robot", "🤖"),
            Map.entry("alien", "👽"), Map.entry("octopus", "🐙"), Map.entry("butterfly", "🦋"));
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Duration ROOM_LIFETIME = Duration.ofHours(24);
    private final LiveRoomRepository rooms;
    private final ChallengeRepository challenges;
    private final UserRepository users;
    private final GameRepository games;
    private final AnswerValidationService answers;
    private final GameConfig config;
    private final ObjectMapper mapper;
    private final org.springframework.context.MessageSource messageSource;
    private final GameResultRepository results;

    public LiveQuizService(final LiveRoomRepository rooms, final ChallengeRepository challenges,
                           final UserRepository users, final GameRepository games,
                           final AnswerValidationService answers, final GameConfig config,
                           final ObjectMapper mapper, final org.springframework.context.MessageSource messageSource,
                           final GameResultRepository results) {
        this.results = results;
        this.rooms = rooms;
        this.challenges = challenges;
        this.users = users;
        this.games = games;
        this.answers = answers;
        this.config = config;
        this.mapper = mapper;
        this.messageSource = messageSource;
    }

    public static String hostIdentity(final Principal principal) {
        if (principal instanceof Authentication auth && auth.getPrincipal() instanceof DarkholdUserDetails user) {
            return user.getEmail();
        }
        return principal == null ? null : principal.getName();
    }

    public String host(final long challengeId, final String host) {
        Challenge challenge = challenges.findById(challengeId)
                .orElseThrow(() -> failure(HttpStatus.NOT_FOUND, "live.error.quizNotFound"));
        var owner = users.findByEmail(host);
        require(owner != null && (Objects.equals(challenge.getChallengeOwner(), owner.getId())
                        || owner.canManageAllChallenges()),
                HttpStatus.FORBIDDEN, "live.error.ownerOnly");
        require(challenge.getQuestionSets() != null && !challenge.getQuestionSets().isEmpty(),
                HttpStatus.BAD_REQUEST, "live.error.noQuestions");
        LiveState state = new LiveState();
        state.challengeId = challengeId;
        state.title = challenge.getTitle();
        state.questions = new ArrayList<>(challenge.getQuestionSets());
        LiveRoom room = new LiveRoom();
        room.setPin(newPin());
        room.setHostName(host);
        room.setExpiresAt(System.currentTimeMillis() + ROOM_LIFETIME.toMillis());
        save(room, state);
        return room.getPin();
    }

    private String newPin() {
        String pin;
        do {
            pin = String.format(Locale.ROOT, "%06d", RANDOM.nextInt(1_000_000));
        } while (rooms.existsById(pin) || games.existsByPin(pin));
        return pin;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> roomsForHost(final String host) {
        return rooms.findByHostNameAndExpiresAtGreaterThanOrderByExpiresAtDesc(host, System.currentTimeMillis())
                .stream().map(room -> {
                    LiveState state = read(room);
                    return Map.<String, Object>of("pin", room.getPin(), "title", state.title,
                            "phase", state.phase, "players", state.players.values().stream()
                                    .filter(player -> !player.removed).count());
                }).toList();
    }

    @Scheduled(fixedDelay = 3600000, initialDelay = 3600000)
    public void removeExpiredRooms() {
        rooms.deleteByExpiresAtLessThan(System.currentTimeMillis());
    }

    public boolean terminate(final String pin, final String host, final boolean isAdmin) {
        var roomOpt = rooms.findById(pin);
        if (roomOpt.isEmpty()) {
            return false;
        }
        LiveRoom room = roomOpt.get();
        require(isAdmin || isHost(room, host), HttpStatus.FORBIDDEN, "live.error.hostOnly");
        rooms.delete(room);
        return true;
    }

    public void terminateAllForHost(final String host) {
        if (host == null || host.isBlank()) {
            return;
        }
        List<LiveRoom> hostRooms = rooms
                .findByHostNameAndExpiresAtGreaterThanOrderByExpiresAtDesc(host, 0);
        rooms.deleteAll(hostRooms);
    }

    public String join(final String pin, final String name, final String avatar, final String previousToken) {
        LiveRoom room = room(pin);
        LiveState state = read(room);
        if (previousToken != null && state.players.containsKey(previousToken)) {
            require(!state.players.get(previousToken).removed, HttpStatus.FORBIDDEN,
                    "live.error.removed");
            return previousToken;
        }
        validateJoin(state, name, avatar);
        String token = UUID.randomUUID().toString();
        LiveState.Player player = new LiveState.Player();
        player.name = name.strip();
        player.avatar = avatar;
        state.players.put(token, player);
        save(room, state);
        return token;
    }

    private void validateJoin(final LiveState state, final String name, final String avatar) {
        require("LOBBY".equals(state.phase) && !state.locked, HttpStatus.CONFLICT,
                "live.error.closed");
        require(name != null && name.strip().matches("[\\p{L}\\p{N}][\\p{L}\\p{N} _.'-]{1,19}"),
                HttpStatus.BAD_REQUEST, "live.error.nickname");
        require(avatar != null && AVATARS.containsKey(avatar), HttpStatus.BAD_REQUEST, "live.error.avatar");
        require(state.players.values().stream().noneMatch(player -> player.name.equalsIgnoreCase(name.strip())),
                HttpStatus.CONFLICT, "live.error.duplicateName");
        require(state.players.size() < 200, HttpStatus.CONFLICT, "live.error.full");
    }

    public Map<String, Object> state(final String pin, final String username, final String token) {
        LiveRoom room = room(pin);
        LiveState state = read(room);
        boolean host = isHost(room, username);
        LiveState.Player player = member(state, token, host);
        String previousPhase = state.phase;
        expireQuestion(state);
        if (!previousPhase.equals(state.phase)) {
            save(room, state);
        }
        return view(room, state, host, player);
    }

    public Map<String, Object> control(final String pin, final String username, final String action,
                                       final int questionIndex, final String playerName) {
        LiveRoom room = room(pin);
        require(isHost(room, username), HttpStatus.FORBIDDEN, "live.error.hostOnly");
        LiveState state = read(room);
        expireQuestion(state);
        require(questionIndex == state.questionIndex, HttpStatus.CONFLICT, "live.error.movedOn");
        applyControl(state, action, playerName);
        save(room, state);
        if ("FINISHED".equals(state.phase)) {
            // applyControl refuses a finished room, so this is the one moment the quiz ends
            results.save(result(room, state));
        }
        return view(room, state, true, null);
    }

    /** A finished live quiz is stored like any other game, so it shows up under Past Games. */
    private GameResult result(final LiveRoom room, final LiveState state) {
        GameResult result = new GameResult();
        result.setPin(room.getPin());
        result.setChallengeId(String.valueOf(state.challengeId));
        result.setChallengeName(state.title);
        result.setModerator(new DarkholdUserDetails(users.findByEmail(room.getHostName())).getUsername());
        result.setGameMode("MULTIPLAYER");
        result.setTotalQuestions(state.questions.size());
        LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
        // a room expires a fixed time after it opens, which tells us when it opened
        Duration played = Duration.ofMillis(
                ROOM_LIFETIME.toMillis() - (room.getExpiresAt() - System.currentTimeMillis()));
        result.setStartedAt(now.minus(played));
        result.setCompletedAt(now);
        result.setDurationMinutes((int) played.toMinutes());
        addParticipants(result, state);
        return result;
    }

    private void addParticipants(final GameResult result, final LiveState state) {
        List<LiveState.Player> ranked = state.players.values().stream().filter(player -> !player.removed)
                .sorted(Comparator.comparingInt((LiveState.Player player) -> player.score).reversed()).toList();
        result.setParticipantCount(ranked.size());
        for (int i = 0; i < ranked.size(); i++) {
            LiveState.Player player = ranked.get(i);
            // equal scores share a rank
            int rank = i > 0 && ranked.get(i - 1).score == player.score
                    ? result.getParticipantResults().get(i - 1).getFinalRank() : i + 1;
            result.getParticipantResults().add(participant(result, player, rank));
        }
        if (!ranked.isEmpty()) {
            result.setWinnerUsername(ranked.get(0).name);
            result.setWinnerScore(ranked.get(0).score);
        }
    }

    private ParticipantResult participant(final GameResult result, final LiveState.Player player, final int rank) {
        ParticipantResult participant = new ParticipantResult();
        participant.setGameResult(result);
        participant.setUsername(player.name);
        participant.setFinalScore(player.score);
        participant.setFinalRank(rank);
        participant.setCorrectAnswers(player.rightAnswers);
        participant.setIncorrectAnswers(player.wrongAnswers);
        participant.setMaxStreak(player.bestStreak);
        return participant;
    }

    public long activeRoomCount() {
        return rooms.findAll().stream().filter(room -> !"FINISHED".equals(read(room).phase)).count();
    }

    private void applyControl(final LiveState state, final String action, final String playerName) {
        require(action != null, HttpStatus.BAD_REQUEST, "live.error.chooseAction");
        require(!"FINISHED".equals(state.phase), HttpStatus.CONFLICT, "live.error.finished");
        switch (action) {
            case "start" -> start(state);
            case "reveal" -> {
                require("QUESTION".equals(state.phase), HttpStatus.CONFLICT, "live.error.noQuestion");
                state.phase = "RESULTS";
            }
            case "next" -> next(state);
            case "lock" -> state.locked = !state.locked;
            case "kick" -> kick(state, playerName);
            case "end" -> state.phase = "FINISHED";
            default -> throw failure(HttpStatus.BAD_REQUEST, "live.error.unknownAction");
        }
    }

    private void start(final LiveState state) {
        require("LOBBY".equals(state.phase), HttpStatus.CONFLICT, "live.error.started");
        require(state.players.values().stream().anyMatch(player -> !player.removed),
                HttpStatus.CONFLICT, "live.error.waitPlayer");
        openQuestion(state);
    }

    private void next(final LiveState state) {
        require("RESULTS".equals(state.phase), HttpStatus.CONFLICT, "live.error.revealFirst");
        if (state.questionIndex + 1 == state.questions.size()) {
            state.phase = "FINISHED";
        } else {
            openQuestion(state);
        }
    }

    private void openQuestion(final LiveState state) {
        state.questionIndex++;
        state.phase = "QUESTION";
        // Everyone receives the same short countdown, even when polling on slower phones.
        state.startedAt = System.currentTimeMillis() + 3000;
        state.endsAt = state.startedAt + timeLimit(state.questions.get(state.questionIndex)) * 1000L;
    }

    private void kick(final LiveState state, final String name) {
        require("LOBBY".equals(state.phase), HttpStatus.CONFLICT, "live.error.lobbyOnly");
        state.players.values().stream().filter(player -> player.name.equals(name))
                .forEach(player -> player.removed = true);
    }

    public Map<String, Object> answer(final String pin, final String token, final int questionIndex,
                                      final String answer) {
        LiveRoom room = room(pin);
        LiveState state = read(room);
        LiveState.Player player = member(state, token, false);
        require(questionIndex == state.questionIndex, HttpStatus.CONFLICT, "live.error.questionEnded");
        if (player.answeredQuestion == questionIndex) {
            return view(room, state, false, player);
        }
        require("QUESTION".equals(state.phase) && System.currentTimeMillis() >= state.startedAt
                && System.currentTimeMillis() < state.endsAt, HttpStatus.CONFLICT, "live.error.answersClosed");
        grade(state, player, answer);
        expireQuestion(state);
        save(room, state);
        return view(room, state, false, player);
    }

    private void grade(final LiveState state, final LiveState.Player player, final String answer) {
        require(answer != null && !answer.isBlank() && answer.length() <= 500,
                HttpStatus.BAD_REQUEST, "live.error.answerRequired");
        QuestionSet question = state.questions.get(state.questionIndex);
        String normalized = normalizeAnswer(question, answer);
        player.answer = normalized;
        player.correct = answers.validateAnswer(question, normalized);
        player.streak = player.correct && player.answeredQuestion == state.questionIndex - 1 ? player.streak + 1
                : (player.correct ? 1 : 0);
        double remaining = Math.max(0, state.endsAt - System.currentTimeMillis());
        double factor = 0.5 + 0.5 * remaining / (state.endsAt - state.startedAt);
        player.earned = player.correct && answers.shouldScore(question)
                ? (int) Math.round(answers.getPoints(question) * factor) : 0;
        player.score += player.earned;
        player.answeredQuestion = state.questionIndex;
        player.tally(answers.shouldScore(question));
    }

    private String normalizeAnswer(final QuestionSet question, final String answer) {
        if (question.getQuestionType() == QuestionType.TYPE_ANSWER
                || question.getQuestionType() == QuestionType.WORD_CLOUD) {
            return answer.strip();
        }
        if (question.getQuestionType() == QuestionType.SORTING) {
            require(SortingAnswer.isValid(answer, question.getSortingAnswers()),
                    HttpStatus.BAD_REQUEST, "live.error.invalidAnswer");
            return SortingAnswer.normalize(answer);
        }
        List<String> selections = Arrays.stream(answer.toUpperCase(Locale.ROOT).split(",", -1))
                .map(String::strip).distinct().sorted().toList();
        Map<String, String> options = options(question);
        require(!selections.isEmpty() && options.keySet().containsAll(selections),
                HttpStatus.BAD_REQUEST, "live.error.invalidAnswer");
        if (question.getQuestionType() != QuestionType.MULTIPLE_CHOICE) {
            require(selections.size() == 1, HttpStatus.BAD_REQUEST, "live.error.singleAnswer");
        }
        return String.join(",", selections);
    }

    private void expireQuestion(final LiveState state) {
        boolean allAnswered = state.players.values().stream().filter(player -> !player.removed)
                .allMatch(player -> player.answeredQuestion == state.questionIndex);
        if ("QUESTION".equals(state.phase) && (System.currentTimeMillis() >= state.endsAt || allAnswered)) {
            state.phase = "RESULTS";
        }
    }

    private Map<String, Object> view(final LiveRoom room, final LiveState state, final boolean host,
                                     final LiveState.Player player) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("pin", room.getPin());
        result.put("title", state.title);
        result.put("host", host);
        result.put("phase", state.phase);
        result.put("locked", state.locked);
        result.put("questionIndex", state.questionIndex);
        result.put("totalQuestions", state.questions.size());
        result.put("serverTime", System.currentTimeMillis());
        result.put("startedAt", state.startedAt);
        result.put("endsAt", state.endsAt);
        result.put("players", leaderboard(state));
        result.put("answeredCount", state.players.values().stream()
                .filter(item -> !item.removed && item.answeredQuestion == state.questionIndex
                        && state.questionIndex >= 0).count());
        addQuestionView(result, state, player);
        return result;
    }

    private List<Map<String, Object>> leaderboard(final LiveState state) {
        return state.players.values().stream().filter(player -> !player.removed)
                .sorted(Comparator.comparingInt((LiveState.Player player) -> visibleScore(state, player)).reversed())
                .map(player -> Map.<String, Object>of("name", player.name, "avatar", player.avatar,
                        "emoji", AVATARS.get(player.avatar), "score", visibleScore(state, player))).toList();
    }

    private int visibleScore(final LiveState state, final LiveState.Player player) {
        return "QUESTION".equals(state.phase) && player.answeredQuestion == state.questionIndex
                ? player.score - player.earned : player.score;
    }

    private void addQuestionView(final Map<String, Object> result, final LiveState state,
                                  final LiveState.Player player) {
        if (player != null) {
            result.put("me", Map.of("name", player.name, "avatar", player.avatar,
                    "emoji", AVATARS.get(player.avatar), "score", visibleScore(state, player),
                    "answered", state.questionIndex >= 0 && player.answeredQuestion == state.questionIndex));
        }
        if (state.questionIndex < 0) {
            return;
        }
        QuestionSet question = state.questions.get(state.questionIndex);
        result.put("question", questionView(question));
        boolean answered = player != null && player.answeredQuestion == state.questionIndex;
        if (question.getQuestionType() == QuestionType.WORD_CLOUD
                && (Boolean.TRUE.equals(result.get("host")) || !"QUESTION".equals(state.phase) || answered)) {
            result.put("wordCloud", wordCloud(state, question));
        }
        if ("RESULTS".equals(state.phase) || "FINISHED".equals(state.phase)) {
            result.put("correctAnswer", correctAnswer(question));
            result.put("distribution", distribution(state, question));
            addFeedback(result, state, player, question);
        }
    }

    private Map<String, Object> questionView(final QuestionSet question) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("text", question.getQuestion());
        result.put("type", question.getQuestionType());
        result.put("options", options(question));
        result.put("multiple", question.getQuestionType() == QuestionType.MULTIPLE_CHOICE
                && question.getCorrectOptions() != null && question.getCorrectOptions().contains(","));
        boolean audio = ImageProcessingService.isAudioUrl(question.getImageUrl());
        result.put("image", audio ? null : question.getImageUrl());
        result.put("audio", audio && ImageProcessingService.supportsAudio(question.getQuestionType())
                ? question.getImageUrl() : null);
        result.put("video", question.getVideoUrl());
        result.put("timeLimit", timeLimit(question));
        return result;
    }

    private void addFeedback(final Map<String, Object> result, final LiveState state,
                              final LiveState.Player player, final QuestionSet question) {
        if (player != null) {
            boolean answered = player.answeredQuestion == state.questionIndex;
            result.put("feedback", Map.of("answered", answered, "correct", answered && player.correct,
                    "earned", answered ? player.earned : 0, "streak", answered ? player.streak : 0,
                    "poll", question.getQuestionType() == QuestionType.POLL
                            || question.getQuestionType() == QuestionType.WORD_CLOUD));
        }
    }

    private Map<String, Long> distribution(final LiveState state, final QuestionSet question) {
        Map<String, Long> counts = new LinkedHashMap<>();
        options(question).keySet().forEach(option -> counts.put(option, state.players.values().stream()
                .filter(player -> !player.removed && player.answeredQuestion == state.questionIndex
                        && Arrays.asList(player.answer.split(",", -1)).contains(option)).count()));
        return counts;
    }

    private List<Map<String, Object>> wordCloud(final LiveState state, final QuestionSet question) {
        boolean fuzzy = !"EXACT".equalsIgnoreCase(question.getCorrectOptions());
        List<String> submitted = state.players.values().stream()
                .filter(p -> !p.removed && p.answeredQuestion == state.questionIndex && p.answer != null)
                .map(p -> p.answer).toList();
        return answers.buildWordCloud(submitted, fuzzy);
    }

    private String correctAnswer(final QuestionSet question) {
        if (question.getQuestionType() == QuestionType.POLL
                || question.getQuestionType() == QuestionType.WORD_CLOUD) {
            return "";
        }
        return question.getQuestionType() == QuestionType.TYPE_ANSWER
                ? question.getAnswer1() : question.getCorrectOptions();
    }

    private Map<String, String> options(final QuestionSet question) {
        if (question.getQuestionType() == QuestionType.TYPE_ANSWER
                || question.getQuestionType() == QuestionType.WORD_CLOUD) {
            return Map.of();
        }
        Map<String, String> options = new LinkedHashMap<>();
        if (question.getQuestionType() == QuestionType.TRUE_FALSE) {
            options.put("TRUE", "True");
            options.put("FALSE", "False");
        } else {
            String[] labels = question.getQuestionType() == QuestionType.SORTING ? question.getSortingAnswers()
                    : new String[]{question.getAnswer1(), question.getAnswer2(),
                    question.getAnswer3(), question.getAnswer4()};
            for (int index = 0; index < labels.length; index++) {
                if (labels[index] != null && !labels[index].isBlank()) {
                    options.put(String.valueOf((char) ('A' + index)), labels[index]);
                }
            }
        }
        return options;
    }

    private int timeLimit(final QuestionSet question) {
        int seconds = question.getTimeLimit() == null ? config.getTimerSeconds() : question.getTimeLimit();
        return Math.max(5, Math.min(240, seconds));
    }

    private LiveState.Player member(final LiveState state, final String token, final boolean host) {
        LiveState.Player player = token == null ? null : state.players.get(token);
        require(host || (player != null && !player.removed), HttpStatus.FORBIDDEN,
                "live.error.joinFirst");
        return player;
    }

    private boolean isHost(final LiveRoom room, final String username) {
        return username != null && room.getHostName().equals(username);
    }

    private LiveRoom room(final String pin) {
        require(pin != null && pin.matches("[0-9]{6}"), HttpStatus.NOT_FOUND, "live.error.invalidCode");
        LiveRoom room = rooms.lockByPin(pin)
                .orElseThrow(() -> failure(HttpStatus.NOT_FOUND, "live.error.roomNotFound"));
        require(room.getExpiresAt() > System.currentTimeMillis(), HttpStatus.GONE, "live.error.expired");
        return room;
    }

    private LiveState read(final LiveRoom room) {
        return mapper.readValue(room.getStateJson(), LiveState.class);
    }

    private void save(final LiveRoom room, final LiveState state) {
        room.setStateJson(mapper.writeValueAsString(state));
        rooms.save(room);
    }

    private void require(final boolean condition, final HttpStatus status, final String message) {
        if (!condition) {
            throw failure(status, message);
        }
    }

    public String message(final String key) {
        return messageSource.getMessage(key, null, org.springframework.context.i18n.LocaleContextHolder.getLocale());
    }

    private ResponseStatusException failure(final HttpStatus status, final String message) {
        return new ResponseStatusException(status, message(message));
    }
}
