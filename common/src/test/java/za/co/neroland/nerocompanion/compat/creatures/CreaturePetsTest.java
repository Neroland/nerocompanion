package za.co.neroland.nerocompanion.compat.creatures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The per-owner NeroCreatures pet list the SPI fills (memory only, erasable, bounded). */
class CreaturePetsTest {

    private static final UUID ALICE = new UUID(0L, 1L);
    private static final UUID BOB = new UUID(0L, 2L);

    private static UUID pet(long n) {
        return new UUID(1L, n);
    }

    @BeforeEach
    @AfterEach
    void reset() {
        CreaturePets.clear();
    }

    @Test
    void tameCommandRelease() {
        CreaturePets.tamed(ALICE, pet(1), "nerocreatures:glacite_wisp");
        CreaturePets.tamed(ALICE, pet(2), "nerocreatures:xertz_forager");
        assertEquals(2, CreaturePets.petsOf(ALICE).size());
        assertEquals("follow", CreaturePets.petsOf(ALICE).get(0).command());
        CreaturePets.command(pet(1), "sit");
        CreaturePets.command(pet(1), "dance");   // unknown command ignored
        assertEquals("sit", CreaturePets.petsOf(ALICE).get(0).command());
        CreaturePets.released(pet(1));
        assertEquals(List.of(pet(2)), CreaturePets.petsOf(ALICE).stream().map(CreaturePets.Pet::petId).toList());
        CreaturePets.released(pet(99));   // unknown: no-op
        assertEquals(1, CreaturePets.size());
    }

    @Test
    void retamingMovesThePetToTheNewOwner() {
        CreaturePets.tamed(ALICE, pet(1), "nerocreatures:glacite_wisp");
        CreaturePets.tamed(BOB, pet(1), "nerocreatures:glacite_wisp");
        assertTrue(CreaturePets.petsOf(ALICE).isEmpty());
        assertEquals(Optional.of(BOB), CreaturePets.ownerOf(pet(1)));
    }

    @Test
    void erasureForgetsOnlyThatOwner() {
        CreaturePets.tamed(ALICE, pet(1), "a");
        CreaturePets.tamed(ALICE, pet(2), "a");
        CreaturePets.tamed(BOB, pet(3), "a");
        assertEquals(2, CreaturePets.forgetOwner(ALICE));
        assertEquals(0, CreaturePets.forgetOwner(ALICE));
        assertTrue(CreaturePets.ownerOf(pet(1)).isEmpty());
        assertEquals(1, CreaturePets.size());
    }

    @Test
    void boundedPerOwner() {
        for (int i = 0; i < CreaturePets.MAX_PER_OWNER + 5; i++) {
            CreaturePets.tamed(ALICE, pet(i), "a");
        }
        assertEquals(CreaturePets.MAX_PER_OWNER, CreaturePets.petsOf(ALICE).size());
        assertEquals(CreaturePets.MAX_PER_OWNER, CreaturePets.size());
        assertEquals(pet(5), CreaturePets.petsOf(ALICE).get(0).petId(), "oldest dropped first");
    }
}
