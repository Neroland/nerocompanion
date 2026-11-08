package za.co.neroland.nerocompanion.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;

import org.junit.jupiter.api.Test;

/** Mining area: the cap, its clamping, the visiting order, and the job's persistence. */
class MineAreaTest {

    private static final Identifier OVERWORLD = Identifier.fromNamespaceAndPath("minecraft", "overworld");

    @Test
    void aSmallSelectionIsKeptAsIs() {
        MineArea area = MineArea.of(new BlockPos(0, 64, 0), new BlockPos(2, 62, -3), 9);
        assertEquals(3, area.sizeX());
        assertEquals(3, area.sizeY());
        assertEquals(4, area.sizeZ());
        assertFalse(MineArea.wouldClamp(new BlockPos(0, 64, 0), new BlockPos(2, 62, -3), 9));
    }

    @Test
    void anOversizedSelectionIsClampedTowardTheFirstCorner() {
        BlockPos first = new BlockPos(10, 70, 10);
        BlockPos second = new BlockPos(40, 20, -30);
        MineArea area = MineArea.of(first, second, 9);
        assertEquals(9, area.sizeX());
        assertEquals(9, area.sizeY());
        assertEquals(9, area.sizeZ());
        assertTrue(area.contains(first));
        assertEquals(new BlockPos(10, 62, 2), area.min());
        assertEquals(new BlockPos(18, 70, 10), area.max());
        assertTrue(MineArea.wouldClamp(first, second, 9));
    }

    @Test
    void theConfiguredEdgeIsClampedToOneThroughSixteen() {
        assertEquals(16, MineArea.clampEdge(40));
        assertEquals(1, MineArea.clampEdge(0));
        assertEquals(1, MineArea.clampEdge(-5));
        MineArea huge = MineArea.of(BlockPos.ZERO, new BlockPos(100, 100, 100), 64);
        assertEquals(16 * 16 * 16, huge.volume());
        MineArea tiny = MineArea.of(BlockPos.ZERO, new BlockPos(100, 100, 100), 0);
        assertEquals(1, tiny.volume());
    }

    @Test
    void blocksAreVisitedTopLayerFirstAndEachExactlyOnce() {
        MineArea area = MineArea.of(new BlockPos(0, 0, 0), new BlockPos(2, 2, 2), 9);
        assertEquals(2, area.at(0).getY());
        assertEquals(0, area.at(area.volume() - 1).getY());
        Set<BlockPos> seen = new HashSet<>();
        int lastY = Integer.MAX_VALUE;
        for (int i = 0; i < area.volume(); i++) {
            BlockPos pos = area.at(i);
            assertTrue(area.contains(pos));
            assertTrue(pos.getY() <= lastY);
            lastY = pos.getY();
            seen.add(pos);
        }
        assertEquals(27, seen.size());
    }

    @Test
    void aStoredOversizedAreaDoesNotDecode() {
        MineArea huge = new MineArea(BlockPos.ZERO, new BlockPos(199, 199, 199));
        assertFalse(huge.withinCap());
        // A tampered save: encode a legal job, then widen its stored box.
        net.minecraft.nbt.CompoundTag tag = (net.minecraft.nbt.CompoundTag) MineJob.CODEC.encodeStart(NbtOps.INSTANCE,
                MineJob.start(OVERWORLD, MineArea.of(BlockPos.ZERO, BlockPos.ZERO, 9))).getOrThrow();
        net.minecraft.nbt.CompoundTag box = (net.minecraft.nbt.CompoundTag) tag.get("area");
        box.put("max", BlockPos.CODEC.encodeStart(NbtOps.INSTANCE, huge.max()).getOrThrow());
        assertTrue(MineJob.CODEC.parse(NbtOps.INSTANCE, tag).error().isPresent());
        assertTrue(MineArea.of(BlockPos.ZERO, new BlockPos(99, 99, 99), 16).withinCap());
    }

    @Test
    void theJobRoundTripsAndClampsItsCursor() {
        MineArea area = MineArea.of(new BlockPos(1, 2, 3), new BlockPos(4, 5, 6), 9);
        MineJob job = new MineJob(OVERWORLD, area, 5, 3, 2);
        Tag tag = MineJob.CODEC.encodeStart(NbtOps.INSTANCE, job).getOrThrow();
        assertEquals(job, MineJob.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow());
        assertEquals(area.volume(), new MineJob(OVERWORLD, area, 9999, 0, 0).cursor());
        assertTrue(new MineJob(OVERWORLD, area, 9999, 0, 0).finished());
        assertEquals(MineJob.TYPE, job.jobType());
    }
}
