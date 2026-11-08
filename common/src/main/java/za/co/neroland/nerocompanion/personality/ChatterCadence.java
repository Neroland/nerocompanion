package za.co.neroland.nerocompanion.personality;

/**
 * The chatter throttle maths, pure so it is tested without a game. A companion has one shared cooldown (so two
 * events never stack bubbles) plus each event's own base cooldown scaled by the {@link ChatterVolume}.
 */
public final class ChatterCadence {

    /** Minimum gap between any two lines from one companion at normal volume (ticks). */
    public static final int GLOBAL_COOLDOWN = 100;
    /** Chance per 20-tick evaluation that an idle companion says something, at normal volume. */
    public static final double IDLE_CHANCE = 1.0D / 45.0D;

    private ChatterCadence() {
    }

    /** Ticks this event must wait after the previous line of the same event. */
    public static int eventCooldown(ChatterEvent event, ChatterVolume volume) {
        return volume.scaleCooldown(event.baseCooldown());
    }

    /** Ticks any line must wait after the previous line of any event. */
    public static int globalCooldown(ChatterVolume volume) {
        return volume.scaleCooldown(GLOBAL_COOLDOWN);
    }

    /**
     * Whether a line for {@code event} may be said at {@code now}. {@code lastAny} / {@code lastEvent} are the game
     * times of the previous line overall and of this event, or {@link Long#MIN_VALUE} if never. Events that
     * {@link ChatterEvent#skipsGap() skip the gap} (bond level-up, pets, danger) ignore the global cooldown but
     * still respect their own and silence. A stamp in the future (another world's clock) counts as elapsed.
     */
    public static boolean ready(ChatterEvent event, ChatterVolume volume, long now, long lastAny, long lastEvent) {
        if (volume.silent()) {
            return false;
        }
        if (!event.skipsGap() && !elapsed(now, lastAny, globalCooldown(volume))) {
            return false;
        }
        return elapsed(now, lastEvent, eventCooldown(event, volume));
    }

    /** The idle-line chance per evaluation at this volume. */
    public static double idleChance(ChatterVolume volume) {
        return volume.scaleChance(IDLE_CHANCE);
    }

    static boolean elapsed(long now, long last, int cooldown) {
        if (last == Long.MIN_VALUE) {
            return true;
        }
        if (cooldown == Integer.MAX_VALUE) {
            return false;
        }
        return now < last || now - last >= cooldown;
    }
}
