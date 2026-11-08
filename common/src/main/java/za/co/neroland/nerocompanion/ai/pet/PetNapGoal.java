package za.co.neroland.nerocompanion.ai.pet;

import java.util.EnumSet;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerocompanion.charge.ChargingPadBlock;
import za.co.neroland.nerocompanion.charge.CompanionBayBlock;
import za.co.neroland.nerocompanion.entity.CompanionEntity;
import za.co.neroland.nerocompanion.entity.CompanionMode;
import za.co.neroland.nerocompanion.entity.HomePos;
import za.co.neroland.nerocompanion.entity.type.CompanionType;

/**
 * Bedtime: when the owner is asleep in a bed, or the pet was told to STAY next to its nap spot, it walks there and
 * curls up ({@link CompanionEntity#setNapping}) until the owner wakes or the order changes. The spot
 * ({@link NapSpots}): its home Charging Pad; else the last Pad that charged it ({@link NapPadMemory}); else beside
 * its home Companion Bay. Same dimension, within {@value #RANGE} blocks. Checked every {@value #CHECK_INTERVAL}
 * ticks; reads a handful of block states, scans no entities. A player-chosen SLEEP is the base SLEEP goal.
 */
public class PetNapGoal extends Goal {

    public static final int CHECK_INTERVAL = 40;
    public static final double RANGE = 24.0D;
    private static final double STAY_NEAR_SPOT = 2.5D;

    /** A nap spot: the block the pet's feet rest in, and the Pad under it (null beside a Bay). */
    record Spot(BlockPos restAt, @Nullable BlockPos pad) {
        Vec3 centre() {
            return new Vec3(this.restAt.getX() + 0.5D, this.restAt.getY(), this.restAt.getZ() + 0.5D);
        }

        boolean reached(BlockPos feet) {
            return feet.equals(this.restAt) || (this.pad != null && feet.equals(this.pad));
        }
    }

    private final CompanionEntity pet;
    private int nextCheck;
    private int nextRecheck;
    private int nextPath;
    private boolean resting;
    @Nullable
    private Spot spot;

    public PetNapGoal(CompanionEntity pet, CompanionType type) {
        this.pet = pet;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP));
    }

    private boolean usable(@Nullable HomePos at, Class<?> block) {
        Level level = this.pet.level();
        if (at == null || !at.isIn(level) || !level.isLoaded(at.pos())
                || !block.isInstance(level.getBlockState(at.pos()).getBlock())) {
            return false;
        }
        BlockPos p = at.pos();
        return this.pet.distanceToSqr(p.getX() + 0.5D, p.getY() + 1.0D, p.getZ() + 0.5D) <= RANGE * RANGE;
    }

    /** Where to nap right now, or null. */
    @Nullable
    private Spot napSpot() {
        HomePos home = this.pet.home().orElse(null);
        HomePos lastPad = this.pet instanceof NapPadMemory memory ? memory.lastNapPad().orElse(null) : null;
        boolean homeIsPad = this.usable(home, ChargingPadBlock.class);
        boolean lastPadUsable = !homeIsPad && this.usable(lastPad, ChargingPadBlock.class);
        boolean homeIsBay = !homeIsPad && !lastPadUsable && this.usable(home, CompanionBayBlock.class);
        return switch (NapSpots.choose(homeIsPad, lastPadUsable, homeIsBay)) {
            case HOME_PAD -> home == null ? null : new Spot(home.pos().above(), home.pos());
            case LAST_PAD -> lastPad == null ? null : new Spot(lastPad.pos().above(), lastPad.pos());
            case BESIDE_BAY -> {
                if (home == null) {
                    yield null;
                }
                BlockPos bay = home.pos();
                int side = NapSpots.firstFree(i -> this.pet.isSafeSpot(bay.offset(NapSpots.BESIDE[i][0],
                        NapSpots.BESIDE[i][1], NapSpots.BESIDE[i][2])));
                yield side < 0 ? null : new Spot(bay.offset(NapSpots.BESIDE[side][0], NapSpots.BESIDE[side][1],
                        NapSpots.BESIDE[side][2]), null);
            }
            case NONE -> null;
        };
    }

    private boolean wantsNap(Spot at) {
        ServerPlayer owner = this.pet.owner();
        if (owner == null) {
            return false;
        }
        if (owner.isSleeping()) {
            return true;
        }
        return this.pet.mode() == CompanionMode.STAY
                && this.pet.stayAnchor().distanceToSqr(at.centre()) <= STAY_NEAR_SPOT * STAY_NEAR_SPOT;
    }

    @Override
    public boolean canUse() {
        if (this.pet.tickCount < this.nextCheck || this.pet.isPerched() || this.pet.isWorking()) {
            return false;
        }
        this.nextCheck = this.pet.tickCount + CHECK_INTERVAL;
        Spot at = this.napSpot();
        if (at == null || !this.wantsNap(at)) {
            return false;
        }
        this.spot = at;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (this.pet.isPerched() || this.pet.isWorking() || this.spot == null) {
            return false;
        }
        if (this.pet.tickCount < this.nextRecheck) {
            return true;
        }
        this.nextRecheck = this.pet.tickCount + CHECK_INTERVAL;
        Spot at = this.napSpot();
        if (at == null || !this.wantsNap(at)) {
            return false;
        }
        if (!at.equals(this.spot)) {
            this.spot = at;
            this.resting = false;
            this.pet.setNapping(false);
        }
        return true;
    }

    @Override
    public void start() {
        this.resting = false;
        this.nextPath = 0;
        this.nextRecheck = this.pet.tickCount + CHECK_INTERVAL;
    }

    @Override
    public void stop() {
        this.resting = false;
        this.spot = null;
        this.pet.setNapping(false);
        this.pet.getNavigation().stop();
    }

    /** Whether the pet is lying at its nap spot right now. */
    public boolean resting() {
        return this.resting;
    }

    @Override
    public void tick() {
        Spot at = this.spot;
        if (at == null) {
            return;
        }
        if (this.resting) {
            this.pet.getNavigation().stop();
            return;
        }
        if (at.reached(this.pet.blockPosition())) {
            this.resting = true;
            this.pet.setNapping(true);
            this.pet.getNavigation().stop();
        } else if (this.pet.getNavigation().isDone() && --this.nextPath <= 0) {
            this.nextPath = 20;
            Vec3 c = at.centre();
            this.pet.getNavigation().moveTo(c.x, c.y, c.z, 1.0D);
        }
    }
}
