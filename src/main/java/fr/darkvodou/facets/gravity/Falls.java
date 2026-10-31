package fr.darkvodou.facets.gravity;

import java.util.Set;
import java.util.function.IntPredicate;

/**
 * Which blocks fall, and where the falling blocks of a column land.
 */
final class Falls {

    private static final Set<String> FALLING = Set.of("sand", "red_sand", "suspicious_sand", "gravel",
            "suspicious_gravel", "anvil", "chipped_anvil", "damaged_anvil", "dragon_egg");
    private static final String POWDER = "_concrete_powder";

    private Falls() {
    }

    /**
     * @param id a block id, with or without its namespace
     */
    static boolean falls(String id) {
        String name = name(id);
        return FALLING.contains(name) || name.endsWith(POWDER);
    }

    /**
     * @return the concrete a concrete powder becomes in water (red_concrete for red_concrete_powder), else null
     */
    static String concrete(String id) {
        String name = name(id);
        return name.endsWith(POWDER) ? name.substring(0, name.length() - POWDER.length()) + "_concrete" : null;
    }

    private static String name(String id) {
        int colon = id.indexOf(':');
        return colon < 0 ? id : id.substring(colon + 1);
    }

    /**
     * Drops the falling blocks of a column, from the lowest one: each falls through the free positions and stops on
     * the first block that is not, or on the falling block below it.
     *
     * @param ys   the heights of the falling blocks, sorted from the lowest, without duplicates
     * @param minY the bottom of the world
     * @param free whether a falling block goes through the position (air, water, grass...). Only asked for positions
     *             that are not in ys: those are free once their block has fallen
     * @return the height each block lands at, below minY when it falls out of the world
     */
    static int[] land(int[] ys, int minY, IntPredicate free) {
        int[] landings = new int[ys.length];
        for (int i = 0; i < ys.length; i++) {
            // Below the previous block everything is known: it landed on the first block that is not free
            int stop = i == 0 ? minY - 1 : ys[i - 1];
            int y = ys[i] - 1;
            while (y > stop && free.test(y)) {
                y--;
            }
            if (y > stop) {
                landings[i] = y + 1;
            } else if (i == 0 || landings[i - 1] < minY) {
                landings[i] = minY - 1;
            } else {
                landings[i] = landings[i - 1] + 1;
            }
        }
        return landings;
    }
}
