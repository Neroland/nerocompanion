package za.co.neroland.nerocompanion.look;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * How a skin or hat is unlocked ({@code 010} section 6.1): {@code default}, {@code bond} (level 1-4),
 * {@code advancement} (id), {@code item} (id, one consumed on Apply), {@code shop} (price, NeroEconomy facade),
 * {@code season} (equal to the server's {@code season} config key) or {@code hook} (a Core gate a quest opens).
 * {@code arg} carries the rule's one argument as text: the level, the id, the season or the price.
 */
public record UnlockRule(Kind kind, String arg) {

    public enum Kind {
        DEFAULT, BOND, ADVANCEMENT, ITEM, SHOP, SEASON, HOOK;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Optional<Kind> byId(String id) {
            for (Kind kind : values()) {
                if (kind.id().equals(id)) {
                    return Optional.of(kind);
                }
            }
            return Optional.empty();
        }
    }

    /** The seasons the {@code season} config key accepts ({@code none} matches no seasonal look). */
    public static final Set<String> SEASONS = Set.of("none", "spring", "summer", "autumn", "winter", "festive");

    public static final UnlockRule ALWAYS = new UnlockRule(Kind.DEFAULT, "");

    public UnlockRule {
        arg = arg == null ? "" : arg;
    }

    /** The bond level a {@code bond} rule needs (0 for other rules). */
    public int level() {
        if (this.kind != Kind.BOND) {
            return 0;
        }
        try {
            return Integer.parseInt(this.arg);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** The price of a {@code shop} rule (0 for other rules). */
    public long price() {
        if (this.kind != Kind.SHOP) {
            return 0L;
        }
        try {
            return Long.parseLong(this.arg);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
