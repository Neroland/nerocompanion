package za.co.neroland.nerocompanion.ai.drone;

import java.util.EnumSet;

import net.minecraft.world.entity.ai.goal.Goal;

import za.co.neroland.nerocompanion.entity.CompanionEntity;

/**
 * Runs the {@link DroneLamp} while a Lamp Chip is installed (no goal flags: it never competes with movement). Its
 * {@code stop()} - mode change (the controller removes and stops it), chip removed, SLEEP - removes the light; the
 * entity's {@code onLeaveWorld} covers recall, parking, death and unload.
 */
public final class LampGoal extends Goal {

    private final CompanionEntity companion;
    private final DroneLamp lamp;

    public LampGoal(CompanionEntity companion, DroneLamp lamp) {
        this.companion = companion;
        this.lamp = lamp;
        this.setFlags(EnumSet.noneOf(Goal.Flag.class));
    }

    @Override
    public boolean canUse() {
        return DroneLamp.installed(this.companion);
    }

    @Override
    public boolean canContinueToUse() {
        return DroneLamp.installed(this.companion) && !this.companion.isRemoved();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        this.lamp.tick(this.companion);
    }

    @Override
    public void stop() {
        this.lamp.clear(this.companion);
    }
}
