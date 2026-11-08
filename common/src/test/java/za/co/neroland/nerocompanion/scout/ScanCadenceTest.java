package za.co.neroland.nerocompanion.scout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

/** Ore scans: at most every 40 ticks, skipped when nothing changed, time-sliced nearest first. */
class ScanCadenceTest {

    @Test
    void firstScanStartsAtOnce() {
        assertTrue(new ScanCadence().shouldStart(0L, 7L, 16, true));
    }

    @Test
    void neverMoreOftenThanFortyTicks() {
        ScanCadence cadence = new ScanCadence();
        cadence.started(100L, 1L, 16);
        assertFalse(cadence.shouldStart(110L, 2L, 16, true));
        assertFalse(cadence.shouldStart(139L, 2L, 16, true));
        assertTrue(cadence.shouldStart(140L, 2L, 16, false), "moved a block");
    }

    @Test
    void skippedWhenNothingChangedUntilTheRefresh() {
        ScanCadence cadence = new ScanCadence();
        cadence.started(0L, 5L, 16);
        assertFalse(cadence.shouldStart(40L, 5L, 16, false));
        assertFalse(cadence.shouldStart(199L, 5L, 16, false));
        assertTrue(cadence.shouldStart(200L, 5L, 16, false), "periodic refresh");
        assertTrue(cadence.shouldStart(40L, 5L, 16, true), "a cached ore was mined");
        assertTrue(cadence.shouldStart(40L, 5L, 18, false), "Range chip changed the radius");
    }

    @Test
    void sectionPlanIsNearestFirstAndSliced() {
        SectionPlan plan = new SectionPlan(8, 8, 8, 16);
        List<SectionPlan.Section> all = plan.all();
        assertEquals(new SectionPlan.Section(0, 0, 0, 0.0D), all.get(0));
        for (int i = 1; i < all.size(); i++) {
            assertTrue(all.get(i - 1).distanceSq() <= all.get(i).distanceSq());
            assertTrue(all.get(i).distanceSq() <= 256.0D);
        }
        Set<SectionPlan.Section> seen = new HashSet<>();
        int slices = 0;
        while (!plan.done()) {
            List<SectionPlan.Section> slice = plan.nextSlice(ScanCadence.SECTIONS_PER_STEP);
            assertTrue(slice.size() <= ScanCadence.SECTIONS_PER_STEP && !slice.isEmpty());
            seen.addAll(slice);
            slices++;
        }
        assertEquals(all.size(), seen.size());
        assertEquals((all.size() + ScanCadence.SECTIONS_PER_STEP - 1) / ScanCadence.SECTIONS_PER_STEP, slices);
        assertTrue(plan.nextSlice(4).isEmpty());
    }

    @Test
    void cornerSectionsOutsideTheSphereAreSkipped() {
        // Centre at a section corner: the far corner sections of the 3x3x3 box lie beyond radius 16.
        SectionPlan plan = new SectionPlan(0, 0, 0, 16);
        assertTrue(plan.all().size() < 27);
    }
}
