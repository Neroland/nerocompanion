package za.co.neroland.nerocompanion.client.screen;

/**
 * The Remote radial's hit-testing and mouse rules, with no game classes so they can be tested on the plain JVM.
 * Positions are relative to the ring's centre ({@code dx} right, {@code dy} down), in GUI pixels.
 *
 * <p>Elements: {@link #NONE}, {@link #HUB} ("Come here"), wedges {@code 0..7} clockwise from the top (the
 * {@code CompanionMode} order) and {@link #RECALL} (the pill under the ring).
 */
public final class RadialLayout {

    /** Nothing is selected. */
    public static final int NONE = -2;
    /** The centre disc: "Come here". */
    public static final int HUB = -1;
    /** The Recall pill. */
    public static final int RECALL = 8;
    /** Outer radius of the ring. */
    public static final int OUTER = 40;
    /** Radius of the hub. */
    public static final int INNER = 10;
    /** How far the cursor must move from the centre before the hub can be selected by direction alone. */
    public static final int MOVE_PX = 4;
    /** The description line under the ring, relative to the centre (below the wedge numbers, which end at +50). */
    public static final int LINE_Y = 52;
    /** The Recall pill, relative to the centre: x, y, width, height. */
    public static final int PILL_X = -56;
    public static final int PILL_Y = 64;
    public static final int PILL_W = 112;
    public static final int PILL_H = 14;

    /** What a mouse button does once the element under the cursor is known. */
    public enum Action {
        /** Send the element and close. */
        CONFIRM,
        /** Close without sending anything. */
        CLOSE,
        /** Stay open and wait for a click, a number key or Esc. */
        STAY_OPEN
    }

    /** Space the name plate needs above the centre, and the pill below it. */
    static final int ABOVE = 68;
    static final int BELOW = PILL_Y + PILL_H;
    /** Room kept clear at the bottom of the screen: the hotbar, and the held-item name vanilla draws above it. */
    static final int BOTTOM_CLEAR = 62;

    private RadialLayout() {
    }

    /**
     * The ring's centre y on a screen {@code height} GUI px tall: the middle, raised when needed so the Recall pill
     * stays above the hotbar and the held-item name, but never so high that the name plate leaves the screen. When it
     * is raised the cursor opens below the ring's centre, which is why {@link #pick} selects nothing until the cursor
     * has moved.
     */
    public static int centreY(int height) {
        return Math.max(ABOVE, Math.min(height / 2, height - BOTTOM_CLEAR - BELOW));
    }

    /** Whether the cursor is far enough from the centre to count as having moved off it. */
    public static boolean movedOff(double dx, double dy) {
        return Math.abs(dx) >= MOVE_PX || Math.abs(dy) >= MOVE_PX;
    }

    /**
     * The element under the cursor.
     *
     * @param moved   whether the cursor has left the centre since the radial opened
     * @param click   a deliberate left click: it may choose the hub without the movement rule
     * @param current the element selected before this move ({@link #NONE} if none); beyond the ring the last wedge
     *                stays selected so a flick works
     */
    public static int pick(double dx, double dy, boolean moved, boolean click, int current) {
        if (!moved && !click) {
            // Until the cursor moves, nothing is selected: the cursor may open off-centre (see centreY), and letting
            // go of the Remote without aiming must never send anything.
            return NONE;
        }
        if (dx >= PILL_X && dy >= PILL_Y && dx < PILL_X + PILL_W && dy < PILL_Y + PILL_H) {
            return RECALL;
        }
        double dist = Math.sqrt(dx * dx + dy * dy);
        if (dist < INNER) {
            return moved || click ? HUB : NONE;
        }
        if (dist > OUTER + 30 && current < 0) {
            return NONE;
        }
        double deg = Math.toDegrees(Math.atan2(dx, -dy));
        if (deg < 0) {
            deg += 360.0D;
        }
        return (int) Math.floor((deg + 22.5D) / 45.0D) % 8;
    }

    /** A left click: confirms what is under the cursor, and a click on empty space closes. */
    public static Action onClick(int picked) {
        return picked == NONE ? Action.CLOSE : Action.CONFIRM;
    }

    /**
     * Releasing the use key (the right button that held the Remote). With something selected it confirms it, so
     * hold, aim and let go still works. With nothing selected the radial stays open: letting go of the Remote to
     * click a wedge must not close the menu, and it never sends "Come here".
     */
    public static Action onUseRelease(int picked) {
        return picked == NONE ? Action.STAY_OPEN : Action.CONFIRM;
    }
}
