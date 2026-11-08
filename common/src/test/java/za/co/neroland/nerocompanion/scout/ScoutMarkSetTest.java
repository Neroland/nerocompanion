package za.co.neroland.nerocompanion.scout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerocompanion.network.payload.Payloads;

/** Scout marks are capped at 32, radius-limited, deduplicated and nearest first. */
class ScoutMarkSetTest {

    private static List<ScoutMarkSet.Candidate> line(int count, int kind, int y) {
        List<ScoutMarkSet.Candidate> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            list.add(new ScoutMarkSet.Candidate(new BlockPos(i % 16, y, i / 16), kind, i));
        }
        return list;
    }

    @Test
    void neverMoreThanThirtyTwo() {
        List<Payloads.Mark> marks = ScoutMarkSet.build(line(100, Payloads.Mark.HOSTILE, 0),
                line(200, Payloads.Mark.ORE, 1), 16);
        assertEquals(32, ScoutMarkSet.MAX_MARKS);
        assertEquals(32, marks.size());
        // An over-asking caller is still clamped.
        assertEquals(32, ScoutMarkSet.build(List.of(), line(200, Payloads.Mark.ORE, 1), 16, 500).size());
    }

    @Test
    void hostilesTakeAtMostHalfWhileOresWait() {
        List<Payloads.Mark> marks = ScoutMarkSet.build(line(40, Payloads.Mark.HOSTILE, 0),
                line(40, Payloads.Mark.ORE, 1), 16);
        long hostiles = marks.stream().filter(m -> m.kind() == Payloads.Mark.HOSTILE).count();
        assertEquals(16, hostiles);
        assertEquals(Payloads.Mark.HOSTILE, marks.get(0).kind());
        // Without ores, hostiles may fill the whole budget.
        assertEquals(32, ScoutMarkSet.build(line(40, Payloads.Mark.HOSTILE, 0), List.of(), 16).size());
        // Few ores: left-over budget goes back to hostiles.
        List<Payloads.Mark> mixed = ScoutMarkSet.build(line(40, Payloads.Mark.HOSTILE, 0),
                line(3, Payloads.Mark.ORE, 1), 16);
        assertEquals(32, mixed.size());
        assertEquals(3, mixed.stream().filter(m -> m.kind() == Payloads.Mark.ORE).count());
    }

    @Test
    void outsideTheRadiusIsDroppedAndNearestComesFirst() {
        List<ScoutMarkSet.Candidate> ores = List.of(
                new ScoutMarkSet.Candidate(new BlockPos(0, 0, 9), Payloads.Mark.ORE, 81),
                new ScoutMarkSet.Candidate(new BlockPos(0, 0, 20), Payloads.Mark.ORE, 400),
                new ScoutMarkSet.Candidate(new BlockPos(0, 0, 2), Payloads.Mark.ORE, 4));
        List<Payloads.Mark> marks = ScoutMarkSet.build(List.of(), ores, 16);
        assertEquals(2, marks.size());
        assertEquals(new BlockPos(0, 0, 2), marks.get(0).pos());
        assertEquals(new BlockPos(0, 0, 9), marks.get(1).pos());
    }

    @Test
    void duplicatePositionsAreMarkedOnce() {
        BlockPos pos = new BlockPos(3, 4, 5);
        List<Payloads.Mark> marks = ScoutMarkSet.build(
                List.of(new ScoutMarkSet.Candidate(pos, Payloads.Mark.HOSTILE, 1),
                        new ScoutMarkSet.Candidate(pos, Payloads.Mark.HOSTILE, 1)),
                List.of(new ScoutMarkSet.Candidate(pos, Payloads.Mark.ORE, 1)), 16);
        assertEquals(1, marks.size());
        assertTrue(new Payloads.ScoutMarks(marks, ScoutMarkSet.TTL_TICKS).ttlTicks() == 40);
    }
}
