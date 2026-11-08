package za.co.neroland.nerocompanion.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import net.minecraft.core.BlockPos;

/**
 * Finds a safe block to put a companion in: used by teleport-to-owner, on unperch, and to free a companion that
 * finds itself inside a block. The search order is fixed (nearest first, same height before above before below)
 * so the result is predictable; what counts as safe is the caller's predicate (the entity checks the world).
 * Pure apart from {@link BlockPos}, so it is unit-tested.
 */
public final class SafeSpot {

    private SafeSpot() {
    }

    /**
     * Candidate positions around {@code center}, nearest first: horizontal rings from {@code minRing} to
     * {@code maxRing} (Chebyshev distance), each at dy = 0, +1, -1, ... up to {@code maxDy}.
     */
    public static List<BlockPos> candidates(BlockPos center, int minRing, int maxRing, int maxDy) {
        List<BlockPos> out = new ArrayList<>();
        for (int ring = minRing; ring <= maxRing; ring++) {
            for (int step = 0; step <= 2 * maxDy; step++) {
                int dy = (step + 1) / 2 * (step % 2 == 1 ? 1 : -1);
                for (int dx = -ring; dx <= ring; dx++) {
                    for (int dz = -ring; dz <= ring; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) == ring) {
                            out.add(center.offset(dx, dy, dz));
                        }
                    }
                }
            }
        }
        return out;
    }

    /** The first candidate the predicate accepts. */
    public static Optional<BlockPos> find(BlockPos center, int minRing, int maxRing, int maxDy,
            Predicate<BlockPos> safe) {
        for (BlockPos candidate : candidates(center, minRing, maxRing, maxDy)) {
            if (safe.test(candidate)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }
}
