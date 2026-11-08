package za.co.neroland.nerocompanion.ai;

import java.util.EnumSet;

import net.minecraft.world.entity.ai.goal.Goal;

import za.co.neroland.nerocompanion.entity.CompanionEntity;
import za.co.neroland.nerocompanion.entity.CompanionMode;

/**
 * SLEEP: holds MOVE, LOOK and JUMP so nothing else runs, and stops navigation. While
 * the scheduler forces sleep the AI step is skipped entirely, so this goal mostly matters for a player-chosen
 * SLEEP; either way no energy drains.
 */
public class SleepGoal extends Goal {

    private final CompanionEntity companion;

    public SleepGoal(CompanionEntity companion) {
        this.companion = companion;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        return this.companion.effectiveMode() == CompanionMode.SLEEP;
    }

    @Override
    public void start() {
        this.companion.getNavigation().stop();
    }

    @Override
    public void tick() {
        this.companion.getNavigation().stop();
    }
}
