package za.co.neroland.nerocompanion.personality;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

import za.co.neroland.nerocompanion.bond.BondClock;
import za.co.neroland.nerocompanion.entity.CompanionEntity;

/**
 * Transient per-companion personality state on the server: chatter timestamps, the bond accrual clock and a few
 * edge flags. Keyed by the companion id (stable across recall, Capsule, Bay parking and reclaim), so a fresh
 * entity cannot reset a cooldown; one table per server instance (weakly held, so it goes with the server).
 * Entries untouched for {@link #EVICT_AFTER} ticks are evicted on a {@link #SWEEP_EVERY}-tick cadence. Never
 * saved; holds no player data. Server thread only.
 */
public final class Mood {

    /** Ticks without use before an entry is dropped (1 h). */
    static final long EVICT_AFTER = 72_000L;
    static final long SWEEP_EVERY = 1_200L;

    private static final Map<Object, Store> STORES = new WeakHashMap<>();

    final BondClock bond = new BondClock();
    long lastChatter = Long.MIN_VALUE;
    final Map<ChatterEvent, Long> lastByEvent = new EnumMap<>(ChatterEvent.class);
    /** Network id of the entity last greeted; a redeploy is a new entity and greets again. */
    int greetedEntity = -1;
    boolean lowBattery;
    boolean pettedOnce;
    long touched;

    Mood() {
    }

    /** One server's moods. Package-private for tests. */
    static final class Store {
        private final Map<UUID, Mood> moods = new HashMap<>();
        private long lastSweep = Long.MIN_VALUE;

        Mood get(UUID id, long now) {
            if (this.lastSweep == Long.MIN_VALUE || now - this.lastSweep >= SWEEP_EVERY || now < this.lastSweep) {
                this.lastSweep = now;
                this.moods.values().removeIf(m -> now - m.touched > EVICT_AFTER || now < m.touched - EVICT_AFTER);
            }
            Mood mood = this.moods.computeIfAbsent(id, k -> new Mood());
            mood.touched = now;
            return mood;
        }

        int size() {
            return this.moods.size();
        }
    }

    /** The mood of {@code companion} (server). */
    public static synchronized Mood of(CompanionEntity companion) {
        Object server = companion.level().getServer();
        Store store = STORES.computeIfAbsent(server == null ? Mood.class : server, k -> new Store());
        UUID id = companion.companionId() != null ? companion.companionId() : companion.getUUID();
        return store.get(id, companion.level().getGameTime());
    }

    public BondClock bond() {
        return this.bond;
    }

    long lastFor(ChatterEvent event) {
        Long last = this.lastByEvent.get(event);
        return last == null ? Long.MIN_VALUE : last;
    }
}
