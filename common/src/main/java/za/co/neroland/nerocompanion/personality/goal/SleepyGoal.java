package za.co.neroland.nerocompanion.personality.goal;

import java.util.EnumSet;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import za.co.neroland.nerocompanion.entity.CompanionEntity;
import za.co.neroland.nerocompanion.personality.ChatterEvent;

/**
 * Sleepy: after 60 s of nothing happening (owner standing still) it naps in place - stops, looks down, a zz
 * bubble - until the owner moves off; bond 2 yawns more often; bond 3 does a slow lazy turn. The nap is a goal
 * state only; the sleep pose itself needs a renderer flag (see notes).
 */
public class SleepyGoal extends TraitIdleGoal {

    /** Ticks of owner stillness before a nap (60 s). */
    static final int NAP_AFTER = 1200;
    private static final int CHECK = 40;

    private enum Act { NAP, YAWN, ROLL }

    private Act act = Act.NAP;
    private Vec3 ownerAnchor = Vec3.ZERO;
    private int stillTicks;

    public SleepyGoal(CompanionEntity companion) {
        super(companion, EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    protected int interval() {
        return CHECK;
    }

    @Override
    protected boolean begin() {
        ServerPlayer owner = this.owner();
        if (owner == null) {
            return false;
        }
        if (owner.position().distanceToSqr(this.ownerAnchor) < 1.0D) {
            this.stillTicks += CHECK;
        } else {
            this.stillTicks = 0;
            this.ownerAnchor = owner.position();
        }
        int bond = this.bond();
        if (bond >= 3 && this.trickReady() && this.chance(0.04F)) {
            this.trickPlayed();
            this.act = Act.ROLL;
            this.duration = 44;
            this.spin(360.0F, 40);
            this.say(ChatterEvent.TRICK);
            return true;
        }
        if (this.stillTicks >= NAP_AFTER) {
            this.act = Act.NAP;
            this.duration = 300 + this.companion.getRandom().nextInt(300);
            this.companion.getNavigation().stop();
            this.companion.setNapping(true);
            this.say(ChatterEvent.IDLE);
            return true;
        }
        if (this.chance(bond >= 2 ? 0.12F : 0.05F)) {
            this.act = Act.YAWN;
            this.duration = 20;
            this.say(ChatterEvent.IDLE);
            return true;
        }
        return false;
    }

    @Override
    public boolean canContinueToUse() {
        if (!super.canContinueToUse()) {
            return false;
        }
        ServerPlayer owner = this.owner();
        // A nap ends as soon as the owner walks off.
        return this.act != Act.NAP || owner != null && owner.position().distanceToSqr(this.ownerAnchor) < 9.0D;
    }

    @Override
    protected void step() {
        if (this.act == Act.ROLL) {
            return;
        }
        Vec3 down = this.companion.position().add(this.companion.getViewVector(1.0F).multiply(1.0D, 0.0D, 1.0D))
                .add(0.0D, -1.0D, 0.0D);
        this.lookAt(down);
    }

    @Override
    protected void end() {
        if (this.act == Act.NAP) {
            this.stillTicks = 0;
            this.companion.setNapping(false);
        }
    }
}
