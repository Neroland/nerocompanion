package za.co.neroland.nerocompanion.personality.goal;

import java.util.EnumSet;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import za.co.neroland.nerocompanion.entity.CompanionEntity;
import za.co.neroland.nerocompanion.entity.Locomotion;
import za.co.neroland.nerocompanion.personality.ChatterEvent;

/**
 * Show-off: takes a spot in front of and to one side of the owner (a block higher if it flies) with a sun bubble,
 * never while the owner is busy; bond 2 flips after the owner has watched it for ~10 s; bond 3 does a double
 * flip with a star - a trick near the owner. Tricks and flips share a 30 s gap.
 */
public class ShowoffGoal extends TraitIdleGoal {

    private enum Act { POSE, FLIP, TRICK }

    private Act act = Act.POSE;
    private int lookedChecks;
    /** Consecutive looked-at checks (50 ticks apart) before a flip: about 10 s. */
    static final int LOOKED_CHECKS = 4;
    private static final long BUSY_MILLIS = 1500L;

    public ShowoffGoal(CompanionEntity companion) {
        super(companion, EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    protected int interval() {
        return 50;
    }

    @Override
    protected boolean begin() {
        ServerPlayer owner = this.owner();
        if (owner == null) {
            return false;
        }
        boolean looked = this.ownerLooksAtMe();
        this.lookedChecks = looked ? this.lookedChecks + 1 : 0;
        int bond = this.bond();
        boolean near = owner.distanceToSqr(this.companion) < 8.0D * 8.0D;
        if (bond >= 3 && near && this.trickReady() && this.chance(0.12F)) {
            this.trickPlayed();
            this.act = Act.TRICK;
            this.duration = 36;
            this.spin(720.0F, 32);
            this.say(ChatterEvent.TRICK);
            return true;
        }
        // Flips once it has been watched for about 10 s (LOOKED_CHECKS x 50 ticks), then rests.
        if (bond >= 2 && this.lookedChecks >= LOOKED_CHECKS && near && this.trickReady()) {
            this.lookedChecks = 0;
            this.trickPlayed();
            this.act = Act.FLIP;
            this.duration = 20;
            this.spin(360.0F, 16);
            this.say(ChatterEvent.TRICK);
            return true;
        }
        // Never pose while the owner is busy (using an item, or any action in the last 1.5 s: digging, placing,
        // attacking - the action clock is vanilla Util.getMillis(), i.e. nanoTime in ms); and off to one side.
        boolean busy = owner.isUsingItem() || System.nanoTime() / 1_000_000L - owner.getLastActionTime() < BUSY_MILLIS;
        if (!busy && this.chance(0.3F)) {
            this.act = Act.POSE;
            this.duration = 60;
            float side = this.companion.getRandom().nextBoolean() ? 40.0F : -40.0F;
            Vec3 front = aroundOwner(owner, owner.yBodyRot + side, 2.5D);
            if (this.companion.locomotion() == Locomotion.FLYING) {
                front = front.add(0.0D, 2.0D, 0.0D);
            }
            this.moveTo(front, 1.0D);
            if (this.chance(0.4F)) {
                this.say(ChatterEvent.IDLE);
            }
            return true;
        }
        return false;
    }

    @Override
    protected void step() {
        ServerPlayer owner = this.owner();
        if (owner != null && this.act == Act.POSE) {
            this.lookAt(owner.getEyePosition());
        }
    }

    @Override
    protected void end() {
        this.companion.getNavigation().stop();
    }
}
