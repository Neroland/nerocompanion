package za.co.neroland.nerocompanion.mining;

import java.util.EnumMap;
import java.util.Map;

/**
 * Per-bot "don't try that again yet" timers for the unload stages: a bound container that refused (lock, claim),
 * was full, or could not be reached is skipped for {@link #TICKS} so the routing falls through to the next stage
 * instead of shuttling back to it. Pure.
 */
public final class Backoff {

    /** Thirty seconds. */
    public static final long TICKS = 600L;

    private final Map<DropRouter.Stage, Long> until = new EnumMap<>(DropRouter.Stage.class);

    public void fail(DropRouter.Stage stage, long now) {
        this.until.put(stage, now + TICKS);
    }

    public boolean blocked(DropRouter.Stage stage, long now) {
        Long end = this.until.get(stage);
        if (end == null) {
            return false;
        }
        if (now >= end) {
            this.until.remove(stage);
            return false;
        }
        return true;
    }

    public void clear(DropRouter.Stage stage) {
        this.until.remove(stage);
    }
}
