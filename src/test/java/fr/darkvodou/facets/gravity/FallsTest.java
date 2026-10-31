package fr.darkvodou.facets.gravity;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.function.IntPredicate;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FallsTest {

    private static final int MIN_Y = -64;

    /**
     * @return free everywhere except at the given heights
     */
    private static IntPredicate solidAt(Integer... ys) {
        Set<Integer> solid = Set.of(ys);
        return y -> !solid.contains(y);
    }

    @Test
    void fallingBlocks() {
        assertTrue(Falls.falls("minecraft:sand"));
        assertTrue(Falls.falls("gravel"));
        assertTrue(Falls.falls("minecraft:red_concrete_powder"));
        assertTrue(Falls.falls("minecraft:damaged_anvil"));
        assertFalse(Falls.falls("minecraft:sandstone"));
        assertFalse(Falls.falls("minecraft:red_concrete"));
        assertFalse(Falls.falls("minecraft:stone"));
    }

    @Test
    void powderBecomesConcrete() {
        assertEquals("light_blue_concrete", Falls.concrete("minecraft:light_blue_concrete_powder"));
        assertNull(Falls.concrete("minecraft:sand"));
    }

    @Test
    void landsOnTheFirstBlock() {
        assertArrayEquals(new int[]{61}, Falls.land(new int[]{70}, MIN_Y, solidAt(60)));
    }

    @Test
    void staysOnABlock() {
        assertArrayEquals(new int[]{61}, Falls.land(new int[]{61}, MIN_Y, solidAt(60)));
    }

    @Test
    void stacksOnTheBlockBelow() {
        assertArrayEquals(new int[]{61, 62, 63}, Falls.land(new int[]{70, 71, 75}, MIN_Y, solidAt(60)));
    }

    @Test
    void stopsOnABlockBetween() {
        // Stone at 72: the block at 75 stops on it, not on the sand below
        assertArrayEquals(new int[]{61, 73}, Falls.land(new int[]{70, 75}, MIN_Y, solidAt(60, 72)));
    }

    @Test
    void landsWhereTheBlockBelowLeft() {
        assertArrayEquals(new int[]{69, 70}, Falls.land(new int[]{70, 71}, MIN_Y, solidAt(68)));
    }

    @Test
    void fallsOutOfTheWorld() {
        int[] landings = Falls.land(new int[]{MIN_Y, MIN_Y + 5}, MIN_Y, solidAt());
        assertTrue(landings[0] < MIN_Y);
        assertTrue(landings[1] < MIN_Y);
    }

    @Test
    void staysOnTheBottomBlock() {
        assertArrayEquals(new int[]{MIN_Y + 1}, Falls.land(new int[]{MIN_Y + 10}, MIN_Y, solidAt(MIN_Y)));
    }
}
