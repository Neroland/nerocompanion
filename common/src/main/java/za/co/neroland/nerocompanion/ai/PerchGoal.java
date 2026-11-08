package za.co.neroland.nerocompanion.ai;

import java.util.EnumSet;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.goal.Goal;

import za.co.neroland.nerocompanion.entity.CompanionEntity;

/**
 * Perch on the owner's shoulder. After the companion
 * has idled within {@value #NEAR} blocks of its owner for {@value #SETTLE_TICKS} ticks it perches: it stays a
 * world entity (no riding) that the server pins to the shoulder each tick, and the client renders at the owner's
 * interpolated shoulder point. It unperches when the owner sneaks, takes damage, glides, mounts something or
 * changes dimension, and on any mode change (the controller removes this goal).
 */
public class PerchGoal extends Goal {

    private static final double NEAR = 3.0D;
    private static final int SETTLE_TICKS = 100;

    private final CompanionEntity companion;
    private long nearSince = -1L;

    public PerchGoal(CompanionEntity companion) {
        this.companion = companion;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        ServerPlayer owner = this.companion.owner();
        if (!this.companion.isSmallChassis() || this.companion.perchCooldown() > 0
                || owner == null || CompanionEntity.ownerRefusesPerch(owner, this.companion.level()) || !owner.onGround()
                || this.companion.distanceToSqr(owner) > NEAR * NEAR) {
            this.nearSince = -1L;
            return false;
        }
        long now = this.companion.level().getGameTime();
        if (this.nearSince < 0L) {
            this.nearSince = now;
        }
        return now - this.nearSince >= SETTLE_TICKS;
    }

    @Override
    public boolean canContinueToUse() {
        return this.companion.isPerched()
                && !CompanionEntity.ownerRefusesPerch(this.companion.owner(), this.companion.level());
    }

    @Override
    public void start() {
        ServerPlayer owner = this.companion.owner();
        if (owner != null) {
            this.companion.startPerch(owner);
        }
    }

    @Override
    public void stop() {
        this.nearSince = -1L;
        this.companion.stopPerch();
    }
}
