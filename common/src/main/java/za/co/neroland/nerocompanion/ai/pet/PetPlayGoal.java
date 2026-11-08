package za.co.neroland.nerocompanion.ai.pet;

import java.util.EnumSet;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import za.co.neroland.nerocompanion.entity.CompanionEntity;
import za.co.neroland.nerocompanion.entity.CompanionMode;
import za.co.neroland.nerocompanion.entity.type.CompanionType;

/**
 * Play idles near the owner ({@link PlayRules}): zoomies, a tail-chasing spin, or a little pounce. Rolled every
 * {@value PlayRules#CHECK_INTERVAL} ticks, at most {@value PlayRules#DURATION} ticks long, then a
 * {@value PlayRules#COOLDOWN}-tick rest. Purely cosmetic: it moves the pet and nothing else.
 */
public class PetPlayGoal extends Goal {

    private final CompanionEntity pet;
    private PlayRules.Kind kind = PlayRules.Kind.SPIN;
    private int ticks;
    private int legs;
    private int nextCheck;
    private int restUntil;

    public PetPlayGoal(CompanionEntity pet, CompanionType type) {
        this.pet = pet;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP));
    }

    private boolean ownerNear() {
        ServerPlayer owner = this.pet.owner();
        return owner != null && owner.level() == this.pet.level()
                && this.pet.distanceToSqr(owner) <= PlayRules.NEAR * PlayRules.NEAR;
    }

    @Override
    public boolean canUse() {
        int now = this.pet.tickCount;
        if (now < this.nextCheck || now < this.restUntil) {
            return false;
        }
        this.nextCheck = now + PlayRules.CHECK_INTERVAL;
        if (this.pet.isPerched() || this.pet.isWorking() || !this.pet.onGround() || !this.ownerNear()
                || this.pet.getRandom().nextInt(PlayRules.CHANCE_IN) != 0) {
            return false;
        }
        this.kind = PlayRules.pick(this.pet.getRandom().nextInt(100), this.pet.mode() == CompanionMode.STAY);
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return this.ticks < PlayRules.DURATION && !this.pet.isPerched() && !this.pet.isWorking() && this.ownerNear();
    }

    @Override
    public void start() {
        this.ticks = 0;
        this.legs = 0;
        if (this.kind == PlayRules.Kind.POUNCE) {
            ServerPlayer owner = this.pet.owner();
            if (owner != null && this.pet.onGround()) {
                Vec3 toward = owner.position().subtract(this.pet.position()).multiply(1.0D, 0.0D, 1.0D);
                Vec3 push = toward.lengthSqr() > 1.0E-4D ? toward.normalize().scale(0.25D) : Vec3.ZERO;
                this.pet.setDeltaMovement(this.pet.getDeltaMovement().add(push.x, 0.35D, push.z));
            }
        }
    }

    @Override
    public void stop() {
        this.restUntil = this.pet.tickCount + PlayRules.COOLDOWN;
        this.pet.getNavigation().stop();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        this.ticks++;
        switch (this.kind) {
            case SPIN -> {
                float yaw = Mth.wrapDegrees(this.pet.getYRot() + 24.0F);
                this.pet.setYRot(yaw);
                this.pet.yBodyRot = yaw;
                this.pet.yHeadRot = yaw;
            }
            case ZOOMIES -> {
                if (this.legs < 3 && this.pet.getNavigation().isDone()) {
                    this.legs++;
                    double angle = this.pet.getRandom().nextDouble() * Math.PI * 2.0D;
                    double reach = 2.0D + this.pet.getRandom().nextDouble() * 2.5D;
                    this.pet.getNavigation().moveTo(this.pet.getX() + Math.cos(angle) * reach, this.pet.getY(),
                            this.pet.getZ() + Math.sin(angle) * reach, 1.6D);
                } else if (this.legs >= 3 && this.pet.getNavigation().isDone()) {
                    this.ticks = PlayRules.DURATION;
                }
            }
            case POUNCE -> {
                if (this.ticks > 12 && this.pet.onGround()) {
                    this.ticks = PlayRules.DURATION;
                }
            }
        }
    }
}
