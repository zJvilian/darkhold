package com.quiz.darkhold.live;

import com.quiz.darkhold.challenge.entity.QuestionSet;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Stored privately in the room; never serialize this object into a client response.
public class LiveState {
    public long challengeId;
    public String title;
    public List<QuestionSet> questions = new ArrayList<>();
    public Map<String, Player> players = new LinkedHashMap<>();
    public String phase = "LOBBY";
    public int questionIndex = -1;
    public long startedAt;
    public long endsAt;
    public boolean locked;
    /** Answer statistics per played question, stored with the result when the quiz ends. */
    public List<Round> rounds = new ArrayList<>();

    public static class Round {
        public int correct;
        public int incorrect;
        public long totalMillis;
        public long fastestMillis = -1;

        void record(final boolean right, final long millis) {
            if (right) {
                correct++;
            } else {
                incorrect++;
            }
            totalMillis += millis;
            fastestMillis = fastestMillis < 0 ? millis : Math.min(fastestMillis, millis);
        }
    }

    public static class Player {
        public String name;
        public String avatar;
        public int score;
        public int streak;
        public int answeredQuestion = -1;
        public String answer;
        public boolean correct;
        public int earned;
        public boolean removed;
        public int rightAnswers;
        public int wrongAnswers;
        public int bestStreak;

        /** Running totals for the stored result; questions without points (polls) count as neither. */
        void tally(final boolean scored) {
            if (scored && correct) {
                rightAnswers++;
            } else if (scored) {
                wrongAnswers++;
            }
            bestStreak = Math.max(bestStreak, streak);
        }
    }
}
