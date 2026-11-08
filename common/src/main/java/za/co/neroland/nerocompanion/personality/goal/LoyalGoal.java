package za.co.neroland.nerocompanion.personality.goal;

import java.util.EnumSet;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import za.co.neroland.nerocompanion.entity.CompanionEntity;
import za.co.neroland.nerocompanion.personality.ChatterEvent;

/**
 * Loyal: stays within 2 blocks and faces the owner; bond 2 settles exactly where the owner stood still; bond 3
 * salutes (head up, a tick bubble) when the owner looks at it.
 */
public class LoyalGoal extends TraitIdleGoal {

    private enum Act { CLOSE, SETTLE, SALUTE }

    private Act act = Act.CLOSE;
    private Vec3 lastOwnerPos = Vec3.ZERO;
    private int ownerStillChecks;

    public LoyalGoal(CompanionEntity companion) {
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
        if (owner.position().distanceToSqr(this.lastOwnerPos) < 0.25D) {
            this.ownerStillChecks++;
        } else {
            this.ownerStillChecks = 0;
            this.lastOwnerPos = owner.position();
        }
        int bond = this.bond();
        if (bond >= 3 && this.ownerLooksAtMe() && this.trickReady() && this.chance(0.35F)) {
            this.trickPlayed();
            this.act = Act.SALUTE;
            this.duration = 20;
            this.say(ChatterEvent.TRICK);
            return true;
        }
        if (bond >= 2 && this.ownerStillChecks >= 4 && this.mayMove() && this.chance(0.5F)) {
            this.act = Act.SETTLE;
            this.duration = 60;
            this.moveTo(aroundOwner(owner, owner.yBodyRot + 150.0F, 1.2D), 0.9D);
            return true;
        }
        if (owner.distanceToSqr(this.companion) > 2.5D * 2.5D && this.mayMove()) {
            this.act = Act.CLOSE;
            this.duration = 40;
            this.moveTo(aroundOwner(owner, owner.yBodyRot + 180.0F, 1.5D), 1.0D);
            return true;
        }
        if (this.chance(0.3F)) {
            this.act = Act.CLOSE;
            this.duration = 30;
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
        if (this.act == Act.SALUTE) {
            this.lookAt(owner.getEyePosition().add(0.0D, 1.5D, 0.0D));
            return;
        }
        this.lookAt(owner.getEyePosition());
    }

    @Override
    protected void end() {
        this.companion.getNavigation().stop();
    }
}
