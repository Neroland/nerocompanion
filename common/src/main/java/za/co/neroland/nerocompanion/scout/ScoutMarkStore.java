package za.co.neroland.nerocompanion.scout;

import java.util.List;

import za.co.neroland.nerocompanion.network.payload.Payloads;

/**
 * The client's current scout marks (pure, no game state). Each {@code ScoutMarks} payload replaces the previous
 * batch and lives for its TTL; a batch that is not refreshed in time disappears. Never more than
 * {@link Payloads.ScoutMarks#MAX_MARKS} marks, even if a malformed batch claims more.
 */
public final class ScoutMarkStore {

    /** The longest TTL a batch may claim, so a bad packet cannot pin marks on screen. */
    public static final int MAX_TTL_TICKS = 100;

    private List<Payloads.Mark> marks = List.of();
    private long expiresAt = Long.MIN_VALUE;

    /** A new batch arrived at client tick {@code now}. */
    public void receive(List<Payloads.Mark> batch, int ttlTicks, long now) {
        int ttl = Math.max(0, Math.min(MAX_TTL_TICKS, ttlTicks));
        List<Payloads.Mark> copy = batch.size() > Payloads.ScoutMarks.MAX_MARKS
                ? batch.subList(0, Payloads.ScoutMarks.MAX_MARKS) : batch;
        this.marks = List.copyOf(copy);
        this.expiresAt = now + ttl;
    }

    /** The marks still alive at client tick {@code now} (empty once the batch expired). */
    public List<Payloads.Mark> active(long now) {
        if (now >= this.expiresAt) {
            this.marks = List.of();
            return this.marks;
        }
        return this.marks;
    }

    /** Forget everything (world change, disconnect). */
    public void clear() {
        this.marks = List.of();
        this.expiresAt = Long.MIN_VALUE;
    }
}
