package za.co.neroland.nerocompanion.scout;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerocompanion.network.payload.Payloads;

/**
 * One scout's incremental ore scan (server). Call {@link #step} on the {@value ScanCadence#HOSTILE_TICKS}-tick cadence:
 * it re-checks the cached ores (at most {@value #MAX_FOUND} block reads), starts a new scan only when
 * {@link ScanCadence} says so, and walks at most {@value ScanCadence#SECTIONS_PER_STEP} chunk sections per step,
 * nearest first. Sections that are all air or whose palette holds no ore ({@code maybeHas}) are skipped; ore-ness is
 * cached per block state; chunks are read with {@code getChunkNow} and never loaded. The finished scan replaces the
 * cache; the marks use the cache, re-measured from the drone's current position.
 */
public final class OreScan {

    public static final int MAX_FOUND = 2 * ScoutMarkSet.MAX_MARKS;
    private static final int CACHE_RESET_TICKS = 6000;

    private final ScanCadence cadence = new ScanCadence();
    private final ScoutOres.Cache ores = new ScoutOres.Cache();
    private List<BlockPos> found = List.of();
    private boolean everFinished;
    @Nullable
    private SectionPlan plan;
    private BlockPos planCenter = BlockPos.ZERO;
    private int planRadius;
    private final List<BlockPos> building = new ArrayList<>();
    private long nextCacheReset = Long.MIN_VALUE;

    /** Advances the scan one step at game tick {@code now}. */
    public void step(ServerLevel level, BlockPos center, int radius, long now) {
        if (now >= this.nextCacheReset) {
            this.nextCacheReset = now + CACHE_RESET_TICKS;
            this.ores.clear();
        }
        boolean invalid = !this.everFinished || this.dropMined(level);
        if (this.plan == null && this.cadence.shouldStart(now, center.asLong(), radius, invalid)) {
            this.cadence.started(now, center.asLong(), radius);
            this.plan = new SectionPlan(center.getX(), center.getY(), center.getZ(), radius);
            this.planCenter = center.immutable();
            this.planRadius = radius;
            this.building.clear();
        }
        SectionPlan current = this.plan;
        if (current == null) {
            return;
        }
        for (SectionPlan.Section section : current.nextSlice(ScanCadence.SECTIONS_PER_STEP)) {
            if (this.building.size() >= MAX_FOUND) {
                break;
            }
            this.scanSection(level, section);
        }
        if (current.done() || this.building.size() >= MAX_FOUND) {
            this.found = List.copyOf(this.building);
            this.building.clear();
            this.plan = null;
            this.everFinished = true;
        }
    }

    /** The cached ores within {@code radius} of {@code center}, as mark candidates. */
    public List<ScoutMarkSet.Candidate> candidates(BlockPos center, int radius) {
        List<ScoutMarkSet.Candidate> out = new ArrayList<>(this.found.size());
        double r2 = (double) radius * radius;
        for (BlockPos pos : this.found) {
            double d2 = pos.distSqr(center);
            if (d2 <= r2) {
                out.add(new ScoutMarkSet.Candidate(pos, Payloads.Mark.ORE, d2));
            }
        }
        return out;
    }

    /** Forget the scan (the goal stopped). */
    public void reset() {
        this.cadence.reset();
        this.plan = null;
        this.found = List.of();
        this.building.clear();
        this.everFinished = false;
    }

    /** Drops cached ores that are no longer ore (or no longer loaded). True if any was dropped. */
    private boolean dropMined(ServerLevel level) {
        if (this.found.isEmpty()) {
            return false;
        }
        List<BlockPos> kept = new ArrayList<>(this.found.size());
        for (BlockPos pos : this.found) {
            if (level.isLoaded(pos) && this.ores.isOre(level.getBlockState(pos))) {
                kept.add(pos);
            }
        }
        if (kept.size() == this.found.size()) {
            return false;
        }
        this.found = List.copyOf(kept);
        return true;
    }

    private void scanSection(ServerLevel level, SectionPlan.Section s) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(s.sx(), s.sz());
        if (chunk == null) {
            return;
        }
        int baseY = s.sy() << 4;
        int index = chunk.getSectionIndex(baseY);
        LevelChunkSection[] all = chunk.getSections();
        if (index < 0 || index >= all.length) {
            return;
        }
        LevelChunkSection section = all[index];
        if (section == null || section.hasOnlyAir() || !section.maybeHas(this.ores::isOre)) {
            return;
        }
        BlockPos c = this.planCenter;
        int r2 = this.planRadius * this.planRadius;
        int baseX = s.sx() << 4;
        int baseZ = s.sz() << 4;
        for (int ly = 0; ly < 16; ly++) {
            int dy = baseY + ly - c.getY();
            if (dy * dy > r2) {
                continue;
            }
            for (int lz = 0; lz < 16; lz++) {
                int dz = baseZ + lz - c.getZ();
                if (dy * dy + dz * dz > r2) {
                    continue;
                }
                for (int lx = 0; lx < 16; lx++) {
                    int dx = baseX + lx - c.getX();
                    if (dx * dx + dy * dy + dz * dz > r2) {
                        continue;
                    }
                    BlockState state = section.getBlockState(lx, ly, lz);
                    if (this.ores.isOre(state)) {
                        this.building.add(new BlockPos(baseX + lx, baseY + ly, baseZ + lz));
                        if (this.building.size() >= MAX_FOUND) {
                            return;
                        }
                    }
                }
            }
        }
    }
}
