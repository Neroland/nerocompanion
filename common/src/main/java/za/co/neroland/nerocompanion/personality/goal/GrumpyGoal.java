package za.co.neroland.nerocompanion.personality.goal;

import java.util.EnumSet;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import za.co.neroland.nerocompanion.entity.CompanionEntity;
import za.co.neroland.nerocompanion.entity.Locomotion;
import za.co.neroland.nerocompanion.personality.ChatterEvent;

/**
 * Grumpy: keeps about 4 blocks from the owner and looks away; bond 2 puffs at a stranger within 3 blocks; bond 3
 * stomps three times (a hop for ground bots, a dip for flyers). It still likes you.
 */
public class GrumpyGoal extends TraitIdleGoal {

    private enum Act { DISTANCE, GLARE, STOMP }

    private Act act = Act.DISTANCE;
    private Vec3 point = Vec3.ZERO;

    public GrumpyGoal(CompanionEntity companion) {
        super(companion, EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    protected int interval() {
        return 40;
    }

    @Override
    protected boolean begin() {
        ServerPlayer owner = this.owner();
        if (owner == null) {
            return false;
        }
        int bond = this.bond();
        if (bond >= 2) {
            ServerPlayer stranger = this.strangerWithin(3.0D);
            if (stranger != null) {
                this.act = Act.GLARE;
                this.point = stranger.getEyePosition();
                this.duration = 30;
                this.say(ChatterEvent.STRANGER);
                return true;
            }
        }
        if (bond >= 3 && this.trickReady() && this.chance(0.08F)) {
            this.trickPlayed();
            this.act = Act.STOMP;
            this.duration = 18;
            this.say(ChatterEvent.TRICK);
            return true;
        }
        double distance = Math.sqrt(owner.distanceToSqr(this.companion));
        if (distance < 3.0D && this.mayMove()) {
            this.act = Act.DISTANCE;
            Vec3 away = this.companion.position().subtract(owner.position()).multiply(1.0D, 0.0D, 1.0D);
            if (away.lengthSqr() < 1.0E-4D) {
                away = new Vec3(1.0D, 0.0D, 0.0D);
            }
            this.point = owner.position().add(away.normalize().scale(4.0D));
            this.duration = 40;
            this.moveTo(this.point, 0.8D);
            return true;
        }
        if (this.chance(0.25F)) {
            this.act = Act.DISTANCE;
            // Look anywhere but at the owner.
            float yaw = (owner.yBodyRot + 90.0F + this.companion.getRandom().nextInt(180)) * Mth.DEG_TO_RAD;
            this.point = this.companion.getEyePosition().add(-Mth.sin(yaw) * 4.0D, 0.0D, Mth.cos(yaw) * 4.0D);
            this.duration = 40;
            return true;
        }
        return false;
    }

    @Override
    protected void step() {
        if (this.act == Act.STOMP) {
            if (this.ticks % 6 == 1) {
                if (this.companion.locomotion() == Locomotion.GROUND) {
                    if (this.companion.onGround()) {
                        this.companion.getJumpControl().jump();
                    }
                } else {
                    this.companion.setDeltaMovement(this.companion.getDeltaMovement().add(0.0D, -0.15D, 0.0D));
                }
            }
            return;
        }
        this.lookAt(this.point);
    }

    @Override
    protected void end() {
        this.companion.getNavigation().stop();
    }
}
