package za.co.neroland.nerocompanion.ai;

import java.util.EnumSet;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.goal.Goal;

import za.co.neroland.nerocompanion.entity.CompanionEntity;

/**
 * Look at the owner (priority 9) when within {@value #RANGE} blocks, for a few seconds at a time. Never at
 * another player: only the stored owner counts.
 */
public class LookAtOwnerGoal extends Goal {

    private static final double RANGE = 8.0D;
    private static final float CHANCE = 0.02F;

    private final CompanionEntity companion;
    private int lookTime;

    public LookAtOwnerGoal(CompanionEntity companion) {
        this.companion = companion;
        this.setFlags(EnumSet.of(Goal.Flag.LOOK));
    }

    private boolean ownerInRange() {
        ServerPlayer owner = this.companion.owner();
        return owner != null && owner.level() == this.companion.level()
                && this.companion.distanceToSqr(owner) <= RANGE * RANGE;
    }

    @Override
    public boolean canUse() {
        return this.companion.getRandom().nextFloat() < CHANCE && this.ownerInRange();
    }

    @Override
    public boolean canContinueToUse() {
        return this.lookTime > 0 && this.ownerInRange();
    }

    @Override
    public void start() {
        this.lookTime = this.adjustedTickDelay(40 + this.companion.getRandom().nextInt(40));
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        ServerPlayer owner = this.companion.owner();
        if (owner != null) {
            this.companion.getLookControl().setLookAt(owner.getX(), owner.getEyeY(), owner.getZ());
        }
        this.lookTime--;
    }
}
