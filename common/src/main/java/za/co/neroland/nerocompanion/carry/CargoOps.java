package za.co.neroland.nerocompanion.carry;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.Container;
import net.minecraft.world.LockCode;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.phys.Vec3;

import za.co.neroland.nerocompanion.authority.Decision;
import za.co.neroland.nerocompanion.authority.OwnerAuthority;
import za.co.neroland.nerocompanion.entity.CompanionEntity;
import za.co.neroland.nerocompanion.entity.CompanionMode;
import za.co.neroland.nerocompanion.entity.HomePos;
import za.co.neroland.nerocompanion.entity.types.CarryBotEntity;

/**
 * Server-side cargo moves shared by the mining and carry bots: into a container (only after
 * {@code OwnerAuthority.mayOpen}), into another bot of the same owner, into the owner's inventory. Nothing here
 * ever gives items to, or drops them at, any player but the bot's own owner.
 */
public final class CargoOps {

    private CargoOps() {
    }

    public static int occupied(SimpleContainer cargo) {
        int used = 0;
        for (int i = 0; i < cargo.getContainerSize(); i++) {
            if (!cargo.getItem(i).isEmpty()) {
                used++;
            }
        }
        return used;
    }

    /** Total items aboard (to tell whether a hand-over actually moved anything). */
    public static int itemCount(SimpleContainer cargo) {
        int total = 0;
        for (int i = 0; i < cargo.getContainerSize(); i++) {
            total += cargo.getItem(i).getCount();
        }
        return total;
    }

    public static boolean hasFreeSlot(SimpleContainer cargo) {
        return CarryRules.mayPickUp(occupied(cargo), cargo.getContainerSize());
    }

    /**
     * The container block entity at a bound position, if it is loaded, in this level, and still a container - or,
     * with NeroLogistics installed, an insert-only inlet in front of an active network drive bay.
     */
    public static Container containerAt(ServerLevel level, HomePos bound) {
        if (!bound.isIn(level) || !level.isLoaded(bound.pos())) {
            return null;
        }
        BlockEntity blockEntity = level.getBlockEntity(bound.pos());
        if (blockEntity instanceof Container container) {
            return container;
        }
        return za.co.neroland.nerocompanion.compat.nerologistics.NeroLogisticsCompat.driveBayInlet(level, bound.pos(),
                blockEntity);
    }

    public static boolean inReach(CompanionEntity bot, BlockPos pos) {
        return bot.position().distanceToSqr(Vec3.atCenterOf(pos)) <= CarryRules.REACH * CarryRules.REACH;
    }

    public static ItemStack insert(Container container, ItemStack stack) {
        return HopperBlockEntity.addItem(null, container, stack, null);
    }

    /** Hands a stack to the owner's inventory; returns what did not fit (never voided, creative included). */
    public static ItemStack give(ServerPlayer owner, ItemStack stack) {
        ItemStack copy = stack.copy();
        if (owner.getInventory().getFreeSlot() < 0 && owner.getInventory().getSlotWithRemainingSpace(copy) < 0) {
            return copy;
        }
        owner.getInventory().add(copy);
        return copy;
    }

    /**
     * Whether the bot may use the container at {@code bound} right now, as its owner: a vanilla lock is read
     * <em>silently</em> first (the lock component against the owner's main hand - no "locked" message or sound),
     * then {@code OwnerAuthority.mayOpen}. Asked at bind time, per routed drop, and at every deposit.
     */
    public static Decision mayUse(CompanionEntity bot, ServerLevel level, HomePos bound) {
        if (!bound.isIn(level) || !level.isLoaded(bound.pos())) {
            return Decision.deny("not_container");
        }
        BlockEntity blockEntity = level.getBlockEntity(bound.pos());
        if (!(blockEntity instanceof Container) && containerAt(level, bound) == null) {
            return Decision.deny("not_container");
        }
        if (lockedSilently(blockEntity, bot.owner())) {
            return Decision.deny("locked");
        }
        return OwnerAuthority.mayOpen(bot, level, bound.pos());
    }

    /** A vanilla container lock the owner's main-hand item does not open; read without side effects. */
    private static boolean lockedSilently(BlockEntity blockEntity, ServerPlayer owner) {
        if (!(blockEntity instanceof BaseContainerBlockEntity)) {
            return false;
        }
        LockCode lock = blockEntity.collectComponents().get(DataComponents.LOCK);
        if (lock == null || LockCode.NO_LOCK.equals(lock)) {
            return false;
        }
        return owner == null || !lock.unlocksWith(owner.getMainHandItem());
    }

