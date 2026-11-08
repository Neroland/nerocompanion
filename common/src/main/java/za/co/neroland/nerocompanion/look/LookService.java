package za.co.neroland.nerocompanion.look;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import za.co.neroland.nerolandcore.progression.ProgressionGates;

import za.co.neroland.nerocompanion.config.NeroCompanionConfig;
import za.co.neroland.nerocompanion.entity.CompanionEntity;
import za.co.neroland.nerocompanion.network.payload.Payloads;
import za.co.neroland.nerocompanion.platform.Services;
import za.co.neroland.nerocompanion.progression.QuestHooks;

/**
 * The server side of looks: which skins and hats one player has unlocked for one of their companions (sent only to
 * that player, in the companion snapshot), the {@code ApplySkin} validation (owner, look exists, type allowed,
 * unlocked; an {@code item} rule consumes one item), and the public catalogue broadcast every client needs to draw
 * other players' companions. No look is stored per player: unlocks are derived each time from the bond, the
 * player's advancements and Core gates, the inventory and the {@code season} config key.
 */
public final class LookService {

    private static int broadcastGeneration = -1;

    private LookService() {
    }

    /** The unlock context of {@code player} with {@code companion}. */
    public static LookUnlocks.Context context(ServerPlayer player, CompanionEntity companion) {
        return new LookUnlocks.Context() {
            @Override
            public int bondLevel() {
                return companion.syncedBondLevel();
            }

            @Override
            public boolean advancementDone(Identifier id) {
                MinecraftServer server = player.level().getServer();
                if (server != null) {
                    AdvancementHolder holder = server.getAdvancements().get(id);
                    if (holder != null && player.getAdvancements().getOrStartProgress(holder).isDone()) {
                        return true;
                    }
                }
                // The shipped looks name Core progression gates (nerocompanion:first_deploy, pet_adopted).
                return ProgressionGates.isOpen(player, id);
            }

            @Override
            public boolean hasItem(Identifier id) {
                return count(player.getInventory(), id) > 0;
            }

            @Override
            public String season() {
                return NeroCompanionConfig.season();
            }

            @Override
            public boolean shopCanSell(Identifier look, long price) {
                return za.co.neroland.nerocompanion.compat.neroeconomy.NeroEconomyCompat.shopOpen();
            }

            @Override
            public boolean shopOwned(Identifier look) {
                return ProgressionGates.isOpen(player,
                        za.co.neroland.nerocompanion.compat.neroeconomy.NeroEconomyCompat.purchaseGate(look));
            }

            @Override
            public boolean hookOpen(Identifier id) {
                String prefix = QuestHooks.SKIN_HOOK_PREFIX;
                if (id.getNamespace().equals(za.co.neroland.nerocompanion.NeroCompanionCommon.MOD_ID)
                        && id.getPath().startsWith(prefix)) {
                    return QuestHooks.isSkinUnlocked(player, id.getPath().substring(prefix.length()));
                }
                return ProgressionGates.isOpen(player, id);
            }
        };
    }

