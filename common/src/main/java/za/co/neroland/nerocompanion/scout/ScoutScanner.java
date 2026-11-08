package za.co.neroland.nerocompanion.scout;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.AABB;

import za.co.neroland.nerocompanion.network.payload.Payloads;

/**
 * The hostile half of a scout scan around a point (server): living {@link Enemy} mobs in the radius, on the
 * {@value ScanCadence#HOSTILE_TICKS}-tick cadence only. Ores are {@link OreScan}'s (cached, time-sliced).
 */
public final class ScoutScanner {

    private ScoutScanner() {
    }

    public static List<ScoutMarkSet.Candidate> hostiles(ServerLevel level, BlockPos center, int radius) {
        double r2 = (double) radius * radius;
        AABB box = new AABB(center).inflate(radius);
        List<ScoutMarkSet.Candidate> found = new ArrayList<>();
        for (Mob mob : level.getEntitiesOfClass(Mob.class, box, m -> m.isAlive() && m instanceof Enemy)) {
            BlockPos pos = mob.blockPosition();
            double d2 = pos.distSqr(center);
            if (d2 <= r2) {
                found.add(new ScoutMarkSet.Candidate(pos, Payloads.Mark.HOSTILE, d2));
            }
        }
        return found;
    }
}
