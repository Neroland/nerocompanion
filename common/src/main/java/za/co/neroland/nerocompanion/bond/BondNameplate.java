package za.co.neroland.nerocompanion.bond;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

/**
 * The bond nameplate ({@code 010} section 4.5): level 3 puts a white heart before the name; level 4 is the glow -
 * name in {@code TEAL_BRIGHT}, underlined, with a {@code BOND_PINK} heart. Levels 0-2 return the name unchanged.
 * Decoration only: it never adds a way to see a name the plate would not already show ({@code NAME_HIDDEN} and the
 * vanilla visibility rules still decide). Client-side use; wired by the renderer / entity (see notes).
 */
public final class BondNameplate {

    public static final int TEAL_BRIGHT = 0xA8F0EA;
    public static final int BOND_PINK = 0xFF8CCF;
    public static final int EYE_WHITE = 0xE8FCFA;
    /** U+2764 heavy black heart, present in the vanilla font. */
    public static final String HEART = "❤";

    private BondNameplate() {
    }

    public static Component decorate(Component name, int bondLevel) {
        if (bondLevel < 3) {
            return name;
        }
        boolean glow = bondLevel >= BondLevels.MAX_LEVEL;
        MutableComponent heart = Component.literal(HEART + " ")
                .withStyle(Style.EMPTY.withColor(glow ? BOND_PINK : EYE_WHITE));
        MutableComponent body = name.copy();
        if (glow) {
            body = body.withStyle(Style.EMPTY.withColor(TEAL_BRIGHT).withUnderlined(true));
        }
        return heart.append(body);
    }
}
