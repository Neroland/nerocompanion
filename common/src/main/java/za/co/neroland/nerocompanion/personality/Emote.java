package za.co.neroland.nerocompanion.personality;

import java.util.Locale;

/**
 * The emote icon set ({@code 010} section 5.1). The ordinal is the cell in {@code textures/gui/emotes.png}
 * (8 columns x 4 rows of 16x16, index = row x 8 + column), so the order is fixed.
 */
public enum Emote {
    HEART, ZZ, QUESTION, EXCLAIM, ORE, DANGER, BOLT, WRENCH,
    NOTE, SWEAT, STAR, PUFF, SPIRAL, EYE, SPARKLE, BOX,
    PICK, SHIELD, TARGET, LEAF, DOTS, TICK, CROSS, HOME,
    BALL, EGG, HAT, SUN, MOON, BLUSH, HOURGLASS, WAVE;

    public static final int COLUMNS = 8;
    public static final int ROWS = 4;

    public String id() {
        return this.name().toLowerCase(Locale.ROOT);
    }

    public int column() {
        return this.ordinal() % COLUMNS;
    }

    public int row() {
        return this.ordinal() / COLUMNS;
    }
}
