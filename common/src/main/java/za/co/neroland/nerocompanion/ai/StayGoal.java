package za.co.neroland.nerocompanion.ai;

import java.util.EnumSet;

import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import za.co.neroland.nerocompanion.entity.CompanionEntity;

/**
 * STAY (priority 6): hold the position the companion was in when it entered STAY, and path back if pushed more
 * than {@value #LEASH} blocks away.
 */
public class StayGoal extends Goal {

    private static final double LEASH = 2.0D;

    private final CompanionEntity companion;

    public StayGoal(CompanionEntity companion) {
        this.companion = companion;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        return this.companion.position().distanceToSqr(this.companion.stayAnchor()) > LEASH * LEASH;
    }

    @Override
    public boolean canContinueToUse() {
        return !this.companion.getNavigation().isDone()
                && this.companion.position().distanceToSqr(this.companion.stayAnchor()) > 0.5D;
    }

    @Override
    public void start() {
        Vec3 anchor = this.companion.stayAnchor();
        this.companion.getNavigation().moveTo(anchor.x, anchor.y, anchor.z, 1.0D);
    }

    @Override
    public void stop() {
        this.companion.getNavigation().stop();
    }
}
