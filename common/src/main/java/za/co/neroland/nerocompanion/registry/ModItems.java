package za.co.neroland.nerocompanion.registry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import za.co.neroland.nerolandcore.registry.RegistrationProvider;
import za.co.neroland.nerolandcore.registry.RegistrationProvider.RegistryEntry;

import za.co.neroland.nerocompanion.NeroCompanionCommon;
import za.co.neroland.nerocompanion.chip.ChipItem;
import za.co.neroland.nerocompanion.chip.ChipKind;
import za.co.neroland.nerocompanion.chip.ChipKinds;
import za.co.neroland.nerocompanion.item.CapsuleItem;
import za.co.neroland.nerocompanion.item.FetchBallItem;
import za.co.neroland.nerocompanion.item.RemoteItem;
import za.co.neroland.nerocompanion.item.ScrapEggItem;
import za.co.neroland.nerocompanion.item.CompanionSpawnEggItem;
import za.co.neroland.nerocompanion.platform.Services;

/** NeroCompanion's items. */
public final class ModItems {

    public static final RegistrationProvider<Item> ITEMS =
            Services.REGISTRATION.provider(Registries.ITEM, NeroCompanionCommon.MOD_ID);

    /** The Capsule: holds one recalled companion; empty, it recalls one of your own. */
    public static final RegistryEntry<Item> CAPSULE = ITEMS.register("capsule",
            key -> new CapsuleItem(new Item.Properties().setId(key).stacksTo(1)));

    /** The Scrap Egg: incubated on a Charging Pad or in a Bay, it hatches a Scrapling. */
    public static final RegistryEntry<Item> SCRAP_EGG = ITEMS.register("scrap_egg",
            key -> new ScrapEggItem(new Item.Properties().setId(key).stacksTo(16)));

    /** The Remote: tap your companion for its screen, hold for the job radial, sneak-use to recall. */
    public static final RegistryEntry<Item> REMOTE = ITEMS.register("remote",
            key -> new RemoteItem(new Item.Properties().setId(key).stacksTo(1)));

    /** The Fetch Ball: a throwable toy for pet companions. */
    public static final RegistryEntry<Item> FETCH_BALL = ITEMS.register("fetch_ball",
            key -> new FetchBallItem(new Item.Properties().setId(key).stacksTo(16)));

    /** The cheap base every chip is crafted from. */
    public static final RegistryEntry<Item> BLANK_CHIP = ITEMS.register("blank_chip",
            key -> new Item(new Item.Properties().setId(key)));

    // --- look unlock items (010 section 6): consumed in the Skin Studio by an item-rule skin or hat --------------
    /** Unlocks the Mining Bot's Hazard skin: Blank Chip + yellow dye + black dye. */
    public static final RegistryEntry<Item> HAZARD_DECAL = ITEMS.register("hazard_decal",
            key -> new Item(new Item.Properties().setId(key)));
    /** Unlocks the Scrap Bucket hat: dropped by a Scrap Egg when it hatches. */
    public static final RegistryEntry<Item> SCRAP_SHELL = ITEMS.register("scrap_shell",
            key -> new Item(new Item.Properties().setId(key)));
    /** Unlocks the Hard Hat (crafted). */
    public static final RegistryEntry<Item> HAT_HARD_HAT = ITEMS.register("hat_hard_hat",
            key -> new Item(new Item.Properties().setId(key).stacksTo(16)));
    /** Unlocks the Sun Visor (crafted). */
    public static final RegistryEntry<Item> HAT_SUN_VISOR = ITEMS.register("hat_sun_visor",
            key -> new Item(new Item.Properties().setId(key).stacksTo(16)));
    /** Unlocks the Headlamp hat (crafted; cosmetic only, it gives no light). */
    public static final RegistryEntry<Item> HAT_HEADLAMP = ITEMS.register("hat_headlamp",
            key -> new Item(new Item.Properties().setId(key).stacksTo(16)));

    /** The look unlock items, in creative-tab order. */
    public static List<RegistryEntry<Item>> lookItems() {
        return List.of(HAZARD_DECAL, SCRAP_SHELL, HAT_HARD_HAT, HAT_SUN_VISOR, HAT_HEADLAMP);
    }

    // --- the chargers' block items -----------------------------------------------------------------
    public static final RegistryEntry<Item> CHARGING_PAD = blockItem("charging_pad", ModBlocks.CHARGING_PAD);
    public static final RegistryEntry<Item> COMPANION_BAY = blockItem("companion_bay", ModBlocks.COMPANION_BAY);
    public static final RegistryEntry<Item> HAND_CRANK_CHARGER = blockItem("hand_crank_charger",
            ModBlocks.HAND_CRANK_CHARGER);

    // --- creative-only spawn eggs, one per companion type -------------------------------------------
    // A plain Item, not vanilla SpawnEggItem (whose constructor changes three times across the lines). Never in a
    // recipe and never spawned naturally: companions are crafted (a Capsule blueprint) or hatched.
    private static final Map<String, RegistryEntry<Item>> SPAWN_EGGS = new java.util.LinkedHashMap<>();

    static {
        for (net.minecraft.resources.Identifier type : ModEntities.COMPANION_TYPES) {
            SPAWN_EGGS.put(type.getPath(), ITEMS.register(type.getPath() + "_spawn_egg",
                    key -> new CompanionSpawnEggItem(new Item.Properties().setId(key), type)));
        }
    }

    /** The spawn eggs in creative-tab order. */
    public static List<RegistryEntry<Item>> spawnEggs() {
        return List.copyOf(SPAWN_EGGS.values());
    }

    // --- chips: one item per kind and tier ---------------------------------------------------------
    private static final Map<String, RegistryEntry<Item>> CHIPS = new HashMap<>();
    private static final List<RegistryEntry<Item>> CHIP_ORDER = new ArrayList<>();

    static {
        for (ChipKind kind : ChipKinds.all()) {
            for (int tier = 1; tier <= kind.maxTier(); tier++) {
                int t = tier;
                RegistryEntry<Item> entry = ITEMS.register(kind.itemName(tier),
                        key -> new ChipItem(new Item.Properties().setId(key).stacksTo(16), kind, t));
                CHIPS.put(kind.name() + "#" + tier, entry);
                CHIP_ORDER.add(entry);
            }
        }
    }

    private static RegistryEntry<Item> blockItem(String name, RegistryEntry<? extends Block> block) {
        return ITEMS.register(name, key -> new BlockItem(block.get(),
                new Item.Properties().setId(key).useBlockDescriptionPrefix()));
    }

    /** The chip item for {@code kind} at {@code tier}, or null. */
    public static Item chip(ChipKind kind, int tier) {
        RegistryEntry<Item> entry = CHIPS.get(kind.name() + "#" + tier);
        return entry == null ? null : entry.get();
    }

    /** Every chip item, in creative-tab order. */
    public static List<RegistryEntry<Item>> chipsInOrder() {
        return List.copyOf(CHIP_ORDER);
    }

    private ModItems() {
    }

    /** Classload-forcing no-op. */
    public static void init() {
    }
}
