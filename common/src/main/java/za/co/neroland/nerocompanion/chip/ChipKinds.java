package za.co.neroland.nerocompanion.chip;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.minecraft.resources.Identifier;

import za.co.neroland.nerolandcore.upgrade.UpgradeType;

import za.co.neroland.nerocompanion.entity.CompanionMode;
import za.co.neroland.nerocompanion.entity.type.ChipCategory;

/**
 * The chip registry: eighteen kinds (six stat families with tiers I-III, eight capability, four cosmetic), thirty
 * items. A code registry because every chip is an item, items register statically, and the client needs a chip's
 * category, tier and effect for tooltips without a definitions payload. What a modpack may retune - which chips
 * cannot be fitted together - is data ({@link ChipRules}).
 */
public final class ChipKinds {

    public static final String NAMESPACE = "nerocompanion";

    private static final Map<Identifier, ChipKind> BY_ID = new LinkedHashMap<>();

    // --- stat (tiers I-III) -----------------------------------------------------------------------
    public static final ChipKind SPEED = stat("speed", UpgradeType.SPEED);
    public static final ChipKind EFFICIENCY = stat("efficiency", UpgradeType.EFFICIENCY);
    public static final ChipKind RANGE = stat("range", UpgradeType.RANGE);
    public static final ChipKind CAPACITY = stat("capacity", UpgradeType.CAPACITY);
    public static final ChipKind ARMOUR = stat("armour", null);
    public static final ChipKind VITALITY = stat("vitality", null);

    // --- capability (single tier) -----------------------------------------------------------------
    public static final ChipKind GUARD_PROTOCOL = add("guard_protocol", ChipCategory.CAPABILITY, 1, null,
            CompanionMode.GUARD);
    public static final ChipKind ORE_FILTER = capability("ore_filter");
    public static final ChipKind AUTO_DEPOSIT = capability("auto_deposit");
    public static final ChipKind CARGO_RACK = capability("cargo_rack");
    public static final ChipKind LAMP = capability("lamp");
    public static final ChipKind SALVAGE = capability("salvage");
    public static final ChipKind SILENT_RUNNING = capability("silent_running");
    public static final ChipKind RAD_SHIELDING = capability("rad_shielding");

    // --- cosmetic (single tier) -------------------------------------------------------------------
    public static final ChipKind CHATTERBOX = cosmetic("chatterbox");
    public static final ChipKind HUSH = cosmetic("hush");
    public static final ChipKind EMOTE_PACK = cosmetic("emote_pack");
    public static final ChipKind SPARKLE_TRAIL = cosmetic("sparkle_trail");

    private ChipKinds() {
    }

    private static ChipKind stat(String name, UpgradeType core) {
        return add(name, ChipCategory.STAT, 3, core, null);
    }

    private static ChipKind capability(String name) {
        return add(name, ChipCategory.CAPABILITY, 1, null, null);
    }

    private static ChipKind cosmetic(String name) {
        return add(name, ChipCategory.COSMETIC, 1, null, null);
    }

    private static ChipKind add(String name, ChipCategory category, int maxTier, UpgradeType core,
            CompanionMode unlocks) {
        ChipKind kind = new ChipKind(Identifier.fromNamespaceAndPath(NAMESPACE, name), category, maxTier,
                Optional.ofNullable(core), Optional.ofNullable(unlocks));
        BY_ID.put(kind.id(), kind);
        return kind;
    }

    /** Every kind, in creative-tab order (stat, capability, cosmetic). */
    public static List<ChipKind> all() {
        return List.copyOf(BY_ID.values());
    }

    public static Optional<ChipKind> byId(Identifier id) {
        return Optional.ofNullable(BY_ID.get(id));
    }

    /** Number of chip items (every tier of every kind). */
    public static int itemCount() {
        int count = 0;
        for (ChipKind kind : BY_ID.values()) {
            count += kind.maxTier();
        }
        return count;
    }
}
