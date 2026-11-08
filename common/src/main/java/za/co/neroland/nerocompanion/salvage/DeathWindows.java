package za.co.neroland.nerocompanion.salvage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

/**
 * Which item entities are a player's death drops (pure, memory only). Vanilla death drops carry no thrower, so the
 * mod tags them itself: when a death is seen (at the end of the death tick), the window claims the item entities that
 * are at most {@link #MAX_AGE} tick old, within {@link #H_RADIUS} block horizontally of the death position and
 * between {@link #BELOW} below and {@link #ABOVE} above it, have no thrower and are covered by <b>no other</b> recent
 * death. Each claim records the stack's count at that moment: the companion never gathers more than that from the
 * entity, so a foreign stack that vanilla merges into a claimed drop is left lying. An item two deaths could explain
 * is claimed by neither.
 *
 * <p>One window per player; a second death while it is open adds its position and claims to the same window (the
 * first death's remaining drops stay targets). Every player death opens one, owners or not, so a bystander's death
 * still makes nearby items ambiguous. Windows open whenever drops fall (keepInventory off), even with salvage
 * turned off in the config: then nobody gathers, but the drops are still marked so other players' companions leave
 * them alone. Never saved, never sent; cleared on server stop and on erasure.
 */
public final class DeathWindows {

    /** Item entities older than this (ticks) when the death is seen are not the death's. */
    public static final int MAX_AGE = 1;
    /** Horizontal reach around the death position (drops spawn there with at most 0.5 block/tick spread). */
    public static final double H_RADIUS = 1.0D;
    /** Vertical band around the death position's feet: drops spawn at eye height minus 0.3. */
    public static final double BELOW = 0.5D;
    public static final double ABOVE = 2.0D;

    /** An item entity that might be a death drop. */
    public record Candidate(UUID itemId, String dimension, double x, double y, double z, int age, boolean hasThrower,
            int count) {
    }

    /** One death position. */
    public record Spot(double x, double y, double z, long deathTick) {
        boolean covers(Candidate c) {
            return Math.abs(c.x() - this.x) <= H_RADIUS && Math.abs(c.z() - this.z) <= H_RADIUS
                    && c.y() >= this.y - BELOW && c.y() <= this.y + ABOVE;
        }
    }

    /** One player's window. */
    public static final class Window {
        private final UUID owner;
        private final String dimension;
        private final List<Spot> spots = new ArrayList<>();
        private long expiresAt;
        private final Map<UUID, Integer> items = new LinkedHashMap<>();

        Window(UUID owner, String dimension) {
            this.owner = owner;
            this.dimension = dimension;
        }

        public UUID owner() {
            return this.owner;
        }

        public String dimension() {
            return this.dimension;
        }

        public List<Spot> spots() {
            return List.copyOf(this.spots);
        }

        public long expiresAt() {
            return this.expiresAt;
        }

        /** The claimed item entity ids still to gather. */
        public Set<UUID> items() {
            return Set.copyOf(this.items.keySet());
        }

        /** How many items were claimed from {@code itemId} (0 = not claimed). */
        public int claimed(UUID itemId) {
            return this.items.getOrDefault(itemId, 0);
        }

        boolean coversRecent(Candidate c, long now) {
            if (!this.dimension.equals(c.dimension())) {
                return false;
            }
            for (Spot spot : this.spots) {
                if (now - spot.deathTick() <= MAX_AGE && spot.covers(c)) {
                    return true;
                }
            }
            return false;
        }

        /** Whether {@code (px, py, pz)} in {@code dim} is within {@code range} blocks of any of its deaths. */
        public boolean near(String dim, double px, double py, double pz, double range) {
            if (!this.dimension.equals(dim)) {
                return false;
            }
            for (Spot spot : this.spots) {
                double dx = px - spot.x();
                double dy = py - spot.y();
                double dz = pz - spot.z();
                if (dx * dx + dy * dy + dz * dz <= range * range) {
                    return true;
                }
            }
            return false;
        }
    }

    private final Map<UUID, Window> windows = new HashMap<>();
    /** Every claimed item id, so no item is ever in two windows. */
    private final Set<UUID> claimed = new HashSet<>();

