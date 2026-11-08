package za.co.neroland.nerocompanion.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import net.minecraft.core.BlockPos;

import org.junit.jupiter.api.Test;

/** Environmental immunity and the safe-spot search used on unperch, teleport and when stuck in a block. */
class CompanionSafetyTest {

    @Test
    void neverDiesFromWaterWallsOrFalls() {
        for (boolean sleeping : new boolean[] {false, true}) {
            assertTrue(CompanionSafety.isImmune(CompanionSafety.Hazard.DROWNING, sleeping));
            assertTrue(CompanionSafety.isImmune(CompanionSafety.Hazard.IN_WALL, sleeping));
            assertTrue(CompanionSafety.isImmune(CompanionSafety.Hazard.FALL, sleeping));
            assertFalse(CompanionSafety.isImmune(CompanionSafety.Hazard.OTHER, sleeping));
        }
    }

    @Test
    void fireAndContactOnlyWhileAsleep() {
        assertTrue(CompanionSafety.isImmune(CompanionSafety.Hazard.FIRE, true));
        assertTrue(CompanionSafety.isImmune(CompanionSafety.Hazard.CONTACT, true));
        assertFalse(CompanionSafety.isImmune(CompanionSafety.Hazard.FIRE, false));
        assertFalse(CompanionSafety.isImmune(CompanionSafety.Hazard.CONTACT, false));
    }

    @Test
    void candidatesAreNearestFirstAndLevelBeforeUpBeforeDown() {
        BlockPos center = new BlockPos(10, 64, 10);
        List<BlockPos> ring0 = SafeSpot.candidates(center, 0, 0, 1);
        assertEquals(List.of(center, center.above(), center.below()), ring0);
        List<BlockPos> all = SafeSpot.candidates(center, 1, 2, 0);
        assertEquals(8 + 16, all.size());
        assertTrue(all.subList(0, 8).stream().allMatch(p -> Math.max(Math.abs(p.getX() - 10),
                Math.abs(p.getZ() - 10)) == 1));
    }

    @Test
    void findReturnsTheFirstSafeSpotOrNothing() {
        BlockPos center = BlockPos.ZERO;
        Set<BlockPos> safe = Set.of(new BlockPos(3, 1, 0), new BlockPos(1, 0, 1));
        assertEquals(Optional.of(new BlockPos(1, 0, 1)), SafeSpot.find(center, 1, 3, 1, safe::contains));
        assertTrue(SafeSpot.find(center, 1, 3, 1, pos -> false).isEmpty());
        // minRing excludes the centre (unperch steps off next to the owner, not into them).
        assertTrue(SafeSpot.find(center, 1, 1, 0, pos -> pos.equals(center)).isEmpty());
    }
}
