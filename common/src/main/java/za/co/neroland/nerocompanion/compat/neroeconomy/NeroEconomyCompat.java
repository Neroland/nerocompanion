package za.co.neroland.nerocompanion.compat.neroeconomy;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import za.co.neroland.nerolandcore.economy.CurrencyApi;

import za.co.neroland.nerocompanion.NeroCompanionCommon;
import za.co.neroland.nerocompanion.economy.CompanionEconomy;
import za.co.neroland.nerocompanion.economy.EconomyOutcome;
import za.co.neroland.nerocompanion.economy.RequestKeys;

/**
 * NeroEconomy façade (decision 020): the {@code shop} skin rule and the reclaim cost. The NeroEconomy calls
 * themselves go through the existing {@code economy/} bridge ({@link CompanionEconomy}, the only code that imports
 * NeroEconomy), so this class never names a NeroEconomy type.
 *
 * <ul>
 *   <li><b>Shop looks</b> are offered while NeroEconomy is hooked ({@link #shopOpen}). Apply charges the price
 *       ({@link #buyLook}); a successful charge opens the player-scope Core gate {@link #purchaseGate}, which is the
 *       permanent unlock (Core stores and erases it), so an owned look stays unlocked if NeroEconomy is later
 *       removed.</li>
 *   <li><b>Reclaim cost</b> ({@link #reclaimCost}) is paid through Core's {@code CurrencyApi}, whose real provider is
 *       NeroEconomy; with no real provider a reclaim is free.</li>
 * </ul>
 *
 * <p>Privacy (POPIA/GDPR): request keys name the purchase, never the player ({@link RequestKeys}); nothing is
 * logged.
 */
public final class NeroEconomyCompat {

    public static final String MOD_ID = "neroeconomy";
    /** Path prefix of the purchase gates. */
    public static final String PURCHASE_PREFIX = "purchase/";

    private NeroEconomyCompat() {
    }

    /** Whether shop looks can be bought right now (NeroEconomy installed, new enough and claimed). */
    public static boolean shopOpen() {
        return CompanionEconomy.isActive();
    }

    /** The Core gate that records a bought look: {@code nerocompanion:purchase/<look ns>/<look path>}. */
    public static Identifier purchaseGate(Identifier look) {
        return Identifier.fromNamespaceAndPath(NeroCompanionCommon.MOD_ID,
                PURCHASE_PREFIX + look.getNamespace() + "/" + look.getPath());
    }

    /**
     * The request key for one purchase of {@code look}: {@code look/<id>#<nonce>}, or {@code look#<nonce>} when the id
     * would make it too long. Never a player identifier.
     */
    public static String requestKey(Identifier look, long nonce) {
        String tail = "#" + Long.toString(nonce & 0xFFFFFFFFL, 36);
        String key = "look/" + look + tail;
        return RequestKeys.isValid(key) ? key : "look" + tail;
    }

    /** Charges {@code price} credits for {@code look}. Unlock only when the outcome is ok. */
    public static EconomyOutcome buyLook(ServerPlayer player, Identifier look, long price) {
        return CompanionEconomy.chargeCosmetic(player, price, requestKey(look, player.getRandom().nextLong()));
    }

    /** The reclaim cost in credits: {@code configured}, or 0 when it is unset or no real currency provider exists. */
    public static long reclaimCost(int configured) {
        return configured > 0 && CurrencyApi.hasRealProvider() ? configured : 0L;
    }
}
