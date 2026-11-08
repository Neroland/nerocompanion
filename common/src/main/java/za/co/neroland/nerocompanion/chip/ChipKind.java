package za.co.neroland.nerocompanion.chip;

import java.util.Optional;

import net.minecraft.resources.Identifier;

import za.co.neroland.nerolandcore.upgrade.UpgradeType;

import za.co.neroland.nerocompanion.entity.CompanionMode;
import za.co.neroland.nerocompanion.entity.type.ChipCategory;

/**
 * One kind of chip: what it is, not which item. Stat kinds come in tiers I-III (one item per tier); every other
 * kind has a single tier. {@code coreType} is set only for the four stat families that are Core upgrade concepts
 * (speed, efficiency, range, capacity); {@code unlocks} only for a capability chip that adds a mode.
 */
public record ChipKind(Identifier id, ChipCategory category, int maxTier, Optional<UpgradeType> coreType,
        Optional<CompanionMode> unlocks) {

    public ChipKind {
        if (maxTier < 1 || maxTier > 3) {
            throw new IllegalArgumentException("maxTier must be 1..3");
        }
    }

    /** The kind's path ({@code speed}, {@code lamp}...), used for item ids and lang keys. */
    public String name() {
        return this.id.getPath();
    }

    public boolean tiered() {
        return this.maxTier > 1;
    }

    /** Registry name of the item for {@code tier}: {@code speed_chip_2}, or {@code lamp_chip} for a single tier. */
    public String itemName(int tier) {
        return this.tiered() ? this.name() + "_chip_" + tier : this.name() + "_chip";
    }
}
