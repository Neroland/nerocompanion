package za.co.neroland.nerocompanion.incubation;

/**
 * The empty-hand check on an incubator, as a pure rule (unit-tested): the first sneak-use shows the egg's progress,
 * a second one on the same incubator within {@value #CONFIRM_TICKS} ticks takes the egg back out.
 */
public final class EggPrompts {

    public static final int CONFIRM_TICKS = 60;

    private EggPrompts() {
    }

    /** Whether a use at {@code at}/{@code now} confirms the previous one at {@code prevAt}/{@code prevTime}. */
    public static boolean confirms(Object prevAt, long prevTime, Object at, long now) {
        return prevAt != null && prevAt.equals(at) && now >= prevTime && now - prevTime <= CONFIRM_TICKS;
    }
}
