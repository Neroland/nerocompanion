package za.co.neroland.nerocompanion.network;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Per player, per payload type: at most {@value #PER_SECOND} payloads per second; the excess is dropped silently.
 * NeroLink actions share this limiter (one per-player {@value #LINK_BUCKET} bucket, {@value #LINK_PER_MINUTE} a minute).
 * Game-time based (20 ticks = one window). In memory only, never saved; pruned as it grows.
 */
public final class RateLimiter {

    public static final int PER_SECOND = 10;
    private static final int WINDOW_TICKS = 20;

    private final Map<String, long[]> windows = new HashMap<>();

    /** NeroLink actions: at most this many per player per minute (one bucket for every link op). */
    public static final int LINK_PER_MINUTE = 10;
    /** The bucket type NeroLink actions count against in this limiter. */
    public static final String LINK_BUCKET = "link";
    private static final int MINUTE_TICKS = 1_200;

    /** Records one payload; false when this player already sent {@value #PER_SECOND} of this type this window. */
    public synchronized boolean allow(UUID player, String type, long gameTime) {
        return this.allow(player, type, gameTime, PER_SECOND, WINDOW_TICKS);
    }

    /** One NeroLink action for {@code player}: at most {@value #LINK_PER_MINUTE} per minute of game time. */
    public synchronized boolean allowLink(UUID player, long gameTime) {
        return this.allow(player, LINK_BUCKET, gameTime, LINK_PER_MINUTE, MINUTE_TICKS);
    }

    private boolean allow(UUID player, String type, long gameTime, int limit, int windowTicks) {
        if (this.windows.size() > 4096) {
            this.windows.values().removeIf(w -> gameTime - w[0] >= MINUTE_TICKS);
        }
        long[] window = this.windows.computeIfAbsent(player + "|" + type, k -> new long[] {gameTime, 0});
        if (gameTime - window[0] >= windowTicks || gameTime < window[0]) {
            window[0] = gameTime;
            window[1] = 0;
        }
        if (window[1] >= limit) {
            return false;
        }
        window[1]++;
        return true;
    }

    public synchronized void clear() {
        this.windows.clear();
    }
}
