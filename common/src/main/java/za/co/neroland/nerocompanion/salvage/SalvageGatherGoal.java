package za.co.neroland.nerocompanion.salvage;

import java.util.EnumSet;

import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerocompanion.entity.CompanionEntity;
import za.co.neroland.nerocompanion.scheduler.CompanionScheduler;

/**
 * Gathers the owner's death drops into the Salvage Hold. Installed by the mode controller at priority
 * 3 in every awake mode, only on companions that salvage. Looks for a target on the 10-tick scan cadence; one target
 * at a time; walks (or flies) at 1.2x and picks up within {@link SalvageRules#PICKUP_RANGE} blocks. Only ever
 * targets item entities the death window claimed for this companion's owner.
 */
public final class SalvageGatherGoal extends Goal {

    private static final double SPEED = 1.2D;

    private final CompanionEntity companion;
    @Nullable
    private ItemEntity target;
    private int repath;

    public SalvageGatherGoal(CompanionEntity companion) {
        this.companion = companion;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (this.companion.isPerched() || this.companion.ownerId() == null
                || !CompanionScheduler.isScanTick(this.companion.tickCount, this.companion.getId())) {
            return false;
        }
        this.target = Salvage.nearestTarget(this.companion);
        return this.target != null;
    }

    @Override
    public boolean canContinueToUse() {
        ItemEntity item = this.target;
        return item != null && Salvage.isOwnersDrop(this.companion, item) && Salvage.windowFor(this.companion)
                .isPresent();
    }

    @Override
    public void start() {
        this.repath = 0;
    }

    @Override
    public void stop() {
        this.target = null;
        this.companion.getNavigation().stop();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        ItemEntity item = this.target;
        if (item == null) {
            return;
        }
        this.companion.getLookControl().setLookAt(item, 30.0F, 30.0F);
        double reach = SalvageRules.PICKUP_RANGE;
        if (this.companion.distanceToSqr(item) <= reach * reach) {
            Salvage.pickUp(this.companion, item);
            this.target = null;
            return;
        }
        if (--this.repath <= 0) {
            this.repath = CompanionScheduler.SCAN_INTERVAL;
            this.companion.getNavigation().moveTo(item, SPEED);
        }
    }
}
