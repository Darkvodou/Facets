package fr.darkvodou.facets.speed;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SpeedCommandTest {

    private static final float DELTA = 1e-6f;

    @Test
    void oneIsTheUsualSpeed() {
        assertEquals(SpeedCommand.FLY_BASE, SpeedCommand.toMinecraft(1, SpeedCommand.FLY_BASE), DELTA);
        assertEquals(SpeedCommand.WALK_BASE, SpeedCommand.toMinecraft(1, SpeedCommand.WALK_BASE), DELTA);
    }

    @Test
    void belowOneIsAShareOfTheUsualSpeed() {
        assertEquals(0.05f, SpeedCommand.toMinecraft(0.5f, SpeedCommand.FLY_BASE), DELTA);
        assertEquals(0.1f, SpeedCommand.toMinecraft(0.5f, SpeedCommand.WALK_BASE), DELTA);
        assertEquals(0, SpeedCommand.toMinecraft(0, SpeedCommand.WALK_BASE), DELTA);
    }

    @Test
    void tenIsTheFastest() {
        assertEquals(1, SpeedCommand.toMinecraft(SpeedCommand.MAX, SpeedCommand.FLY_BASE), DELTA);
        assertEquals(1, SpeedCommand.toMinecraft(SpeedCommand.MAX, SpeedCommand.WALK_BASE), DELTA);
    }

    @Test
    void growsEvenlyPastOne() {
        // Flight: 0.1 per step
        assertEquals(0.2f, SpeedCommand.toMinecraft(2, SpeedCommand.FLY_BASE), DELTA);
        assertEquals(0.55f, SpeedCommand.toMinecraft(5.5f, SpeedCommand.FLY_BASE), DELTA);
        float walk = SpeedCommand.toMinecraft(5, SpeedCommand.WALK_BASE);
        assertEquals(0.2f + 4 / 9f * 0.8f, walk, DELTA);
    }
}