    /**
     * Opens {@code owner}'s window, or adds this death to the one still open in the same dimension (a second death
     * inside the window never abandons the first's drops).
     */
    public Window open(UUID owner, String dimension, double x, double y, double z, long deathTick, long windowTicks) {
        Window window = this.windows.get(owner);
        if (window == null || deathTick > window.expiresAt || !window.dimension.equals(dimension)) {
            this.forget(owner);
            window = new Window(owner, dimension);
            this.windows.put(owner, window);
        }
        window.spots.add(new Spot(x, y, z, deathTick));
        window.expiresAt = Math.max(window.expiresAt, deathTick + Math.max(1L, windowTicks));
        return window;
    }

    /**
     * Claims {@code candidates} for {@code window}: young, unthrown, at this tick's death, not claimed, and covered by
     * no other player's recent death. Records each claimed count.
     *
     * @return how many entities were claimed
     */
    public int claim(Window window, List<Candidate> candidates, long now) {
        int count = 0;
        for (Candidate c : candidates) {
            if (c.hasThrower() || c.age() > MAX_AGE || c.count() <= 0 || this.claimed.contains(c.itemId())
                    || !window.coversRecent(c, now) || this.ambiguous(window, c, now)) {
                continue;
            }
            window.items.put(c.itemId(), c.count());
            this.claimed.add(c.itemId());
            count++;
        }
        return count;
    }

    private boolean ambiguous(Window self, Candidate c, long now) {
        for (Window other : this.windows.values()) {
            if (other != self && other.coversRecent(c, now)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Items another window claimed that this death (seen at {@code now}) also covers: released from both. Call
     * after opening a window and before claiming.
     */
    public void releaseContested(Window opened, List<Candidate> candidates, long now) {
        for (Window other : List.copyOf(this.windows.values())) {
            if (other == opened) {
                continue;
            }
            for (Candidate c : candidates) {
                if (other.items.containsKey(c.itemId()) && c.age() <= MAX_AGE && opened.coversRecent(c, now)) {
                    other.items.remove(c.itemId());
                    this.claimed.remove(c.itemId());
                }
            }
        }
    }

    /** {@code owner}'s open window at {@code now}, if any. */
    public Optional<Window> active(UUID owner, long now) {
        Window window = this.windows.get(owner);
        return window == null || now > window.expiresAt ? Optional.empty() : Optional.of(window);
    }

    /** The item was gathered, picked up or gone: no longer a target. */
    public void release(UUID owner, UUID itemId) {
        Window window = this.windows.get(owner);
        if (window != null && window.items.remove(itemId) != null) {
            this.claimed.remove(itemId);
        }
    }

    /** Whether {@code itemId} is one of {@code owner}'s claimed death drops. */
    public boolean isOwners(UUID owner, UUID itemId) {
        Window window = this.windows.get(owner);
        return window != null && window.items.containsKey(itemId);
    }

    /**
     * Whether {@code itemId} is claimed as a death drop by an open window of a player other than {@code owner}
     * (at {@code now}). Every pickup path that is not salvage refuses such items, so a companion never gathers
     * another player's death pile while that player's window is open. A null {@code owner} counts every window.
     */
    public boolean isClaimedByOther(@Nullable UUID owner, UUID itemId, long now) {
        if (!this.claimed.contains(itemId)) {
            return false;
        }
        for (Window window : this.windows.values()) {
            if (now <= window.expiresAt && !window.owner.equals(owner) && window.items.containsKey(itemId)) {
                return true;
            }
        }
        return false;
    }

    /** How many items of {@code itemId} may be gathered for {@code owner} (0 = none). */
    public int claimedCount(UUID owner, UUID itemId) {
        Window window = this.windows.get(owner);
        return window == null ? 0 : window.claimed(itemId);
    }

    /** Drops windows past their expiry. */
    public void expire(long now) {
        List<UUID> gone = new ArrayList<>();
        this.windows.forEach((owner, window) -> {
            if (now > window.expiresAt) {
                gone.add(owner);
            }
        });
        gone.forEach(this::forget);
    }

    /** Erasure: forget {@code owner}'s window. */
    public void forget(UUID owner) {
        Window window = this.windows.remove(owner);
        if (window != null) {
            this.claimed.removeAll(window.items.keySet());
        }
    }

    public boolean holds(UUID owner) {
        return this.windows.containsKey(owner);
    }

    public void clear() {
        this.windows.clear();
        this.claimed.clear();
    }

    /**
     * How many of a claimed entity's current {@code available} items to take: never more than were claimed (a merged
     * foreign stack is left behind), never more than are there (a claimed stack merged away into another).
     */
    public static int takeCount(int claimed, int available) {
        return Math.max(0, Math.min(claimed, available));
    }
}
