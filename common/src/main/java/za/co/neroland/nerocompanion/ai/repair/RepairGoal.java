package za.co.neroland.nerocompanion.ai.repair;

import java.util.EnumSet;
import java.util.OptionalInt;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;

import za.co.neroland.nerocompanion.chip.ChipEffects;
import za.co.neroland.nerocompanion.energy.CompanionEnergy;
import za.co.neroland.nerocompanion.entity.CompanionEntity;
import za.co.neroland.nerocompanion.entity.type.CompanionType;

/**
 * REPAIR mode: every {@link RepairMath#SCAN_INTERVAL} ticks, while the online owner is within range in the same
 * dimension, pick the most-damaged repairable item among the owner's armour, off hand and hotbar and restore its
 * durability a few points a tick at {@link RepairMath#FE_PER_POINT} FE each, stopping at the
 * {@link RepairMath#RESERVE_PERCENT} % reserve. Only items that can take damage are touched; nothing is created,
 * moved or removed. Movement is left to the follow goal (this goal takes only LOOK), so the bot stays at the
 * owner's side while it works.
 */
public final class RepairGoal extends Goal {

    private final CompanionEntity bot;
    private int slot = -1;
    private int nextScan;

    public RepairGoal(CompanionEntity bot, CompanionType type) {
        this.bot = bot;
        this.setFlags(EnumSet.of(Goal.Flag.LOOK));
    }

    private double rangeSq() {
        double range = RepairMath.range(ChipEffects.rangeBonus(this.bot.activeChips()));
        return range * range;
    }

    /** The owner, if online, in this level and within repair range. */
    private ServerPlayer ownerInRange() {
        ServerPlayer owner = this.bot.owner();
        if (owner == null || owner.level() != this.bot.level() || owner.isSpectator()) {
            return null;
        }
        return this.bot.distanceToSqr(owner) <= this.rangeSq() ? owner : null;
    }

    private boolean canAffordAPoint() {
        CompanionEnergy energy = this.bot.energy();
        return RepairMath.affordablePoints(energy.stored(), energy.capacity(), 1) > 0;
    }

    @Override
    public boolean canUse() {
        if (this.bot.tickCount < this.nextScan) {
            return false;
        }
        this.nextScan = this.bot.tickCount + RepairMath.SCAN_INTERVAL;
        ServerPlayer owner = this.ownerInRange();
        if (owner == null || !this.canAffordAPoint()) {
            return false;
        }
        OptionalInt found = RepairTargets.mostDamaged(owner);
        this.slot = found.orElse(-1);
        return this.slot >= 0;
    }

    @Override
    public boolean canContinueToUse() {
        if (this.slot < 0 || !this.canAffordAPoint()) {
            return false;
        }
        ServerPlayer owner = this.ownerInRange();
        return owner != null && RepairTargets.needsRepair(RepairTargets.stackAt(owner, this.slot));
    }

    @Override
    public void start() {
        this.bot.setWorking(true);
    }

    @Override
    public void stop() {
        this.bot.setWorking(false);
        this.slot = -1;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        ServerPlayer owner = this.ownerInRange();
        if (owner == null || this.slot < 0) {
            return;
        }
        this.bot.getLookControl().setLookAt(owner, 10.0F, this.bot.getMaxHeadXRot());
        ItemStack stack = RepairTargets.stackAt(owner, this.slot);
        if (!RepairTargets.needsRepair(stack)) {
            return;
        }
        int damage = stack.getDamageValue();
        double speed = ChipEffects.speedMultiplier(this.bot.activeChips());
        int wanted = Math.min(damage, RepairMath.pointsPerTick(speed));
        CompanionEnergy energy = this.bot.energy();
        int points = RepairMath.affordablePoints(energy.stored(), energy.capacity(), wanted);
        if (points <= 0 || !energy.tryPay(RepairMath.cost(points))) {
            return;
        }
        stack.setDamageValue(damage - points);
        if (damage - points <= 0) {
            this.bot.jobDone(za.co.neroland.nerocompanion.progression.CompanionProgress.Work.REPAIR);
            this.slot = -1;
        }
    }
}
