package za.co.neroland.nerocompanion.personality.goal;

import java.util.EnumSet;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import za.co.neroland.nerocompanion.entity.CompanionEntity;

/** Bond 1+: now and then the companion looks around on its own (every trait). Look only, never moves. */
public class LookAroundGoal extends TraitIdleGoal {

    private Vec3 point = Vec3.ZERO;

    public LookAroundGoal(CompanionEntity companion) {
        super(companion, EnumSet.of(Goal.Flag.LOOK));
    }

    @Override
    protected int interval() {
        return 60;
    }

    @Override
    protected boolean begin() {
        if (this.bond() < 1 || !this.chance(0.4F)) {
            return false;
        }
        float yaw = this.companion.getRandom().nextFloat() * (float) (Math.PI * 2.0D);
        double pitch = (this.companion.getRandom().nextDouble() - 0.4D) * 2.0D;
        this.point = this.companion.getEyePosition().add(Mth.cos(yaw) * 4.0D, pitch, Mth.sin(yaw) * 4.0D);
        this.duration = 20 + this.companion.getRandom().nextInt(30);
        return true;
    }

    @Override
    protected void step() {
        this.lookAt(this.point);
    }
}
