package za.co.neroland.nerocompanion.economy;

import java.util.Optional;
import java.util.OptionalLong;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import za.co.neroland.nerocompanion.NeroCompanionCommon;
import za.co.neroland.nerocompanion.platform.Services;

/**
 * Cosmetic purchases and price display through NeroEconomy, which is optional.
 *
 * <ul>
 *   <li>{@link #chargeCosmetic}: unlock the skin only when {@link EconomyOutcome#ok()}; otherwise show
 *       {@link EconomyOutcome#message()}. The confirm step restates the price and what the player gets.
 *       The purchase id is the request key, so a double click can never charge twice.</li>
 *   <li>{@link #askPrice} / {@link #bidPrice}: what a Merchant House sells / pays for one unmodified unit now,
 *       for a companion that says "this sells for about 12 credits". Empty when it does not, or when
 *       NeroEconomy is absent.</li>
 *   <li>{@link #remainingToday}: how much more of an item this player may trade with the Houses today, about
 *       that player only.</li>
 * </ul>
 *
 * <p><b>Carry bots that haul to market</b> need no API: the Auto-Trader is a vanilla container (slots 0-8
 * take tradeable stacks from any face, 9-17 give up what a buy rule bought). A bot owned by a creative-mode
 * or spectator player must not feed a trader, and a bot must never load chunks to keep one running.
 *
 * <p>Server thread only. Privacy (POPIA/GDPR): purchases are never logged, and no other player's data is read.
 */
public final class CompanionEconomy {

    /** Reason code for a cosmetic bought with credits (player to the Reserve). */
    public static final String REASON_COSMETIC = "cosmetic_purchase";

    /**
     * What a player may still trade with the Merchant Houses today.
     *
     * @param key  the commodity key that covers the item
     * @param buy  units the player may still buy today
     * @param sell units the player may still sell today
     */
    public record DailyAllowance(String key, long buy, long sell) {
    }

    /** Whether NeroEconomy is installed, new enough and claimed. */
    private static volatile boolean active;

    private CompanionEconomy() {
    }

    /**
     * Hooks NeroEconomy when it is installed. Call once from common init (claims close when the first server
     * starts). With NeroEconomy absent, or older than API version 1, this does nothing and every call below
     * returns {@link EconomyOutcome.Status#ABSENT}.
     */
    public static void init() {
        if (active || !Services.PLATFORM.isModLoaded("neroeconomy")) return;
        try {
            int api = NeroEconomyBridge.apiVersion();
            if (api < 1) {
                NeroCompanionCommon.LOGGER.info("[NeroCompanion] NeroEconomy is too old (API version {}); credit purchases and price display stay off.", api);
                return;
            }
            NeroEconomyBridge.claim();
            active = true;
            NeroCompanionCommon.LOGGER.info("[NeroCompanion] NeroEconomy found; credit purchases and price display are available.");
        } catch (RuntimeException | LinkageError failure) {
            NeroCompanionCommon.LOGGER.warn("[NeroCompanion] Could not hook NeroEconomy ({}); credit purchases and price display stay off.",
                    failure.getClass().getSimpleName());
        }
    }

    /** Whether NeroEconomy is hooked, so paid features can be offered. */
    public static boolean isActive() {
        return active;
    }

    /**
     * Charges for a cosmetic into NeroEconomy's Reserve.
     *
     * @param player     the player buying
     * @param price      the price in whole credits, more than 0
     * @param purchaseId NeroCompanion's own purchase id, used as the request key (required, see
     *                   {@link RequestKeys}; never a player identifier)
     */
    public static EconomyOutcome chargeCosmetic(ServerPlayer player, long price, String purchaseId) {
        if (!active) return EconomyOutcome.absentOutcome();
        if (player == null || price <= 0L || !RequestKeys.isValid(purchaseId)) return EconomyOutcome.invalid();
        return NeroEconomyBridge.charge(player.level().getServer(), player.getUUID(), price, REASON_COSMETIC, purchaseId);
    }

    /** What a Merchant House sells one unit of {@code itemId} for now; empty when it does not or NeroEconomy is absent. */
    public static OptionalLong askPrice(MinecraftServer server, String itemId) {
        if (!active || server == null || itemId == null) return OptionalLong.empty();
        return NeroEconomyBridge.ask(server, itemId);
    }

    /** What a Merchant House pays for one unit of {@code itemId} now; empty when it does not or NeroEconomy is absent. */
    public static OptionalLong bidPrice(MinecraftServer server, String itemId) {
        if (!active || server == null || itemId == null) return OptionalLong.empty();
        return NeroEconomyBridge.bid(server, itemId);
    }

    /**
     * How much more of {@code itemId} this player may trade with the Merchant Houses today; empty when the item
     * is no commodity or NeroEconomy is absent.
     */
    public static Optional<DailyAllowance> remainingToday(ServerPlayer player, String itemId) {
        if (!active || player == null || itemId == null) return Optional.empty();
        return NeroEconomyBridge.remainingToday(player.level().getServer(), player.getUUID(), itemId);
    }
}
