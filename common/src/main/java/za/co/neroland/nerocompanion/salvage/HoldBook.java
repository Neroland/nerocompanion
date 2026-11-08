package za.co.neroland.nerocompanion.salvage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The Salvage Hold's rules, generic over the stack type so they are tested without a game.
 * <b>One hold per owner</b>: the stacks companions gathered, the companions that gathered them (any of them hands
 * everything back), and one expiry fixed when the first stack went in (never extended). At most
 * {@link #MAX_STACKS} stacks; a full hold takes nothing more.
 *
 * @param <T> the stack type ({@code ItemStack} in game)
 */
public final class HoldBook<T> {

    /** Most stacks one owner's hold keeps. */
    public static final int MAX_STACKS = 64;

    /** One owner's hold. */
    public record Hold<T>(List<T> items, Set<UUID> holders, long expiresAt, long touchedAt) {
        public Hold {
            items = List.copyOf(items);
            holders = Set.copyOf(holders);
        }
    }

    private final Map<UUID, Hold<T>> holds = new LinkedHashMap<>();
    private Runnable onChange = () -> { };

    public HoldBook() {
    }

    public HoldBook(Map<UUID, Hold<T>> loaded) {
        this.holds.putAll(loaded);
    }

    public void onChange(Runnable listener) {
        this.onChange = listener == null ? () -> { } : listener;
    }

    public Optional<Hold<T>> get(UUID owner) {
        return Optional.ofNullable(this.holds.get(owner));
    }

    public Map<UUID, Hold<T>> all() {
        return Map.copyOf(this.holds);
    }

    public boolean isFull(UUID owner) {
        Hold<T> hold = this.holds.get(owner);
        return hold != null && hold.items().size() >= MAX_STACKS;
    }

    /**
     * Puts {@code stack} into {@code owner}'s hold on behalf of {@code holder}. The first stack fixes the expiry at
     * {@code now + holdTicks}.
     *
     * @return whether it went in (false when the hold is full)
     */
    public boolean add(UUID owner, UUID holder, T stack, long now, long holdTicks, long millis) {
        Hold<T> hold = this.holds.get(owner);
        if (hold != null && hold.items().size() >= MAX_STACKS) {
            return false;
        }
        List<T> items = hold == null ? new ArrayList<>() : new ArrayList<>(hold.items());
        Set<UUID> holders = hold == null ? new LinkedHashSet<>() : new LinkedHashSet<>(hold.holders());
        items.add(stack);
        holders.add(holder);
        long expires = hold == null ? now + Math.max(1L, holdTicks) : hold.expiresAt();
        this.holds.put(owner, new Hold<>(items, holders, expires, millis));
        this.onChange.run();
        return true;
    }

    /** Whether the hold of {@code owner} is held (in part) by {@code holder}. */
    public boolean heldBy(UUID owner, UUID holder) {
        Hold<T> hold = this.holds.get(owner);
        return hold != null && hold.holders().contains(holder);
    }

    /**
     * The owner interacted with {@code holder}: if it holds their things, everything comes out (all of the owner's
     * hold, whoever gathered it).
     */
    public List<T> returnTo(UUID owner, UUID holder) {
        if (!this.heldBy(owner, holder)) {
            return List.of();
        }
        Hold<T> hold = this.holds.remove(owner);
        this.onChange.run();
        return hold.items();
    }

    /**
     * {@code holder} is being evaluated at {@code now}: if it holds an expired hold, the items come out (to drop at
     * the companion).
     */
    public List<T> takeExpired(UUID owner, UUID holder, long now) {
        Hold<T> hold = this.holds.get(owner);
        if (hold == null || !hold.holders().contains(holder) || now <= hold.expiresAt()) {
            return List.of();
        }
        this.holds.remove(owner);
        this.onChange.run();
        return hold.items();
    }

    /** Erasure. */
    public boolean forgetOwner(UUID owner) {
        boolean removed = this.holds.remove(owner) != null;
        if (removed) {
            this.onChange.run();
        }
        return removed;
    }

    /** Retention: holds not written for {@code retentionMillis}. @return how many were purged */
    public int purgeUntouched(long nowMillis, long retentionMillis) {
        int before = this.holds.size();
        this.holds.values().removeIf(hold -> nowMillis - hold.touchedAt() > retentionMillis);
        int removed = before - this.holds.size();
        if (removed > 0) {
            this.onChange.run();
        }
        return removed;
    }

    public boolean holdsFor(UUID owner) {
        return this.holds.containsKey(owner);
    }

    /** Every owner with a hold (the inactive-owner retention sweep). */
    public Set<UUID> owners() {
        return Set.copyOf(this.holds.keySet());
    }
}
