package za.co.neroland.nerocompanion.ai.carry;

import java.util.EnumSet;
import java.util.Optional;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import za.co.neroland.nerocompanion.authority.Decision;
import za.co.neroland.nerocompanion.authority.OwnerAuthority;
import za.co.neroland.nerocompanion.carry.CargoOps;
import za.co.neroland.nerocompanion.carry.CarryRules;
import za.co.neroland.nerocompanion.carry.DepositRules;
import za.co.neroland.nerocompanion.chip.ChipEffects;
import za.co.neroland.nerocompanion.chip.ChipKinds;
import za.co.neroland.nerocompanion.config.NeroCompanionConfig;
import za.co.neroland.nerocompanion.entity.CompanionEntity;
import za.co.neroland.nerocompanion.entity.HomePos;
import za.co.neroland.nerocompanion.entity.type.CompanionType;
import za.co.neroland.nerocompanion.entity.types.CarryBotEntity;
import za.co.neroland.nerocompanion.mining.Backoff;
import za.co.neroland.nerocompanion.mining.DropRouter;
import za.co.neroland.nerocompanion.mining.MineRules;
import za.co.neroland.nerocompanion.mining.StuckTracker;
import za.co.neroland.nerocompanion.salvage.Salvage;

/**
 * {@code CARRY}: pick up loose items within the pickup radius and deliver them. A deposit run goes to the bound
 * container ({@code OwnerAuthority.mayOpen} asked again right at the container) or, unbound, to the owner; it
 * starts when the owner asks (sneak-use the Remote on the bound container) or, with the Auto-Deposit chip, at
 * {@link CarryRules#AUTO_DEPOSIT_FILL} full.
 *
 * <p>Only items no other player threw are picked up, and only while the owner is online in this dimension.
 * Scans run on a 10-tick cadence; the target is cached between scans.
 *
 * <p><b>"Come here" fast path:</b> the Remote's "come here" switches to FOLLOW, which removes this goal at once
 * (mid-pickup or mid-deposit). Inside CARRY, a pickup is dropped the moment the owner is farther than the chassis'
 * teleport distance, so the follow leash takes over; only a deposit run may leave the leash. Cargo stays aboard.
 */
public class CarryGoal extends Goal {

    private static final int SCAN_INTERVAL = 10;
    private static final int REPATH_INTERVAL = 10;
    private static final double OTHER_PLAYER_REACH = 1.5D;
    private static final double PICKUP_REACH = 1.75D;

    private final CompanionEntity companion;
    private ItemEntity item;
    private boolean depositing;
    private int nextScanTick;
    private int repathIn;
    private final StuckTracker stuck = new StuckTracker();
    private final Backoff backoff = new Backoff();
    private Vec3 lastPos = Vec3.ZERO;
    private boolean warned;
    private DropRouter.Stage depositStage;
    private Vec3 depositAt;

