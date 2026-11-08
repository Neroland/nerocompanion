package za.co.neroland.nerocompanion.ai.repair;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The owner's gear the Repair Bot looks at, by a stable index: 0-3 armour (head, chest, legs, feet), 4 the off hand,
 * 5-13 the hotbar. Nothing else of the inventory is touched. Server only.
 */
public final class RepairTargets {

    private static final EquipmentSlot[] EQUIPMENT = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
            EquipmentSlot.FEET, EquipmentSlot.OFFHAND};
    private static final int HOTBAR = 9;

    /** Number of indexed slots. */
    public static final int SIZE = EQUIPMENT.length + HOTBAR;

    private RepairTargets() {
    }

    /** The stack at {@code index}, or {@link ItemStack#EMPTY} for an out-of-range index. */
    public static ItemStack stackAt(Player owner, int index) {
        if (index < 0 || index >= SIZE) {
            return ItemStack.EMPTY;
        }
        if (index < EQUIPMENT.length) {
            return owner.getItemBySlot(EQUIPMENT[index]);
        }
        return owner.getInventory().getItem(index - EQUIPMENT.length);
    }

    /** Whether the stack can and needs to take repair. */
    public static boolean needsRepair(ItemStack stack) {
        return !stack.isEmpty() && stack.isDamageableItem() && stack.getDamageValue() > 0;
    }

    /** The most-damaged repairable piece of the owner's armour, off hand and hotbar ({@link RepairMath#mostDamaged}). */
    public static OptionalInt mostDamaged(Player owner) {
        List<RepairMath.Slot> slots = new ArrayList<>(SIZE);
        for (int i = 0; i < SIZE; i++) {
            ItemStack stack = stackAt(owner, i);
            if (stack.isEmpty()) {
                continue;
            }
            boolean damageable = stack.isDamageableItem();
            slots.add(new RepairMath.Slot(i, damageable, damageable ? stack.getDamageValue() : 0,
                    damageable ? stack.getMaxDamage() : 0));
        }
        return RepairMath.mostDamaged(slots);
    }
}
