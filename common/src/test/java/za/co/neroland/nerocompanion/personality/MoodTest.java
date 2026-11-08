package za.co.neroland.nerocompanion.personality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerocompanion.bond.BondRules;

/** Mood state is keyed by companion id, so a new entity for the same companion cannot reset cooldowns. */
class MoodTest {

    @Test
    void recallRedeployCannotFarmPets() {
        Mood.Store store = new Mood.Store();
        UUID companion = UUID.randomUUID();
        long now = 10_000L;
        int points = 0;
        // pet -> recall -> redeploy (a new entity, same companion id) -> pet, every ~3 s for a minute.
        for (int cycle = 0; cycle < 20; cycle++) {
            Mood mood = store.get(companion, now);
            points += mood.bond().pet(now);
            now += 60L;
        }
        assertEquals(BondRules.POINTS_PER_PET, points);
        // Exactly one cycle: pet, then the "new entity" pets inside the cooldown.
        Mood.Store fresh = new Mood.Store();
        int first = fresh.get(companion, 0L).bond().pet(0L);
        int second = fresh.get(companion, 60L).bond().pet(60L);
        assertEquals(BondRules.POINTS_PER_PET, first + second);
    }

    @Test
    void parkUnparkKeepsJobCooldown() {
        Mood.Store store = new Mood.Store();
        UUID companion = UUID.randomUUID();
        assertEquals(BondRules.POINTS_PER_JOB, store.get(companion, 0L).bond().job(0L));
        assertEquals(0, store.get(companion, 100L).bond().job(100L));
    }

    @Test
    void entriesAreSharedPerIdAndEvictedWhenIdle() {
        Mood.Store store = new Mood.Store();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        Mood moodA = store.get(a, 0L);
        assertSame(moodA, store.get(a, 10L));
        store.get(b, 10L);
        assertEquals(2, store.size());
        // b stays in use; a is untouched for over an hour and goes on the next sweep.
        long later = Mood.EVICT_AFTER + 2 * Mood.SWEEP_EVERY;
        store.get(b, later - Mood.SWEEP_EVERY);
        store.get(b, later);
        assertEquals(1, store.size());
        assertNotSame(moodA, store.get(a, later));
    }
}
