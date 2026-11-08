package za.co.neroland.nerocompanion.ai.drone;

import java.util.EnumSet;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerocompanion.chip.ChipEffects;
import za.co.neroland.nerocompanion.chip.ChipKinds;
import za.co.neroland.nerocompanion.entity.CompanionEntity;
import za.co.neroland.nerocompanion.network.payload.Payloads;
import za.co.neroland.nerocompanion.platform.Services;
import za.co.neroland.nerocompanion.scout.MarkRouting;
import za.co.neroland.nerocompanion.scout.OreScan;
import za.co.neroland.nerocompanion.scout.ScanCadence;
import za.co.neroland.nerocompanion.scout.ScoutMarkSet;
import za.co.neroland.nerocompanion.scout.ScoutScanner;

/**
 * SCOUT: fly up to {@value ScoutAheadRules#AHEAD} blocks ahead of the owner along their horizontal look, at the first of
 * {@link ScoutAheadRules#HEIGHTS} above their feet that is free (so it leads in a two-high tunnel), stopping a block
 * short of the first wall or fluid a ray from the owner hits (never behind a wall, never into water or lava). A new
 * target within {@value ScoutAheadRules#DEAD_BAND} blocks of the old one is ignored, so head turns do not make it dart.
 *
 * <p>Marks: hostiles every {@value ScanCadence#HOSTILE_TICKS} ticks; ores from an {@link OreScan} (time-sliced, at most
 * every {@value ScanCadence#ORE_TICKS} ticks, skipped when nothing changed); at most {@value ScoutMarkSet#MAX_MARKS}
 * marks with a {@value ScoutMarkSet#TTL_TICKS}-tick TTL <b>to the owner only</b>; an empty batch is not repeated. With
 * the Ore Filter chip only ores are marked. Gives way to the follow goal (which can teleport) beyond
 * {@value ScoutAheadRules#LEASH} blocks.
 */
public final class ScoutAheadGoal extends Goal {

    private final CompanionEntity companion;
    private final OreScan oreScan = new OreScan();
    private long nextUpdate;
    @Nullable
    private Vec3 target;
    private boolean lastBatchEmpty = true;

    public ScoutAheadGoal(CompanionEntity companion) {
        this.companion = companion;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        ServerPlayer owner = this.companion.owner();
        return owner != null && this.companion.ownerHere() && !owner.isSpectator() && !this.companion.isPerched()
                && this.companion.distanceToSqr(owner) <= ScoutAheadRules.LEASH * ScoutAheadRules.LEASH;
    }

    @Override
    public boolean canContinueToUse() {
        return this.canUse();
    }

    @Override
    public void start() {
        this.nextUpdate = 0L;
        this.target = null;
        this.companion.setWorking(true);
    }

    @Override
    public void stop() {
        this.companion.getNavigation().stop();
        this.companion.setWorking(false);
        this.target = null;
        this.oreScan.reset();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        ServerPlayer owner = this.companion.owner();
        if (owner == null || !(this.companion.level() instanceof ServerLevel level)) {
            return;
        }
        long now = level.getGameTime();
        if (now < this.nextUpdate) {
            return;
        }
        this.nextUpdate = now + ScanCadence.HOSTILE_TICKS;
        Vec3 ahead = aheadPoint(level, owner);
        if (ahead != null) {
            boolean retarget = this.target == null || ScoutAheadRules.retarget(this.target.x, this.target.y,
                    this.target.z, ahead.x, ahead.y, ahead.z);
            if (retarget) {
                this.target = ahead;
            }
            if (retarget || this.companion.getNavigation().isDone()) {
                Vec3 to = this.target;
                this.companion.getNavigation().moveTo(to.x, to.y, to.z, 1.3D);
            }
        }
        this.sendMarks(level, now);
    }

    /**
     * Where the drone should be: for each height in {@link ScoutAheadRules#HEIGHTS} above the owner's feet whose block
     * is free, a ray along the horizontal look stops at the first collider or fluid; the point a block short of it
     * (or the full distance) is taken if it is at least {@value ScoutAheadRules#MIN_AHEAD} blocks out, free of blocks
     * and fluid, and visible from the owner's eyes. Null if no height works (the drone keeps its last target).
     */
    @Nullable
    static Vec3 aheadPoint(ServerLevel level, Player owner) {
        double[] dir = ScoutAheadRules.horizontal(owner.getLookAngle().x, owner.getLookAngle().z, owner.getYRot());
        Vec3 eyes = owner.getEyePosition();
        for (double h : ScoutAheadRules.HEIGHTS) {
            Vec3 origin = new Vec3(owner.getX(), owner.getY() + h, owner.getZ());
            if (!free(level, BlockPos.containing(origin))) {
                continue;
            }
            Vec3 end = origin.add(dir[0] * ScoutAheadRules.AHEAD, 0.0D, dir[1] * ScoutAheadRules.AHEAD);
            HitResult hit = level.clip(new ClipContext(origin, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY,
                    owner));
            double reach = hit.getType() == HitResult.Type.MISS ? ScoutAheadRules.AHEAD
                    : origin.distanceTo(hit.getLocation()) - 1.0D;
            for (double d = reach; d >= ScoutAheadRules.MIN_AHEAD; d -= 1.0D) {
                Vec3 point = origin.add(dir[0] * d, 0.0D, dir[1] * d);
                if (free(level, BlockPos.containing(point)) && level.clip(new ClipContext(eyes, point,
                        ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, owner)).getType() == HitResult.Type.MISS) {
                    return point;
                }
            }
        }
        return null;
    }

    /** Loaded, no collision shape, no fluid. */
    private static boolean free(ServerLevel level, BlockPos pos) {
        return level.isLoaded(pos) && level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                && level.getFluidState(pos).isEmpty();
    }

    private void sendMarks(ServerLevel level, long now) {
        int radius = ScoutMarkSet.RADIUS + ChipEffects.rangeBonus(this.companion.activeChips());
        BlockPos center = this.companion.blockPosition();
        boolean oresOnly = ChipEffects.has(this.companion.activeChips(), ChipKinds.ORE_FILTER);
        this.oreScan.step(level, center, radius, now);
        List<ScoutMarkSet.Candidate> hostiles = oresOnly ? List.of() : ScoutScanner.hostiles(level, center, radius);
        List<Payloads.Mark> marks = ScoutMarkSet.build(hostiles, this.oreScan.candidates(center, radius), radius);
        if (marks.stream().anyMatch(mark -> mark.kind() == Payloads.Mark.ORE)) {
            // Throttled by the chatter service's per-event cooldown, so a steady ore field chirps now and then.
            za.co.neroland.nerocompanion.personality.ChatterService.say(this.companion,
                    za.co.neroland.nerocompanion.personality.ChatterEvent.ORE_FOUND);
        }
        if (marks.isEmpty() && this.lastBatchEmpty) {
            return;
        }
        this.lastBatchEmpty = marks.isEmpty();
        MarkRouting.recipient(this.companion.ownerId(), level.players(), Player::getUUID,
                        p -> !Services.ENTITIES.isFakePlayer(p))
                .ifPresent(p -> Services.NETWORK.sendToPlayer(p,
                        new Payloads.ScoutMarks(marks, ScoutMarkSet.TTL_TICKS)));
    }
}
