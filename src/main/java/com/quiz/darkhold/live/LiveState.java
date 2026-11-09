package com.quiz.darkhold.live;

import com.quiz.darkhold.challenge.entity.QuestionSet;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Stored privately in the room; never serialize this object into a client response.
public class LiveState {
    public String title;
    public List<QuestionSet> questions = new ArrayList<>();
    public Map<String, Player> players = new LinkedHashMap<>();
    public String phase = "LOBBY";
    public int questionIndex = -1;
    public long startedAt;
    public long endsAt;
    public boolean locked;

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
    }
}
