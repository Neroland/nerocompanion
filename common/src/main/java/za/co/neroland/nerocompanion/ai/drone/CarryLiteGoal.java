package za.co.neroland.nerocompanion.ai.drone;

import java.util.EnumSet;
import java.util.UUID;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerocompanion.authority.OwnerAuthority;
import za.co.neroland.nerocompanion.entity.CompanionEntity;
import za.co.neroland.nerocompanion.entity.CompanionMode;
import za.co.neroland.nerocompanion.salvage.Salvage;

/**
 * Carry-lite for the Personal Drone (FOLLOW and CARRY): on a {@value PickupRules#SCAN_TICKS}-tick cadence look for the
 * nearest item entity within {@value PickupRules#PICKUP_RADIUS} blocks that {@link PickupRules} allows, fly to it and
 * put it in the cargo. In FOLLOW the drone is an overflow buffer: it only takes what the owner has no room for, and
 * hands cargo back once the owner has room again (perched or not). Owner online and in the same level only; items
 * farther than pickup radius + follow distance from the owner are ignored. An item not reached within
 * {@value PickupRules#GIVE_UP_TICKS} ticks (or {@value PickupRules#STALLED_TICKS} once the navigator has stopped) is
 * skipped for {@value RetryMemory#BACKOFF_TICKS} ticks ({@link RetryMemory}).
 */
public final class CarryLiteGoal extends Goal {

    private final CompanionEntity companion;
    @Nullable
    private ItemEntity target;
    private long nextScan = Long.MIN_VALUE;
    private long nextRepath;
    private int running;
    private final RetryMemory failed = new RetryMemory();

    public CarryLiteGoal(CompanionEntity companion) {
        this.companion = companion;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (!(this.companion.level() instanceof ServerLevel level)) {
            return false;
        }
        long now = level.getGameTime();
        if (now < this.nextScan) {
            return false;
        }
        this.nextScan = now + PickupRules.SCAN_TICKS;
        ServerPlayer owner = this.companion.owner();
        if (owner == null || !this.companion.ownerHere()) {
            return false;
        }
        // Hand-back is an inventory transfer, not movement: it runs while perched too.
        this.handBack(owner);
        if (this.companion.isPerched()) {
            return false;
        }
        this.failed.prune(now);
        this.target = this.findTarget(level, owner);
        return this.target != null;
    }

    @Override
    public boolean canContinueToUse() {
        ServerPlayer owner = this.companion.owner();
        return this.target != null && owner != null && this.companion.ownerHere()
                && !this.companion.isPerched() && this.allowed(this.target, owner);
    }

    @Override
    public void start() {
        this.running = 0;
        this.nextRepath = 0L;
        this.companion.setWorking(true);
    }

    @Override
    public void stop() {
        this.target = null;
        this.companion.getNavigation().stop();
        this.companion.setWorking(false);
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        ItemEntity item = this.target;
        if (item == null) {
            return;
        }
        this.running++;
        this.companion.getLookControl().setLookAt(item, 30.0F, 30.0F);
        if (this.companion.distanceToSqr(item) <= PickupRules.GRAB_DISTANCE * PickupRules.GRAB_DISTANCE) {
            this.grab(item);
            this.target = null;
            return;
        }
        long now = this.companion.level().getGameTime();
        if (PickupRules.giveUp(this.running, this.companion.getNavigation().isDone(), false)) {
            // Unreachable (under a trapdoor, behind glass, on a ledge): skip it for a while.
            this.failed.failed(item.getUUID(), now);
            this.target = null;
            return;
        }
        if (now >= this.nextRepath) {
            this.nextRepath = now + 10L;
            this.companion.getNavigation().moveTo(item.getX(), item.getY() + 0.5D, item.getZ(), 1.2D);
        }
    }

    @Nullable
    private ItemEntity findTarget(ServerLevel level, ServerPlayer owner) {
        if (this.companion.cargoSlots() <= 0) {
            return null;
        }
        ItemEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class,
                this.companion.getBoundingBox().inflate(PickupRules.PICKUP_RADIUS), e -> true)) {
            double d = this.companion.distanceToSqr(item);
            if (d < bestDistance && this.allowed(item, owner)) {
                best = item;
                bestDistance = d;
            }
        }
        // Same claim check as the carry bot: never collect where the owner may not go.
        if (best != null && OwnerAuthority.mayEnter(this.companion, level, best.blockPosition()).denied()) {
            this.failed.failed(best.getUUID(), level.getGameTime());
            return null;
        }
        return best;
    }

    private boolean allowed(ItemEntity item, ServerPlayer owner) {
        ItemStack stack = item.getItem();
        if (stack.isEmpty()) {
            return false;
        }
        Entity thrower = item.getOwner();
        UUID throwerId = thrower == null ? null : thrower.getUUID();
        if (this.failed.blocked(item.getUUID(), this.companion.level().getGameTime())
                || !PickupRules.withinOwnerReach(owner.distanceToSqr(item), this.followDistance())) {
            return false;
        }
        if (!PickupRules.mayPickUp(item.isAlive(), item.hasPickUpDelay(), throwerId, this.companion.ownerId(),
                this.companion.distanceToSqr(item), Salvage.isAnotherPlayersDrop(this.companion, item))) {
            return false;
        }
        boolean room = DroneCargo.hasRoom(owner.getInventory(), stack);
        if (!PickupRules.collects(this.companion.effectiveMode(), room)) {
            return false;
        }
        return this.companion.cargo().canAddItem(stack);
    }

    private double followDistance() {
        return this.companion.typeDef() == null ? 3.0D : this.companion.typeDef().goalWeights().followDistance();
    }

    private void grab(ItemEntity item) {
        if (!item.isAlive() || item.getItem().isEmpty()) {
            return;
        }
        SimpleContainer cargo = this.companion.cargo();
        ItemStack offered = item.getItem().copy();
        ItemStack rest = cargo.addItem(offered);
        if (rest.getCount() != item.getItem().getCount()) {
            this.companion.impulse(CompanionEntity.IMPULSE_PICKUP);
        }
        if (rest.isEmpty()) {
            item.discard();
        } else if (rest.getCount() != item.getItem().getCount()) {
            item.setItem(rest);
        }
    }

    /** In FOLLOW, gives cargo back to an owner who has room again and is close. */
    private void handBack(ServerPlayer owner) {
        if (this.companion.effectiveMode() != CompanionMode.FOLLOW) {
            return;
        }
        SimpleContainer cargo = this.companion.cargo();
        if (cargo.isEmpty()) {
            return;
        }
        double distance = this.companion.distanceToSqr(owner);
        boolean gave = false;
        for (int i = 0; i < cargo.getContainerSize(); i++) {
            ItemStack stack = cargo.getItem(i);
            if (stack.isEmpty() || !PickupRules.handsBack(CompanionMode.FOLLOW,
                    DroneCargo.hasRoom(owner.getInventory(), stack), distance)) {
                continue;
            }
            gave |= DroneCargo.give(owner, cargo, i);
        }
        if (gave) {
            this.companion.jobDone(za.co.neroland.nerocompanion.progression.CompanionProgress.Work.CARRY);
        }
    }
}
