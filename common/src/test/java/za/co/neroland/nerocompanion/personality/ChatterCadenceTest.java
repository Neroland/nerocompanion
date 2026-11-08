package za.co.neroland.nerocompanion.personality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Chatter cooldown maths and the Chatterbox / Hush / Silent Running chips. */
class ChatterCadenceTest {

    @Test
    void volumeFromChips() {
        assertEquals(ChatterVolume.NORMAL, ChatterVolume.of(false, false, false));
        assertEquals(ChatterVolume.CHATTY, ChatterVolume.of(false, true, false));
        assertEquals(ChatterVolume.HUSHED, ChatterVolume.of(false, false, true));
        assertEquals(ChatterVolume.HUSHED, ChatterVolume.of(false, true, true));
        assertEquals(ChatterVolume.SILENT, ChatterVolume.of(true, true, false));
    }

    @Test
    void chatterboxHalvesAndHushQuadruplesCooldowns() {
        int base = ChatterEvent.IDLE.baseCooldown();
        assertEquals(base, ChatterCadence.eventCooldown(ChatterEvent.IDLE, ChatterVolume.NORMAL));
        assertEquals(base / 2, ChatterCadence.eventCooldown(ChatterEvent.IDLE, ChatterVolume.CHATTY));
        assertEquals(base * 4, ChatterCadence.eventCooldown(ChatterEvent.IDLE, ChatterVolume.HUSHED));
        assertEquals(Integer.MAX_VALUE, ChatterCadence.eventCooldown(ChatterEvent.IDLE, ChatterVolume.SILENT));
        assertEquals(ChatterCadence.GLOBAL_COOLDOWN / 2, ChatterCadence.globalCooldown(ChatterVolume.CHATTY));
    }

    @Test
    void idleChanceScalesWithFrequency() {
        double base = ChatterCadence.IDLE_CHANCE;
        assertEquals(base, ChatterCadence.idleChance(ChatterVolume.NORMAL), 1e-12);
        assertEquals(base * 2, ChatterCadence.idleChance(ChatterVolume.CHATTY), 1e-12);
        assertEquals(base / 4, ChatterCadence.idleChance(ChatterVolume.HUSHED), 1e-12);
        assertEquals(0.0D, ChatterCadence.idleChance(ChatterVolume.SILENT), 1e-12);
    }

    @Test
    void firstLineIsAlwaysReadyUnlessSilent() {
        assertTrue(ChatterCadence.ready(ChatterEvent.PETTED, ChatterVolume.NORMAL, 0L, Long.MIN_VALUE,
                Long.MIN_VALUE));
        assertFalse(ChatterCadence.ready(ChatterEvent.PETTED, ChatterVolume.SILENT, 0L, Long.MIN_VALUE,
                Long.MIN_VALUE));
    }

    @Test
    void globalAndEventCooldownsBothApply() {
        long last = 1000L;
        int global = ChatterCadence.GLOBAL_COOLDOWN;
        int job = ChatterEvent.JOB_DONE.baseCooldown();
        // Another event spoke 1 tick ago: blocked by the global gap.
        assertFalse(ChatterCadence.ready(ChatterEvent.JOB_DONE, ChatterVolume.NORMAL, last + 1, last, Long.MIN_VALUE));
        assertTrue(ChatterCadence.ready(ChatterEvent.JOB_DONE, ChatterVolume.NORMAL, last + global, last,
                Long.MIN_VALUE));
        // Same event: its own cooldown.
        assertFalse(ChatterCadence.ready(ChatterEvent.JOB_DONE, ChatterVolume.NORMAL, last + job - 1, last, last));
        assertTrue(ChatterCadence.ready(ChatterEvent.JOB_DONE, ChatterVolume.NORMAL, last + job, last, last));
        // Chatty: half the wait; hushed: four times.
        assertTrue(ChatterCadence.ready(ChatterEvent.JOB_DONE, ChatterVolume.CHATTY, last + job / 2, last, last));
        assertFalse(ChatterCadence.ready(ChatterEvent.JOB_DONE, ChatterVolume.HUSHED, last + job, last, last));
        assertTrue(ChatterCadence.ready(ChatterEvent.JOB_DONE, ChatterVolume.HUSHED, last + job * 4, last, last));
    }

    @Test
    void petsAnswerQuicklyButNotEveryClick() {
        long last = 1000L;
        // Another line 1 tick ago does not hold a pet back...
        assertTrue(ChatterCadence.ready(ChatterEvent.PETTED, ChatterVolume.NORMAL, last + 1, last, Long.MIN_VALUE));
        // ...but pets have their own short cooldown.
        int petted = ChatterEvent.PETTED.baseCooldown();
        assertFalse(ChatterCadence.ready(ChatterEvent.PETTED, ChatterVolume.NORMAL, last + petted - 1, last, last));
        assertTrue(ChatterCadence.ready(ChatterEvent.PETTED, ChatterVolume.NORMAL, last + petted, last, last));
    }

    @Test
    void bondUpIgnoresTheGlobalGap() {
        assertTrue(ChatterCadence.ready(ChatterEvent.BOND_UP, ChatterVolume.NORMAL, 1001L, 1000L, Long.MIN_VALUE));
        assertFalse(ChatterCadence.ready(ChatterEvent.BOND_UP, ChatterVolume.SILENT, 1001L, 1000L,
                Long.MIN_VALUE));
    }
}