    /** Whether {@code container} could take at least part of one of the cargo stacks (a free or mergeable slot). */
    public static boolean hasRoomFor(Container container, SimpleContainer cargo) {
        for (int c = 0; c < cargo.getContainerSize(); c++) {
            ItemStack stack = cargo.getItem(c);
            if (stack.isEmpty()) {
                continue;
            }
            for (int i = 0; i < container.getContainerSize(); i++) {
                ItemStack slot = container.getItem(i);
                if (slot.isEmpty() ? container.canPlaceItem(i, stack)
                        : ItemStack.isSameItemSameComponents(slot, stack)
                                && slot.getCount() < Math.min(slot.getMaxStackSize(), container.getMaxStackSize())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Deposits the whole cargo into {@code bound} after {@link #mayUse} - every time. A container that throws
     * part-way keeps what it took and the cargo keeps the rest (no dupe).
     */
    public static DepositRules.Result<ItemStack> depositAll(CompanionEntity bot, ServerLevel level, HomePos bound) {
        Decision mayOpen = mayUse(bot, level, bound);
        Container container = mayOpen.allowed() ? containerAt(level, bound) : null;
        SimpleContainer cargo = bot.cargo();
        if (container == null) {
            Decision denied = mayOpen.allowed() ? Decision.deny("not_container") : mayOpen;
            return DepositRules.deposit(denied, snapshot(cargo), stack -> stack, ItemStack::getCount);
        }
        DepositRules.Result<ItemStack> result = DepositRules.deposit(mayOpen, snapshot(cargo),
                stack -> insert(container, stack), ItemStack::getCount);
        writeBack(cargo, result.remaining());
        container.setChanged();
        return result;
    }

    /** Moves as much cargo as fits from one bot into another (same owner checked by the caller). */
    public static void handOver(SimpleContainer from, SimpleContainer to) {
        for (int i = 0; i < from.getContainerSize(); i++) {
            ItemStack stack = from.getItem(i);
            if (!stack.isEmpty()) {
                from.setItem(i, to.addItem(stack.copy()));
            }
        }
    }

    /** Moves as much cargo as fits into the owner's inventory. */
    public static void handTo(SimpleContainer from, ServerPlayer owner) {
        for (int i = 0; i < from.getContainerSize(); i++) {
            ItemStack stack = from.getItem(i);
            if (!stack.isEmpty()) {
                from.setItem(i, give(owner, stack));
            }
        }
    }

    /** A copy of every slot, in order. */
    public static List<ItemStack> snapshot(SimpleContainer cargo) {
        List<ItemStack> out = new ArrayList<>(cargo.getContainerSize());
        for (int i = 0; i < cargo.getContainerSize(); i++) {
            out.add(cargo.getItem(i).copy());
        }
        return out;
    }

    private static void writeBack(SimpleContainer cargo, List<ItemStack> remaining) {
        for (int i = 0; i < Math.min(cargo.getContainerSize(), remaining.size()); i++) {
            cargo.setItem(i, remaining.get(i));
        }
    }

    /** The nearest carry bot of the same owner, in CARRY mode, awake, with a free slot, within {@code range}. */
    public static CarryBotEntity nearbyCarryBot(CompanionEntity bot, ServerLevel level, double range) {
        if (bot.ownerId() == null) {
            return null;
        }
        CarryBotEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (CarryBotEntity carry : level.getEntitiesOfClass(CarryBotEntity.class,
                bot.getBoundingBox().inflate(range), candidate -> candidate != bot && candidate.isAlive()
                        && !candidate.isInert() && bot.ownerId().equals(candidate.ownerId())
                        && candidate.effectiveMode() == CompanionMode.CARRY)) {
            double distance = carry.distanceToSqr(bot);
            if (distance < bestDistance && hasFreeSlot(carry.cargo())) {
                best = carry;
                bestDistance = distance;
            }
        }
        return best;
    }

    /** Owner message on the action bar (lang key + args); no-op when the owner is offline. */
    public static void tell(CompanionEntity bot, String key, Object... args) {
        ServerPlayer owner = bot.owner();
        if (owner != null) {
            owner.sendSystemMessage(Component.translatable(key, args), true);
        }
    }
}
