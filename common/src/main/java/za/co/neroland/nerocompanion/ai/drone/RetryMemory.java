package za.co.neroland.nerocompanion.ai.drone;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Items a drone failed to reach (pure): after a failed attempt an item is skipped for {@value #BACKOFF_TICKS} ticks,
 * so carry-lite does not hover at an unreachable item forever. At most {@value #MAX_ENTRIES} entries; expired entries
 * are pruned, and when full the entry that expires first is dropped.
 */
public final class RetryMemory {

    public static final int BACKOFF_TICKS = 600;
    public static final int MAX_ENTRIES = 32;

    private final Map<UUID, Long> until = new HashMap<>();

    public void failed(UUID item, long now) {
        this.prune(now);
        if (this.until.size() >= MAX_ENTRIES && !this.until.containsKey(item)) {
            UUID first = null;
            long min = Long.MAX_VALUE;
            for (Map.Entry<UUID, Long> e : this.until.entrySet()) {
                if (e.getValue() < min) {
                    min = e.getValue();
                    first = e.getKey();
                }
            }
            this.until.remove(first);
        }
        this.until.put(item, now + BACKOFF_TICKS);
    }

    public boolean blocked(UUID item, long now) {
        Long end = this.until.get(item);
        return end != null && now < end;
    }

    public void prune(long now) {
        for (Iterator<Map.Entry<UUID, Long>> it = this.until.entrySet().iterator(); it.hasNext(); ) {
            if (now >= it.next().getValue()) {
                it.remove();
            }
        }
    }

    public int size() {
        return this.until.size();
    }
}