    public CarryGoal(CompanionEntity companion, CompanionType type) {
        this.companion = companion;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    private int radius() {
        CompanionType def = this.companion.typeDef();
        return CarryRules.pickupRadius(def != null ? def.pickupRadius() : CarryRules.DEFAULT_PICKUP_RADIUS,
                ChipEffects.rangeBonus(this.companion.activeChips()));
    }

    private boolean ownerNear() {
        ServerPlayer owner = this.companion.owner();
        if (owner == null || owner.level() != this.companion.level()) {
            return false;
        }
        CompanionType type = this.companion.typeDef();
        double leash = type == null ? 16.0D : type.goalWeights().teleportDistance();
        return this.companion.distanceToSqr(owner) < leash * leash;
    }

    @Override
    public boolean canUse() {
        if (!(this.companion.level() instanceof ServerLevel) || this.companion.isPerched()
                || this.companion.tickCount < this.nextScanTick) {
            return false;
        }
        this.nextScanTick = this.companion.tickCount + SCAN_INTERVAL;
        if (!this.ownerInWorkRange()) {
            return false;
        }
        if (this.wantsDeposit()) {
            this.depositing = true;
            return true;
        }
        if (!this.ownerNear()) {
            return false;
        }
        this.item = this.findItem();
        return this.item != null;
    }

    @Override
    public boolean canContinueToUse() {
        if (this.companion.isPerched() || !this.ownerInWorkRange()) {
            return false;
        }
        if (this.depositing) {
            return true; // a deposit run may leave the leash; it ends at the container
        }
        // The "come here" fast path: a pickup yields to the follow leash the moment the owner is out of it.
        return this.ownerNear() && this.item != null && this.item.isAlive();
    }

    /** Deposits and pickups need the owner in this level within {@code sleepDistance} (default 64). */
    private boolean ownerInWorkRange() {
        ServerPlayer owner = this.companion.owner();
        boolean here = owner != null && owner.level() == this.companion.level();
        return MineRules.ownerInRange(here, here ? this.companion.distanceToSqr(owner) : Double.MAX_VALUE,
                NeroCompanionConfig.limits().sleepDistance());
    }

    @Override
    public void start() {
        this.repathIn = 0;
        this.stuck.reset();
        this.lastPos = this.companion.position();
        this.companion.setWorking(true);
    }

    @Override
    public void stop() {
        this.item = null;
        this.depositing = false;
        this.companion.getNavigation().stop();
        this.companion.setWorking(false);
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (!(this.companion.level() instanceof ServerLevel level)) {
            return;
        }
        if (this.depositing) {
            this.deposit(level);
            return;
        }
        if (this.item == null || !this.item.isAlive()) {
            this.item = null;
            return;
        }
        Vec3 at = this.item.position();
        if (this.companion.position().distanceToSqr(at) > PICKUP_REACH * PICKUP_REACH) {
            if (this.walkTo(at)) {
                this.item = null; // unreachable: forget it, the next scan picks another
            }
            return;
        }
        SimpleContainer cargo = this.companion.cargo();
        ItemStack left = cargo.addItem(this.item.getItem().copy());
        if (left.getCount() != this.item.getItem().getCount()) {
            this.companion.impulse(CompanionEntity.IMPULSE_PICKUP);
        }
        if (left.isEmpty()) {
            this.item.discard();
        } else {
            this.item.setItem(left);
        }
        this.item = null;
    }

    // --- pickups --------------------------------------------------------------------------------------

    private ItemEntity findItem() {
        SimpleContainer cargo = this.companion.cargo();
        if (!CarryRules.mayPickUp(CargoOps.occupied(cargo), cargo.getContainerSize())) {
            this.warnFull();
            return null;
        }
        this.warned = false;
        ServerLevel level = (ServerLevel) this.companion.level();
        ItemEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (ItemEntity candidate : level.getEntitiesOfClass(ItemEntity.class,
                this.companion.getBoundingBox().inflate(this.radius()), this::mayTake)) {
            double distance = candidate.distanceToSqr(this.companion);
            if (distance < bestDistance && cargo.canAddItem(candidate.getItem())) {
                best = candidate;
                bestDistance = distance;
            }
        }
        if (best == null || OwnerAuthority.mayEnter(this.companion, level, best.blockPosition()).denied()) {
            return null;
        }
        // Courtesy, not authority: leave items another player is standing on (a shared farm, their own pickup).
        ItemEntity chosen = best;
        if (!level.getEntitiesOfClass(Player.class, chosen.getBoundingBox().inflate(OTHER_PLAYER_REACH),
                player -> !player.isSpectator() && !player.getUUID().equals(this.companion.ownerId())).isEmpty()) {
            return null;
        }
        return chosen;
    }

    /**
     * Never items another player threw (their drops are theirs), never another player's death drops while their
     * salvage window is open, never items still on pickup delay.
     */
    private boolean mayTake(ItemEntity candidate) {
        Entity thrower = candidate.getOwner();
        return CarryRules.mayTake(candidate.isAlive(), candidate.hasPickUpDelay(), candidate.getItem().isEmpty(),
                thrower instanceof Player player ? player.getUUID() : null, this.companion.ownerId(),
                Salvage.isAnotherPlayersDrop(this.companion, candidate));
    }

    // --- deposits -------------------------------------------------------------------------------------

    private boolean wantsDeposit() {
        SimpleContainer cargo = this.companion.cargo();
        boolean requested = this.companion instanceof CarryBotEntity carry && carry.depositRequested();
        boolean chip = ChipEffects.has(this.companion.activeChips(), ChipKinds.AUTO_DEPOSIT);
        if (!CarryRules.shouldDeposit(CargoOps.occupied(cargo), cargo.getContainerSize(), chip, requested)) {
            if (requested) {
                ((CarryBotEntity) this.companion).clearDepositRequest(); // nothing aboard
            }
            return false;
        }
        return this.planDelivery();
    }

    /**
     * Picks where this run goes, once per run: the bound container when it is usable (in range, not backed off,
     * silent lock + {@code mayOpen}, room), else the owner. False when neither is available.
     */
    private boolean planDelivery() {
        ServerLevel level = (ServerLevel) this.companion.level();
        Optional<HomePos> bound = this.usableContainer(level);
        if (bound.isPresent()) {
            this.depositStage = DropRouter.Stage.BOUND_CONTAINER;
            this.depositAt = Vec3.atCenterOf(bound.get().pos());
            return true;
        }
        ServerPlayer owner = this.companion.owner();
        if (this.backoff.blocked(DropRouter.Stage.OWNER, level.getGameTime()) || owner == null
                || owner.level() != level) {
            return false;
        }
        this.depositStage = DropRouter.Stage.OWNER;
        this.depositAt = owner.position();
        return true;
    }

    /** Where the planned run goes now (the owner moves; a container does not). */
    private Vec3 deliveryPoint() {
        if (this.depositStage == DropRouter.Stage.OWNER) {
            ServerPlayer owner = this.companion.owner();
            return owner != null && owner.level() == this.companion.level() ? owner.position() : null;
        }
        return this.depositAt;
    }

    /**
     * The bound container if it is in range, not backed off, usable by the owner (silent lock + {@code mayOpen}) and
     * has room; a container that fails any of those is backed off and the run falls back to the owner.
     */
    private Optional<HomePos> usableContainer(ServerLevel level) {
        Optional<HomePos> bound = this.companion.boundContainer();
        long now = level.getGameTime();
        if (bound.isEmpty() || this.backoff.blocked(DropRouter.Stage.BOUND_CONTAINER, now)) {
            return Optional.empty();
        }
        net.minecraft.world.Container container = CargoOps.containerAt(level, bound.get());
        if (container == null || this.companion.position().distanceToSqr(Vec3.atCenterOf(bound.get().pos()))
                > CarryRules.CONTAINER_RANGE * CarryRules.CONTAINER_RANGE) {
            return Optional.empty();
        }
        Decision mayUse = CargoOps.mayUse(this.companion, level, bound.get());
        if (mayUse.denied()) {
            this.backoff.fail(DropRouter.Stage.BOUND_CONTAINER, now);
            CargoOps.tell(this.companion, "message.nerocompanion.carry.deposit_denied",
                    this.companion.getDisplayName(), Component.translatable(mayUse.reasonKey()));
            return Optional.empty();
        }
        if (!CargoOps.hasRoomFor(container, this.companion.cargo())) {
            this.backoff.fail(DropRouter.Stage.BOUND_CONTAINER, now);
            CargoOps.tell(this.companion, "message.nerocompanion.carry.container_full",
                    this.companion.getDisplayName());
            return Optional.empty();
        }
        return bound;
    }

    private void deposit(ServerLevel level) {
        Vec3 at = this.deliveryPoint();
        if (at == null) {
            this.endDeposit();
            return;
        }
        DropRouter.Stage stage = this.depositStage;
        Optional<HomePos> bound = stage == DropRouter.Stage.BOUND_CONTAINER ? this.companion.boundContainer()
                : Optional.empty();
        if (this.companion.position().distanceToSqr(at) > CarryRules.REACH * CarryRules.REACH) {
            if (this.walkTo(at)) {
                this.backoff.fail(stage, level.getGameTime());
                this.endDeposit();
            }
            return;
        }
        this.companion.getNavigation().stop();
        SimpleContainer cargo = this.companion.cargo();
        int before = CargoOps.itemCount(cargo);
        if (bound.isPresent()) {
            DepositRules.Result<ItemStack> result = CargoOps.depositAll(this.companion, level, bound.get());
            if (result.denied()) {
                CargoOps.tell(this.companion, "message.nerocompanion.carry.deposit_denied",
                        this.companion.getDisplayName(), Component.translatable(result.reasonKey()));
            } else if (!result.movedAny()) {
                CargoOps.tell(this.companion, "message.nerocompanion.carry.container_full",
                        this.companion.getDisplayName());
            }
        } else {
            ServerPlayer owner = this.companion.owner();
            if (owner != null && owner.position().distanceToSqr(this.companion.position())
                    <= CarryRules.REACH * CarryRules.REACH * 4.0D) {
                CargoOps.handTo(cargo, owner);
            }
        }
        if (CargoOps.itemCount(cargo) < before) {
            this.backoff.clear(stage);
            this.companion.jobDone(za.co.neroland.nerocompanion.progression.CompanionProgress.Work.CARRY); // only a deposit that moved something counts as work done
        } else {
            this.backoff.fail(stage, level.getGameTime()); // refused or full: the next run falls through
        }
        this.endDeposit();
    }

    private void endDeposit() {
        this.depositing = false;
        this.stuck.reset();
        if (this.companion instanceof CarryBotEntity carry) {
            carry.clearDepositRequest();
        }
        this.nextScanTick = this.companion.tickCount + 2 * SCAN_INTERVAL;
    }

    /** Walks toward {@code at}; true once the walk is judged unreachable ({@link StuckTracker}). */
    private boolean walkTo(Vec3 at) {
        if (--this.repathIn > 0) {
            return false;
        }
        this.repathIn = this.adjustedTickDelay(REPATH_INTERVAL);
        boolean path = this.companion.getNavigation().moveTo(at.x, at.y, at.z, 1.0D);
        Vec3 now = this.companion.position();
        boolean unreachable = this.stuck.check(path, now.distanceTo(at), now.distanceTo(this.lastPos));
        this.lastPos = now;
        return unreachable;
    }

    private void warnFull() {
        if (!this.warned) {
            this.warned = true;
            CargoOps.tell(this.companion, "message.nerocompanion.carry.full", this.companion.getDisplayName());
        }
        this.nextScanTick = this.companion.tickCount + 4 * SCAN_INTERVAL;
    }
}
