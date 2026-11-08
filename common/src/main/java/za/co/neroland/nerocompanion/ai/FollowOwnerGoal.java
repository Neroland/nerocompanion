package za.co.neroland.nerocompanion.ai;

import java.util.EnumSet;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.goal.Goal;

import za.co.neroland.nerocompanion.entity.CompanionEntity;
import za.co.neroland.nerocompanion.entity.SafeSpot;
import za.co.neroland.nerocompanion.entity.type.CompanionType;

/**
 * Follow the owner, wolf-style: path toward the owner when farther than the follow distance,
 * stop inside it, and teleport next to the owner when farther than the teleport distance - but only onto a safe
 * spot (no wall, no lava, fire or magma, not over the void for ground chassis, inside the world border), and
 * only in the owner's dimension. The owner is the stored one, resolved by UUID; never a nearby player.
 */
public class FollowOwnerGoal extends Goal {

    private static final int REPATH_TICKS = 10;

    private final CompanionEntity companion;
    private int repathIn;

    public FollowOwnerGoal(CompanionEntity companion) {
        this.companion = companion;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    private double followDistance() {
        CompanionType type = this.companion.typeDef();
        return type == null ? 3.0D : type.goalWeights().followDistance();
    }

    private double teleportDistance() {
        CompanionType type = this.companion.typeDef();
        return type == null ? 16.0D : type.goalWeights().teleportDistance();
    }

    @Override
    public boolean canUse() {
        ServerPlayer owner = this.companion.owner();
        if (owner == null || owner.level() != this.companion.level() || owner.isSpectator()
                || this.companion.isPerched()) {
            return false;
        }
        double start = this.followDistance() + 2.0D;
        return this.companion.distanceToSqr(owner) > start * start;
    }

    @Override
    public boolean canContinueToUse() {
        ServerPlayer owner = this.companion.owner();
        if (owner == null || owner.level() != this.companion.level() || this.companion.isPerched()) {
            return false;
        }
        double stop = this.followDistance();
        return this.companion.distanceToSqr(owner) > stop * stop;
    }

    @Override
    public void start() {
        this.repathIn = 0;
    }

    @Override
    public void stop() {
        this.companion.getNavigation().stop();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        ServerPlayer owner = this.companion.owner();
        if (owner == null) {
            return;
        }
        this.companion.getLookControl().setLookAt(owner, 10.0F, this.companion.getMaxHeadXRot());
        if (--this.repathIn > 0) {
            return;
        }
        this.repathIn = this.adjustedTickDelay(REPATH_TICKS);
        double teleport = this.teleportDistance();
        if (this.companion.distanceToSqr(owner) >= teleport * teleport) {
            this.tryTeleportNear(owner);
        } else {
            this.companion.getNavigation().moveTo(owner, 1.0D);
        }
    }

    /** Teleports 2-3 blocks from the owner onto the nearest safe spot ({@link SafeSpot}); stays put if none. */
    private void tryTeleportNear(ServerPlayer owner) {
        SafeSpot.find(owner.blockPosition(), 2, 3, 1, this.companion::isSafeSpot).ifPresent(target -> {
            this.companion.snapTo(target.getX() + 0.5D, target.getY(), target.getZ() + 0.5D,
                    this.companion.getYRot(), this.companion.getXRot());
            this.companion.getNavigation().stop();
        });
    }
}
