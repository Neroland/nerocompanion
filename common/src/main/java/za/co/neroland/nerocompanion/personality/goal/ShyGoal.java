package za.co.neroland.nerocompanion.personality.goal;

import java.util.EnumSet;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import za.co.neroland.nerocompanion.entity.CompanionEntity;
import za.co.neroland.nerocompanion.personality.ChatterEvent;

/**
 * Shy: keeps behind the owner (about 2 blocks back along the owner's facing) and away from other players - from
 * bond 0 it never approaches a stranger; bond 2 hides on the owner's far side from a stranger within 6 blocks with
 * a blush; bond 3 peeks out and ducks back twice.
 */
public class ShyGoal extends TraitIdleGoal {

    private enum Act { BEHIND, HIDE, PEEK }

    private Act act = Act.BEHIND;
    private Vec3 point = Vec3.ZERO;

    public ShyGoal(CompanionEntity companion) {
        super(companion, EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    protected int interval() {
        return 30;
    }

    @Override
    protected boolean begin() {
        ServerPlayer owner = this.owner();
        if (owner == null) {
            return false;
        }
        int bond = this.bond();
        ServerPlayer stranger = this.strangerWithin(6.0D);
        if (stranger != null && this.mayMove()) {
            Vec3 away = owner.position().subtract(stranger.position()).multiply(1.0D, 0.0D, 1.0D);
            if (away.lengthSqr() < 1.0E-4D) {
                away = new Vec3(0.0D, 0.0D, 1.0D);
            }
            this.act = Act.HIDE;
            this.point = owner.position().add(away.normalize().scale(1.8D));
            this.duration = 60;
            this.moveTo(this.point, 1.1D);
            if (bond >= 2) {
                this.say(ChatterEvent.STRANGER);
            }
            return true;
        }
        if (bond >= 3 && this.trickReady() && this.chance(0.08F)) {
            this.trickPlayed();
            this.act = Act.PEEK;
            this.duration = 32;
            this.say(ChatterEvent.TRICK);
            return true;
        }
        Vec3 behind = aroundOwner(owner, owner.yBodyRot + 180.0F, 2.0D);
        if (this.companion.position().distanceToSqr(behind) > 1.5D * 1.5D && this.mayMove()) {
            this.act = Act.BEHIND;
            this.point = behind;
            this.duration = 40;
            this.moveTo(behind, 0.9D);
            return true;
        }
        return false;
    }

    @Override
    protected void step() {
        ServerPlayer owner = this.owner();
        if (owner == null) {
            return;
        }
        if (this.act == Act.PEEK) {
            // Look at the owner, then away, twice (8-tick beats).
            boolean peek = (this.ticks / 8) % 2 == 0;
            this.lookAt(peek ? owner.getEyePosition()
                    : this.companion.getEyePosition().add(this.companion.position().subtract(owner.position())));
            return;
        }
        this.lookAt(owner.getEyePosition());
    }

    @Override
    protected void end() {
        this.companion.getNavigation().stop();
    }
}
