package za.co.neroland.nerocompanion.look;

import net.minecraft.resources.Identifier;

/**
 * Whether a look is unlocked for one player and one companion. Pure: everything it needs comes through
 * {@link Context}, so the server ({@code LookService}) and the tests share one rule table. A look of another type is
 * never unlocked, whatever its rule.
 */
public final class LookUnlocks {

    /** What the rules read. Server: the player, their companion, the config and Core's gates. */
    public interface Context {
        /** The companion's bond level (0-4). */
        int bondLevel();

        /** A vanilla advancement or Core progression gate {@code id} is done / open for the player. */
        boolean advancementDone(Identifier id);

        /** The player has at least one of item {@code id}. */
        boolean hasItem(Identifier id);

        /** The server's {@code season} config key. */
        String season();

        /** Hook gate {@code id} is open for the player. */
        boolean hookOpen(Identifier id);

        /** A shop can sell this look at this price right now (NeroEconomy present). */
        default boolean shopCanSell(Identifier look, long price) {
            return false;
        }

        /** The player already bought this look (a permanent unlock, kept even without NeroEconomy). */
        default boolean shopOwned(Identifier look) {
            return false;
        }
    }

    private LookUnlocks() {
    }

    public static boolean unlocked(Look look, Identifier type, Context context) {
        if (!look.allows(type)) {
            return false;
        }
        UnlockRule rule = look.unlock();
        return switch (rule.kind()) {
            case DEFAULT -> true;
            case BOND -> context.bondLevel() >= rule.level();
            case ADVANCEMENT -> id(rule) != null && context.advancementDone(id(rule));
            case ITEM -> id(rule) != null && context.hasItem(id(rule));
            case HOOK -> id(rule) != null && context.hookOpen(id(rule));
            case SEASON -> rule.arg().equals(context.season());
            case SHOP -> context.shopOwned(look.id()) || (rule.price() > 0L && context.shopCanSell(look.id(),
                    rule.price()));
        };
    }

    private static Identifier id(UnlockRule rule) {
        return Identifier.tryParse(rule.arg());
    }
}
