package fr.darkvodou.facets.cover;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The arguments of //fixcover and of its brush: a radius, and flags that can be grouped (-res): -r (or -random) picks
 * a placement at random, -n -s -e -w -u -d exclude the blocks facing north, south, east, west, up and down: they forbid
 * holding on the block to the south, north, west, east, below and above (see {@link Cover.Face#flag()}). Each flag has
 * a long name, given alone: -random, -north, -south, -east, -west, -up, -down.
 *
 * @param radius   the radius as typed, null when missing
 * @param excluded the faces that never hold anything
 */
record CoverOptions(@Nullable String radius, boolean random, Set<Cover.Face> excluded) {

    static final String FLAGS = "[-r] [-n -s -e -w -u -d]";
    private static final List<String> FLAG_SUGGESTIONS = List.of("-r", "-n", "-s", "-e", "-w", "-u", "-d");
    private static final Map<String, Character> LONG_FLAGS = Map.of("-random", 'r', "-north", 'n', "-south", 's',
            "-east", 'e', "-west", 'w', "-up", 'u', "-down", 'd');

    /**
     * @return the letters of a flag argument (the one of its long name, or the grouped ones), null for another value
     */
    @Nullable
    private static String letters(String arg) {
        Character letter = LONG_FLAGS.get(arg);
        if (letter != null) {
            return String.valueOf(letter);
        }
        return arg.matches("-[a-z]+") ? arg.substring(1) : null;
    }

    /**
     * @return the options of the arguments from this index, null when they are invalid
     */
    @Nullable
    static CoverOptions parse(String[] args, int from) {
        String radius = null;
        boolean random = false;
        Set<Cover.Face> excluded = EnumSet.noneOf(Cover.Face.class);
        for (int i = from; i < args.length; i++) {
            String letters = letters(args[i].toLowerCase(Locale.ROOT));
            if (letters != null) {
                for (char letter : letters.toCharArray()) {
                    Cover.Face face = Cover.Face.ofFlag(letter);
                    if (letter == 'r') {
                        random = true;
                    } else if (face != null) {
                        excluded.add(face);
                    } else {
                        return null;
                    }
                }
            } else if (radius == null) {
                radius = args[i];
            } else {
                return null;
            }
        }
        return new CoverOptions(radius, random, excluded);
    }

    @Nullable
    Random randomizer() {
        return random ? new Random() : null;
    }

    /**
     * @return the options shown when a brush is bound, like ", random, without -n -e"
     */
    String describe() {
        StringBuilder text = new StringBuilder();
        if (random) {
            text.append(", random");
        }
        if (!excluded.isEmpty()) {
            text.append(", without ").append(excluded.stream().map(face -> "-" + face.flag())
                    .collect(Collectors.joining(" ")));
        }
        return text.toString();
    }

    /**
     * @param radii the radii suggested while none is typed, empty when the radius is optional and not suggested
     * @return the suggestions for the last argument, from this index
     */
    static List<String> complete(String[] args, int from, List<String> radii) {
        if (args.length <= from) {
            return List.of();
        }
        boolean hasRadius = false;
        StringBuilder flags = new StringBuilder();
        for (int i = from; i < args.length - 1; i++) {
            String letters = letters(args[i].toLowerCase(Locale.ROOT));
            if (letters != null) {
                flags.append(letters);
            } else {
                hasRadius = true;
            }
        }
        String last = args[args.length - 1].toLowerCase(Locale.ROOT);
        List<String> suggestions = new ArrayList<>();
        if (!hasRadius) {
            suggestions.addAll(radii);
        }
        for (String flag : FLAG_SUGGESTIONS) {
            if (flags.indexOf(flag.substring(1)) < 0) {
                suggestions.add(flag);
            }
        }
        // The long names once a word is started
        if (last.length() > 2) {
            LONG_FLAGS.keySet().stream().sorted()
                    .filter(name -> flags.indexOf(String.valueOf(LONG_FLAGS.get(name))) < 0)
                    .forEach(suggestions::add);
        }
        return suggestions.stream().filter(suggestion -> suggestion.startsWith(last)).toList();
    }
}
