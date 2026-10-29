package fr.darkvodou.moretools.loft;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Command flags: one letter each (-n), combinable (-nu), each also having a long name (-net).
 */
final class Flags {

    private final Map<Character, String> names = new LinkedHashMap<>();

    /**
     * @param letterThenName alternating letters and long names: 'n', "net", 'u', "uniform"
     */
    Flags(Object... letterThenName) {
        for (int i = 0; i + 1 < letterThenName.length; i += 2) {
            names.put((Character) letterThenName[i], (String) letterThenName[i + 1]);
        }
    }

    /**
     * @return the letters of a flag argument (unknown letters included, to be reported),
     * or null if the argument is not a flag
     */
    @Nullable
    String parse(String arg) {
        if (arg.length() < 2 || arg.charAt(0) != '-') {
            return null;
        }
        String name = arg.substring(1).toLowerCase(Locale.ROOT);
        for (Map.Entry<Character, String> entry : names.entrySet()) {
            if (entry.getValue().equals(name)) {
                return String.valueOf(entry.getKey());
            }
        }
        return name.chars().allMatch(Character::isLetter) ? name : null;
    }

    boolean isKnown(char letter) {
        return names.containsKey(letter);
    }

    /**
     * Completions for a flag being typed: the short and long form of each unused flag,
     * and the typed letters followed by another one (-n → -np).
     */
    List<String> complete(String typed, Set<Character> used) {
        if (!typed.startsWith("-")) {
            return List.of();
        }
        String lower = typed.toLowerCase(Locale.ROOT);
        String letters = lower.substring(1);
        boolean combination = !letters.isEmpty() && letters.chars().allMatch(c -> isKnown((char) c));
        List<String> options = new ArrayList<>();
        List<String> combinations = new ArrayList<>();
        if (combination && letters.length() > 1) {
            options.add(typed);
        }
        for (Map.Entry<Character, String> entry : names.entrySet()) {
            char letter = entry.getKey();
            if (used.contains(letter)) {
                continue;
            }
            String shortForm = "-" + letter;
            String longForm = "-" + entry.getValue();
            if (shortForm.startsWith(lower)) {
                options.add(shortForm);
            }
            if (longForm.startsWith(lower)) {
                options.add(longForm);
            }
            if (combination && letters.indexOf(letter) < 0) {
                combinations.add(typed + letter);
            }
        }
        options.addAll(combinations);
        return options;
    }

    /**
     * Unused flags in their short and long form, when nothing is typed yet.
     */
    List<String> all(Set<Character> used) {
        return complete("-", used);
    }
}
