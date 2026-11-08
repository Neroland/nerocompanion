package za.co.neroland.nerocompanion.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

/** Drop routing: own cargo, then carry bot, then bound container, then owner; leftovers stay with the bot. */
class DropRouterTest {

    /** A sink with a capacity; stacks are plain counts. */
    private static final class Box implements DropRouter.Sink<Integer> {
        final DropRouter.Stage stage;
        final List<DropRouter.Stage> log;
        final boolean available;
        int room;
        int got;

        Box(DropRouter.Stage stage, int room, boolean available, List<DropRouter.Stage> log) {
            this.stage = stage;
            this.room = room;
            this.available = available;
            this.log = log;
        }

        @Override
        public boolean available() {
            return this.available;
        }

        @Override
        public Integer offer(Integer stack) {
            this.log.add(this.stage);
            int taken = Math.min(this.room, stack);
            this.room -= taken;
            this.got += taken;
            return stack - taken;
        }
    }

    private static Map<DropRouter.Stage, Box> boxes(List<DropRouter.Stage> log, int cargo, int carry, int container,
            boolean containerAllowed, int owner) {
        Map<DropRouter.Stage, Box> sinks = new EnumMap<>(DropRouter.Stage.class);
        sinks.put(DropRouter.Stage.OWNER, new Box(DropRouter.Stage.OWNER, owner, true, log));
        sinks.put(DropRouter.Stage.BOUND_CONTAINER, new Box(DropRouter.Stage.BOUND_CONTAINER, container,
                containerAllowed, log));
        sinks.put(DropRouter.Stage.CARRY_BOT, new Box(DropRouter.Stage.CARRY_BOT, carry, true, log));
        sinks.put(DropRouter.Stage.OWN_CARGO, new Box(DropRouter.Stage.OWN_CARGO, cargo, true, log));
        return sinks;
    }

    @Test
    void stagesAreOfferedInOrderUntilTheStackIsGone() {
        List<DropRouter.Stage> log = new ArrayList<>();
        Map<DropRouter.Stage, Box> sinks = boxes(log, 10, 5, 5, true, 100);
        int left = DropRouter.route(25, sinks, n -> n == 0);
        assertEquals(0, left);
        assertEquals(List.of(DropRouter.Stage.OWN_CARGO, DropRouter.Stage.CARRY_BOT,
                DropRouter.Stage.BOUND_CONTAINER, DropRouter.Stage.OWNER), log);
        assertEquals(10, sinks.get(DropRouter.Stage.OWN_CARGO).got);
        assertEquals(5, sinks.get(DropRouter.Stage.OWNER).got);
    }

    @Test
    void ownCargoFirstAndNothingElseWhenItFits() {
        List<DropRouter.Stage> log = new ArrayList<>();
        DropRouter.route(3, boxes(log, 64, 64, 64, true, 64), n -> n == 0);
        assertEquals(List.of(DropRouter.Stage.OWN_CARGO), log);
    }

    @Test
    void aContainerTheOwnerMayNotOpenIsSkipped() {
        List<DropRouter.Stage> log = new ArrayList<>();
        Map<DropRouter.Stage, Box> sinks = boxes(log, 0, 0, 64, false, 64);
        assertEquals(0, DropRouter.route(8, sinks, n -> n == 0));
        assertEquals(0, sinks.get(DropRouter.Stage.BOUND_CONTAINER).got);
        assertEquals(8, sinks.get(DropRouter.Stage.OWNER).got);
    }

    @Test
    void whatNobodyTakesIsReturnedToDropAtTheBot() {
        List<DropRouter.Stage> log = new ArrayList<>();
        Map<DropRouter.Stage, Box> sinks = new EnumMap<>(DropRouter.Stage.class);
        sinks.put(DropRouter.Stage.OWN_CARGO, new Box(DropRouter.Stage.OWN_CARGO, 2, true, log));
        assertEquals(6, DropRouter.route(8, sinks, n -> n == 0));
    }

    @Test
    void aFullBotUnloadsToTheFirstReachableStage() {
        assertEquals(Optional.of(DropRouter.Stage.CARRY_BOT), DropRouter.unloadTarget(EnumSet.allOf(
                DropRouter.Stage.class)));
        assertEquals(Optional.of(DropRouter.Stage.BOUND_CONTAINER), DropRouter.unloadTarget(EnumSet.of(
                DropRouter.Stage.OWNER, DropRouter.Stage.BOUND_CONTAINER)));
        assertEquals(Optional.of(DropRouter.Stage.OWNER), DropRouter.unloadTarget(EnumSet.of(
                DropRouter.Stage.OWNER, DropRouter.Stage.OWN_CARGO)));
        assertTrue(DropRouter.unloadTarget(EnumSet.of(DropRouter.Stage.OWN_CARGO)).isEmpty());
    }
}
