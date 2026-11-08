package za.co.neroland.nerocompanion.ai;

import java.util.EnumSet;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;

import za.co.neroland.nerocompanion.config.NeroCompanionConfig;
import za.co.neroland.nerocompanion.entity.CompanionEntity;
import za.co.neroland.nerocompanion.entity.HomePos;
import za.co.neroland.nerocompanion.scheduler.CompanionLimits;

/**
 * Low battery: at or below the low-battery percent, head for the home pad or Bay if it
 * is in this dimension and within range. If it is not, the scheduler puts the companion to sleep in place instead
 * (and the owner gets the low-battery notice once per discharge). Chargers arrive in Stage 2; until then a
 * companion that reaches home waits there.
 */
public class ReturnHomeGoal extends Goal {

    private static final int REPATH_TICKS = 20;

    private final CompanionEntity companion;
    private int repathIn;

    public ReturnHomeGoal(CompanionEntity companion) {
        this.companion = companion;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    private boolean wantsHome() {
        if (this.companion.home().isEmpty() || this.companion.isPerched()) {
            return false;
        }
        CompanionLimits limits = NeroCompanionConfig.limits();
        if (!this.companion.energy().isLow(limits.lowBatteryPercent())) {
            return false;
        }
        HomePos home = this.companion.home().get();
        double range = limits.returnHomeRange();
        return home.isIn(this.companion.level())
                && home.pos().distToCenterSqr(this.companion.position()) <= range * range;
    }

    @Override
    public boolean canUse() {
        return this.wantsHome() && !this.atHome();
    }

    @Override
    public boolean canContinueToUse() {
        return this.wantsHome() && !this.atHome();
    }

    private boolean atHome() {
        BlockPos home = this.companion.home().map(HomePos::pos).orElse(null);
        return home != null && home.distToCenterSqr(this.companion.position()) <= 2.25D;
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
    public void tick() {
        if (--this.repathIn > 0) {
            return;
        }
        this.repathIn = this.adjustedTickDelay(REPATH_TICKS);
        this.companion.home().ifPresent(home -> this.companion.getNavigation().moveTo(
                home.pos().getX() + 0.5D, home.pos().getY() + 1.0D, home.pos().getZ() + 0.5D, 1.0D));
    }
}
