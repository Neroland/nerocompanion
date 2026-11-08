package za.co.neroland.nerocompanion.ai.drone;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/** Moving drone cargo into the owner's inventory (server). Nothing is ever dropped or deleted here. */
public final class DroneCargo {

    private DroneCargo() {
    }

    /** Hands every cargo slot to {@code owner} as far as it fits. True if anything moved. */
    public static boolean handAll(ServerPlayer owner, SimpleContainer cargo) {
        boolean moved = false;
        for (int i = 0; i < cargo.getContainerSize(); i++) {
            moved |= give(owner, cargo, i);
        }
        return moved;
    }

    /** Moves cargo slot {@code slot} into the owner's inventory as far as it fits. True if anything moved. */
    public static boolean give(ServerPlayer owner, SimpleContainer cargo, int slot) {
        ItemStack stack = cargo.getItem(slot);
        if (stack.isEmpty()) {
            return false;
        }
        ItemStack moving = stack.copy();
        int before = moving.getCount();
        owner.getInventory().add(moving);
        if (moving.getCount() == before) {
            return false;
        }
        cargo.setItem(slot, moving.isEmpty() ? ItemStack.EMPTY : moving);
        cargo.setChanged();
        return true;
    }

    /** Whether the owner's inventory has room for (some of) {@code stack}. */
    public static boolean hasRoom(Inventory inventory, ItemStack stack) {
        return inventory.getFreeSlot() >= 0 || inventory.getSlotWithRemainingSpace(stack) >= 0;
    }
}
