package za.co.neroland.nerocompanion.scheduler;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * An in-memory lookup from companion id to the entity it is loaded as (and its owner), so screen and Remote actions
 * find a loaded companion without scanning levels. Entities register on deploy and on every scheduler evaluation and
 * leave it when recalled, parked or killed. <b>Not a cap source</b>: it starts empty after a restart, so the caps and
 * the duplicate guard read the persistent Recovery Ledger ({@code recovery.RecoveryBook}) instead. Server-thread
 * only.
 */
public final class CompanionIndex {

    private record Entry(UUID owner, UUID entityId, RegionKey region) {
    }

    private static Object boundServer;
    private static CompanionIndex instance = new CompanionIndex();

    private final Map<UUID, Entry> entries = new HashMap<>();

    /** The index for {@code server}; a different server (a new world) starts empty. */
    public static synchronized CompanionIndex forServer(Object server) {
        if (server != boundServer) {
            boundServer = server;
            instance = new CompanionIndex();
        }
        return instance;
    }

    /** The server stopped: drop the reference to it and the entries (single player reopens another world). */
    public static synchronized void serverStopped() {
        boundServer = null;
        instance = new CompanionIndex();
    }

    /** Records or refreshes a LIVE companion. */
    public void touch(UUID companionId, UUID owner, UUID entityId, RegionKey region) {
        Objects.requireNonNull(companionId, "companionId");
        this.entries.put(companionId, new Entry(owner, entityId, region));
    }

    /** Removes a companion, but only if {@code entityId} is the entity the index knows it as. */
    public void remove(UUID companionId, UUID entityId) {
        Entry entry = this.entries.get(companionId);
        if (entry != null && entry.entityId().equals(entityId)) {
            this.entries.remove(companionId);
        }
    }

    /** Erasure / debug: drops every entry for an owner. */
    public int removeOwner(UUID owner) {
        int before = this.entries.size();
        this.entries.values().removeIf(entry -> entry.owner().equals(owner));
        return before - this.entries.size();
    }




    /** The entity UUIDs of {@code owner}'s indexed (loaded) companions. */
    public java.util.List<UUID> entitiesOf(UUID owner) {
        java.util.List<UUID> out = new java.util.ArrayList<>();
        for (Entry entry : this.entries.values()) {
            if (entry.owner().equals(owner)) {
                out.add(entry.entityId());
            }
        }
        return out;
    }

    /** The entity UUID a LIVE companion is known as, if indexed. */
    public java.util.Optional<UUID> entityOf(UUID companionId) {
        Entry entry = this.entries.get(companionId);
        return entry == null ? java.util.Optional.empty() : java.util.Optional.of(entry.entityId());
    }

    /** The owner of a LIVE companion, if indexed. */
    public java.util.Optional<UUID> ownerOf(UUID companionId) {
        Entry entry = this.entries.get(companionId);
        return entry == null ? java.util.Optional.empty() : java.util.Optional.of(entry.owner());
    }

    public int size() {
        return this.entries.size();
    }
}
