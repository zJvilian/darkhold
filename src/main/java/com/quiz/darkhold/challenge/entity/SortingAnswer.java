package com.quiz.darkhold.challenge.entity;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Order-preserving parsing and validation for sorting answers. */
public final class SortingAnswer {
    private SortingAnswer() {
    }

    public static String normalize(final String order) {
        return order == null ? "" : String.join(",", Arrays.stream(order.toUpperCase(Locale.ROOT).split(",", -1))
                .map(String::strip).toList());
    }

    public static boolean isValid(final String order, final String... answers) {
        Set<String> available = new HashSet<>();
        for (int index = 0; index < answers.length; index++) {
            if (answers[index] != null && !answers[index].isBlank()) {
                available.add(String.valueOf((char) ('A' + index)));
            }
        }
        var selected = Arrays.asList(normalize(order).split(",", -1));
        return available.size() >= 2 && answers.length <= 10 && selected.size() == available.size()
                && new HashSet<>(selected).equals(available);
    }
}
