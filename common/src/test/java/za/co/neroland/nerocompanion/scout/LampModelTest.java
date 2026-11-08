package za.co.neroland.nerocompanion.scout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;

/** The Lamp: one light at most, only in air, moved with hysteresis, never forgotten while unloaded. */
class LampModelTest {

    /** A toy 1-D world: positions are ints; each is air, our light or another block; some may be unloaded. */
    private static final class Toy implements LampModel.World<Integer> {
        final Map<Integer, String> blocks = new HashMap<>();
        final Set<Integer> denied = new HashSet<>();
        final Set<Integer> unloaded = new HashSet<>();
        final List<Integer> orphans = new ArrayList<>();
        int placements;
        int maxLights;

        String at(int pos) {
            return this.blocks.getOrDefault(pos, "air");
        }

        long lights() {
            return this.blocks.values().stream().filter("light"::equals).count();
        }

        @Override
        public boolean isLoaded(Integer pos) {
            return !this.unloaded.contains(pos);
        }

        @Override
        public boolean isAir(Integer pos) {
            return "air".equals(at(pos));
        }

        @Override
        public boolean isOurLight(Integer pos) {
            return "light".equals(at(pos));
        }

        @Override
        public boolean mayPlace(Integer pos) {
            return !this.denied.contains(pos);
        }

        @Override
        public boolean place(Integer pos) {
            this.blocks.put(pos, "light");
            this.placements++;
            this.maxLights = (int) Math.max(this.maxLights, lights());
            return true;
        }

        @Override
        public void remove(Integer pos) {
            this.blocks.remove(pos);
        }

        @Override
        public int distance(Integer a, Integer b) {
            return Math.abs(a - b);
        }

        @Override
        public void orphan(Integer pos) {
            this.orphans.add(pos);
        }
    }

    @Test
    void placesOnlyIntoLoadedAirAndOnlyWhereAllowed() {
        Toy world = new Toy();
        LampModel<Integer> lamp = new LampModel<>();
        world.blocks.put(1, "stone");
        assertFalse(lamp.update(1, 0, world));
        assertEquals("stone", world.at(1));
        world.denied.add(2);
        assertFalse(lamp.update(2, 100, world));
        world.unloaded.add(4);
        assertFalse(lamp.update(4, 200, world));
        assertEquals(0, world.lights());
        assertTrue(lamp.update(3, 300, world));
        assertEquals(Optional.of(3), lamp.lit());
        assertEquals("light", world.at(3));
    }

    @Test
    void oneBlockOfDriftWaitsTenStableTicks() {
        Toy world = new Toy();
        LampModel<Integer> lamp = new LampModel<>();
        lamp.update(0, 0, world);
        assertFalse(lamp.update(1, 20, world), "one block over, not yet settled");
        assertFalse(lamp.update(1, 29, world));
        assertTrue(lamp.update(1, 30, world), "settled for 10 ticks");
        assertEquals("air", world.at(0));
        assertEquals("light", world.at(1));
    }

    @Test
    void aMovingDroneRelightsAtMostOncePerTenTicks() {
        Toy world = new Toy();
        LampModel<Integer> lamp = new LampModel<>();
        // A block every 2 ticks for 200 ticks.
        for (int t = 0; t <= 200; t++) {
            lamp.update(t / 2, t, world);
            assertTrue(world.lights() <= 1);
        }
        assertTrue(world.placements <= 21, "placements " + world.placements);
        assertTrue(world.placements >= 10, "still follows the drone");
        assertEquals(1, world.maxLights);
    }

    @Test
    void aTwoBlockJumpMovesAsSoonAsTheIntervalAllows() {
        Toy world = new Toy();
        LampModel<Integer> lamp = new LampModel<>();
        lamp.update(0, 0, world);
        assertFalse(lamp.update(5, 5, world), "interval not yet over");
        assertTrue(lamp.update(5, 10, world));
        assertEquals("light", world.at(5));
        assertEquals(1, world.lights());
    }

    @Test
    void turningOffIsImmediateAndClearForgets() {
        Toy world = new Toy();
        LampModel<Integer> lamp = new LampModel<>();
        lamp.update(5, 0, world);
        assertTrue(lamp.update(null, 1, world));
        assertEquals(0, world.lights());
        lamp.update(6, 50, world);
        assertTrue(lamp.clear(world));
        assertEquals(0, world.lights());
        assertFalse(lamp.clear(world), "second clear is a no-op");
    }

    @Test
    void aBlockThePlayerReplacedIsNeverBroken() {
        Toy world = new Toy();
        LampModel<Integer> lamp = new LampModel<>();
        lamp.update(7, 0, world);
        world.blocks.put(7, "torch");
        lamp.update(9, 50, world);
        assertEquals("torch", world.at(7));
        lamp.clear(world);
        world.blocks.put(8, "glass");
        lamp.remember(8);
        lamp.clear(world);
        assertEquals("glass", world.at(8));
    }

    @Test
    void anUnloadedLightIsOrphanedNotForgotten() {
        Toy world = new Toy();
        LampModel<Integer> lamp = new LampModel<>();
        lamp.update(3, 0, world);
        world.unloaded.add(3);
        lamp.update(40, 100, world);
        assertEquals(List.of(3), world.orphans);
        assertEquals("light", world.at(3), "nothing touched an unloaded chunk");
        assertEquals(Optional.of(40), lamp.lit());
        world.unloaded.add(40);
        assertTrue(lamp.clear(world));
        assertEquals(List.of(3, 40), world.orphans);
    }

    @Test
    void aRememberedStaleLightIsCleanedUp() {
        Toy world = new Toy();
        world.blocks.put(9, "light");
        LampModel<Integer> lamp = new LampModel<>();
        lamp.remember(9);
        lamp.update(20, 0, world);
        assertEquals("air", world.at(9));
        assertEquals(1, world.lights());
    }

    @Test
    void aLongRandomFlightNeverLeavesMoreThanOneLight() {
        Toy world = new Toy();
        LampModel<Integer> lamp = new LampModel<>();
        Random random = new Random(42L);
        int pos = 20;
        for (int step = 0; step < 5_000; step++) {
            int r = random.nextInt(20);
            if (r == 0) {
                lamp.clear(world);
            } else if (r == 1) {
                lamp.update(null, step, world);
            } else {
                pos = Math.max(0, Math.min(40, pos + random.nextInt(3) - 1));
                if (random.nextInt(16) == 0) {
                    world.blocks.put(random.nextInt(40), "stone");
                }
                lamp.update(pos, step, world);
            }
            assertTrue(world.lights() <= 1);
            assertEquals(lamp.lit().isPresent() ? 1 : 0, world.lights());
        }
        lamp.clear(world);
        assertEquals(0, world.lights());
        assertEquals(1, world.maxLights);
    }
}
