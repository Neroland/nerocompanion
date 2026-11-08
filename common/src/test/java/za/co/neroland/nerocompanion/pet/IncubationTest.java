package za.co.neroland.nerocompanion.pet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerocompanion.incubation.Incubation;

/** Scrap Egg incubation timing: five minutes of incubator ticks, whatever the incubator's period. */
class IncubationTest {

    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-00000000000a");

    @Test
    void hatchesAfterExactlyFiveMinutesOfTicks() {
        assertEquals(6000, Incubation.HATCH_TICKS);
        Incubation egg = Incubation.start(OWNER, 0);
        int calls = 0;
        while (!egg.ready()) {
            egg = egg.advance(5);   // the Pad and Bay tick every 5 ticks
            calls++;
        }
        assertEquals(1200, calls);
        assertEquals(Incubation.HATCH_TICKS, egg.elapsedTicks());
        assertFalse(Incubation.start(OWNER, 0).advance(5999).ready());
        assertTrue(Incubation.start(OWNER, 0).advance(6000).ready());
    }

    @Test
    void progressIsClampedAndNeverRunsBackwards() {
        Incubation egg = Incubation.start(OWNER, 0).advance(100_000);
        assertEquals(Incubation.HATCH_TICKS, egg.elapsedTicks());
        assertEquals(0, egg.remainingTicks());
        Incubation half = Incubation.start(OWNER, 3000);
        assertSame(half, half.advance(0));
        assertSame(half, half.advance(-50));
        assertEquals(0, Incubation.start(OWNER, -10).elapsedTicks());
        assertEquals(0.5F, half.progress(), 1.0E-6F);
    }

    @Test
    void eggCarriesProgressToANewIncubator() {
        Incubation resumed = Incubation.start(OWNER, 4500);
        assertEquals(1500, resumed.remainingTicks());
        assertTrue(resumed.advance(1500).ready());
    }

    @Test
    void stagesFollowTheTextureThresholds() {
        assertEquals(0, Incubation.stageOf(0.0F));
        assertEquals(0, Incubation.stageOf(0.49F));
        assertEquals(1, Incubation.stageOf(0.5F));
        assertEquals(1, Incubation.stageOf(0.89F));
        assertEquals(2, Incubation.stageOf(0.9F));
        assertEquals(2, Incubation.stageOf(Incubation.progressOf(6000)));
    }

    @Test
    void codecRoundTripsOwnerAndTicks() {
        Incubation egg = Incubation.start(OWNER, 1234);
        JsonElement json = Incubation.CODEC.encodeStart(JsonOps.INSTANCE, egg).getOrThrow();
        assertEquals(egg, Incubation.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
    }
}
