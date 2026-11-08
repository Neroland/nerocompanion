package za.co.neroland.nerocompanion.scout;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.core.BlockPos;

import za.co.neroland.nerocompanion.network.payload.Payloads;

/**
 * Builds the owner's scout marks from what one scan found (pure). Numbers: radius {@value #RADIUS} blocks around the
 * drone (plus the Range chip), at most {@value #MAX_MARKS} marks, rebuilt every {@value #REBUILD_TICKS} ticks, each
 * batch living {@value #TTL_TICKS} ticks on the client unless refreshed.
 *
 * <p>Order: hostiles first (nearest first, at most half the budget while ores are waiting), then ores nearest first,
 * then any hostiles left over. Duplicate positions are dropped; anything outside the radius is dropped.
 */
public final class ScoutMarkSet {

    public static final int RADIUS = 16;
    public static final int MAX_MARKS = Payloads.ScoutMarks.MAX_MARKS;
    public static final int TTL_TICKS = 40;
    public static final int REBUILD_TICKS = 10;

    /** One thing a scan found: its block position, its mark kind and its squared distance to the drone. */
    public record Candidate(BlockPos pos, int kind, double distanceSq) {
    }

    private ScoutMarkSet() {
    }

    public static List<Payloads.Mark> build(List<Candidate> hostiles, List<Candidate> ores, double radius) {
        return build(hostiles, ores, radius, MAX_MARKS);
    }

    public static List<Payloads.Mark> build(List<Candidate> hostiles, List<Candidate> ores, double radius, int max) {
        int cap = Math.max(0, Math.min(MAX_MARKS, max));
        double limit = radius * radius;
        List<Candidate> h = sorted(hostiles, limit);
        List<Candidate> o = sorted(ores, limit);
        List<Payloads.Mark> out = new ArrayList<>(Math.min(cap, h.size() + o.size()));
        Set<BlockPos> seen = new HashSet<>();
        int hostileBudget = o.isEmpty() ? cap : cap / 2;
        int hi = 0;
        for (; hi < h.size() && out.size() < hostileBudget; hi++) {
            add(out, seen, h.get(hi), cap);
        }
        for (Candidate c : o) {
            add(out, seen, c, cap);
        }
        for (; hi < h.size(); hi++) {
            add(out, seen, h.get(hi), cap);
        }
        return List.copyOf(out);
    }

    private static List<Candidate> sorted(List<Candidate> in, double limit) {
        List<Candidate> list = new ArrayList<>();
        for (Candidate c : in) {
            if (c != null && c.distanceSq() <= limit) {
                list.add(c);
            }
        }
        list.sort(Comparator.comparingDouble(Candidate::distanceSq));
        return list;
    }

    private static void add(List<Payloads.Mark> out, Set<BlockPos> seen, Candidate c, int cap) {
        if (out.size() < cap && seen.add(c.pos().immutable())) {
            out.add(new Payloads.Mark(c.pos().immutable(), c.kind()));
        }
    }
}