    /** Ids of the looks of {@code kind} unlocked for this player and companion (only looks its type may wear). */
    public static List<String> unlocked(ServerPlayer player, CompanionEntity companion, LookKind kind) {
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return List.of();
        }
        LookUnlocks.Context context = context(player, companion);
        List<String> out = new ArrayList<>();
        for (Look look : (kind == LookKind.HAT ? Looks.hats(server) : Looks.skins(server)).values()) {
            if (LookUnlocks.unlocked(look, companion.typeId(), context)) {
                out.add(look.id().toString());
            }
        }
        return out;
    }

    /**
     * {@code ApplySkin}: {@code player} already owns {@code companion} (checked by the caller). An empty id is the
     * standard skin / no hat. Rejections never change anything.
     */
    public static Payloads.Feedback apply(ServerPlayer player, CompanionEntity companion, boolean isHat,
            Optional<Identifier> requested) {
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return Payloads.Feedback.deny("gui.nerocompanion.reason.not_live");
        }
        LookKind kind = isHat ? LookKind.HAT : LookKind.SKIN;
        Optional<Identifier> current = isHat ? companion.hat() : companion.skin();
        Optional<Identifier> wanted = requested;
        if (!isHat && wanted.isPresent() && wanted.get().equals(Looks.defaultSkin(companion.typeId()))) {
            wanted = Optional.empty();
        }
        if (wanted.equals(current)) {
            return Payloads.Feedback.deny("gui.nerocompanion.reason.already_applied");
        }
        if (wanted.isEmpty()) {
            companion.setLook(isHat, Optional.empty());
            return isHat ? Payloads.Feedback.ok("gui.nerocompanion.studio.hat_removed")
                    : Payloads.Feedback.ok("gui.nerocompanion.studio.applied", "@gui.nerocompanion.studio.default");
        }
        Optional<Look> look = Looks.get(server, kind, wanted.get());
        Payloads.Feedback denial = check(look.orElse(null), companion.typeId(), context(player, companion));
        if (denial != null) {
            return denial;
        }
        Look chosen = look.get();
        if (chosen.unlock().kind() == UnlockRule.Kind.SHOP && !player.getAbilities().instabuild) {
            Payloads.Feedback refused = buy(player, chosen);
            if (refused != null) {
                return refused;
            }
        }
        if (chosen.unlock().kind() == UnlockRule.Kind.ITEM && !player.getAbilities().instabuild) {
            Identifier item = Identifier.tryParse(chosen.unlock().arg());
            if (item == null || !consumeOne(player.getInventory(), item)) {
                return Payloads.Feedback.deny("gui.nerocompanion.reason.need_item", "@" + itemKey(item));
            }
        }
        companion.setLook(isHat, Optional.of(chosen.id()));
        return Payloads.Feedback.ok("gui.nerocompanion.studio.applied", "@" + chosen.nameKey());
    }

    /**
     * Why {@code look} cannot go on a companion of {@code type} for this context, or null when it can. Pure (the
     * tests drive it with a fake context).
     */
    public static Payloads.Feedback check(Look look, Identifier type, LookUnlocks.Context context) {
        if (look == null) {
            return Payloads.Feedback.deny("gui.nerocompanion.reason.no_such_look");
        }
        if (!look.allows(type)) {
            return Payloads.Feedback.deny("gui.nerocompanion.reason.look_type",
                    "@entity." + type.getNamespace() + "." + type.getPath());
        }
        if (!LookUnlocks.unlocked(look, type, context)) {
            UnlockRule rule = look.unlock();
            if (rule.kind() == UnlockRule.Kind.ITEM) {
                return Payloads.Feedback.deny("gui.nerocompanion.reason.need_item",
                        "@" + itemKey(Identifier.tryParse(rule.arg())));
            }
            return Payloads.Feedback.deny("gui.nerocompanion.reason.look_locked");
        }
        return null;
    }

    /**
     * A {@code shop} look not yet owned: charge its price through NeroEconomy (façade), then record the purchase as a
     * Core gate - the permanent unlock. Null when the player owns it (or has just bought it), else the refusal.
     */
    private static Payloads.Feedback buy(ServerPlayer player, Look look) {
        Identifier gate = za.co.neroland.nerocompanion.compat.neroeconomy.NeroEconomyCompat.purchaseGate(look.id());
        if (ProgressionGates.isOpen(player, gate)) {
            return null;
        }
        za.co.neroland.nerocompanion.economy.EconomyOutcome outcome =
                za.co.neroland.nerocompanion.compat.neroeconomy.NeroEconomyCompat.buyLook(player, look.id(),
                        look.unlock().price());
        if (!outcome.ok()) {
            return Payloads.Feedback.deny(outcome.translationKey());
        }
        ProgressionGates.open(player, gate);
        return null;
    }

    private static String itemKey(Identifier item) {
        return item == null ? "item.minecraft.air" : "item." + item.getNamespace() + "." + item.getPath();
    }

    static int count(Inventory inventory, Identifier id) {
        Item item = BuiltInRegistries.ITEM.getValue(id);
        int n = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty() && stack.is(item)) {
                n += stack.getCount();
            }
        }
        return n;
    }

    private static boolean consumeOne(Inventory inventory, Identifier id) {
        Item item = BuiltInRegistries.ITEM.getValue(id);
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty() && stack.is(item)) {
                stack.shrink(1);
                inventory.setChanged();
                return true;
            }
        }
        return false;
    }

    // --- the public catalogue -------------------------------------------------------------------------

    /** Every look, for one client (on join). */
    public static void sendCatalogue(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        if (server != null) {
            Services.NETWORK.sendToPlayer(player, new Payloads.LookCatalogue(Looks.all(server)));
        }
    }

    /** Server tick: after a reload changed the catalogue, every online player gets the new one. */
    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % 20 != 0) {
            return;
        }
        int generation = Looks.generation(server);
        if (generation == broadcastGeneration) {
            return;
        }
        boolean first = broadcastGeneration < 0;
        broadcastGeneration = generation;
        if (first) {
            return; // the first load happens before anyone joins or on join itself; join sends the catalogue
        }
        Payloads.LookCatalogue payload = new Payloads.LookCatalogue(Looks.all(server));
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Services.NETWORK.sendToPlayer(player, payload);
        }
    }

    public static void serverStopped() {
        broadcastGeneration = -1;
        Looks.serverStopped();
    }
}
