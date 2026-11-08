package za.co.neroland.nerocompanion.chip;

import java.util.List;
import java.util.function.BiFunction;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import za.co.neroland.nerolandcore.upgrade.UpgradeContainer;
import za.co.neroland.nerolandcore.upgrade.UpgradeType;

import za.co.neroland.nerocompanion.entity.ChipStack;

/**
 * A companion's installed chips as a Core {@link UpgradeContainer} (slot storage, NeroCompanion classifier), so
 * any Core-side consumer counts the Core-shaped part ({@link #coreCount}). The persisted form stays
 * {@code CompanionRecord.chips}; this is rebuilt from it whenever the chips change. Slots are capped by Core's
 * {@code upgradeModuleSlotCap}.
 */
public final class ChipSlots {

    private final UpgradeContainer container;

    private ChipSlots(UpgradeContainer container) {
        this.container = container;
    }

    /**
     * Builds the container for {@code slots} slots holding {@code chips} (slot order). {@code itemFor} maps a chip
     * to its item (null if unknown; that slot stays empty here but the record keeps the chip).
     */
    public static ChipSlots of(int slots, List<ChipStack> chips, BiFunction<ChipKind, Integer, Item> itemFor) {
        UpgradeContainer container = new UpgradeContainer(Math.max(1, slots), ChipItem.CLASSIFIER, () -> { });
        for (int i = 0; i < chips.size() && i < container.slots(); i++) {
            ChipStack chip = chips.get(i);
            Item item = ChipKinds.byId(chip.chip()).map(kind -> itemFor.apply(kind, chip.tier())).orElse(null);
            container.setStack(i, item == null ? ItemStack.EMPTY : new ItemStack(item));
        }
        return new ChipSlots(container);
    }

    public UpgradeContainer container() {
        return this.container;
    }

    /** How many installed chips Core counts as {@code type}. */
    public int coreCount(UpgradeType type) {
        return this.container.count(type);
    }
}
