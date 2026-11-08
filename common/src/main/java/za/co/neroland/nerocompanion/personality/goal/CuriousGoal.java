package za.co.neroland.nerocompanion.personality.goal;

import java.util.EnumSet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerocompanion.entity.CompanionEntity;
import za.co.neroland.nerocompanion.personality.ChatterEvent;

/**
 * Curious: wanders to look at the nearest block entity (chest, sign, furnace...) within 6 blocks - looks, never
 * opens; bond 2 adds peering over edges; bond 3 adds a 360-degree spin trick. The block-entity lookup reads the
 * already-loaded chunks' block-entity maps (no block scan), once per check.
 */
public class CuriousGoal extends TraitIdleGoal {

    private static final int RADIUS = 6;

    private enum Act { INSPECT, PEER, TRICK }

    private Act act = Act.INSPECT;
    private Vec3 point = Vec3.ZERO;

    public CuriousGoal(CompanionEntity companion) {
        super(companion, EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    protected int interval() {
        return 160;
    }

    @Override
    protected boolean begin() {
        ServerPlayer owner = this.owner();
        if (owner == null) {
            return false;
        }
        int bond = this.bond();
        if (bond >= 3 && owner.distanceToSqr(this.companion) < 36.0D && this.trickReady() && this.chance(0.2F)) {
            this.trickPlayed();
            this.act = Act.TRICK;
            this.duration = 24;
            this.spin(360.0F, 20);
            this.say(ChatterEvent.TRICK);
            return true;
        }
        if (bond >= 2 && this.chance(0.3F)) {
            this.act = Act.PEER;
            Vec3 ahead = this.companion.getViewVector(1.0F).multiply(1.0D, 0.0D, 1.0D).normalize().scale(2.0D);
            this.point = this.companion.position().add(ahead).add(0.0D, -2.0D, 0.0D);
            this.duration = 40;
            return true;
        }
        if (!this.chance(0.6F)) {
            return false;
        }
        BlockPos target = nearestBlockEntity(this.companion.level(), this.companion.blockPosition(), RADIUS);
        if (target == null || target.distSqr(owner.blockPosition()) > 12 * 12) {
            return false;
        }
        this.act = Act.INSPECT;
        this.point = Vec3.atCenterOf(target);
        this.duration = 80;
        this.moveTo(this.point, 0.8D);
        if (this.chance(0.3F)) {
            this.say(ChatterEvent.IDLE);
        }
        return true;
    }

    @Override
    protected void step() {
        if (this.act == Act.TRICK) {
            return;
        }
        this.lookAt(this.point);
        if (this.act == Act.INSPECT && this.companion.position().distanceToSqr(this.point) < 4.0D) {
            this.companion.getNavigation().stop();
        }
    }

    @Override
    protected void end() {
        this.companion.getNavigation().stop();
    }

    /** The nearest block entity within {@code radius} of {@code center} in loaded chunks, or null. */
    @Nullable
    static BlockPos nearestBlockEntity(Level level, BlockPos center, int radius) {
        BlockPos best = null;
        double bestDist = (double) radius * radius;
        int minCx = SectionPos.blockToSectionCoord(center.getX() - radius);
        int maxCx = SectionPos.blockToSectionCoord(center.getX() + radius);
        int minCz = SectionPos.blockToSectionCoord(center.getZ() - radius);
        int maxCz = SectionPos.blockToSectionCoord(center.getZ() + radius);
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                if (!level.hasChunk(cx, cz)) {
                    continue;
                }
                LevelChunk chunk = level.getChunk(cx, cz);
                for (BlockPos pos : chunk.getBlockEntities().keySet()) {
                    double d = pos.distSqr(center);
                    if (d <= bestDist) {
                        bestDist = d;
                        best = pos;
                    }
                }
            }
        }
        return best;
    }
}
