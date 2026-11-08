package za.co.neroland.nerocompanion.hud;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import za.co.neroland.nerocompanion.network.payload.Payloads;
import za.co.neroland.nerocompanion.platform.Services;

/**
 * Server side of the HUD strip: each LIVE companion publishes its summary on its once-a-second evaluation; owners
 * whose list changed get one {@code HudSync} at most once per second, and every owner gets one on login. Only
 * the owner ever receives their list. In memory, per server; after a restart a companion reappears once its chunk
 * loads (the Stage 4 ledger makes this persistent).
 */
public final class HudBoard {

    private static HudBoard instance;
    private static Object instanceFor;

    private final Map<UUID, Map<UUID, Payloads.HudEntry>> byOwner = new LinkedHashMap<>();
    private final Map<UUID, UUID> ownerOf = new LinkedHashMap<>();
    private final Set<UUID> dirty = new HashSet<>();

    private HudBoard() {
    }

    public static synchronized HudBoard forServer(Object server) {
        if (instance == null || instanceFor != server) {
            instance = new HudBoard();
            instanceFor = server;
        }
        return instance;
    }

    public static synchronized void serverStopped() {
        instance = null;
        instanceFor = null;
    }

    /** A companion's current summary (replaces the previous one; marks the owner dirty only on a change). */
    public synchronized void publish(UUID owner, Payloads.HudEntry entry) {
        UUID previousOwner = this.ownerOf.put(entry.companionId(), owner);
        if (previousOwner != null && !previousOwner.equals(owner)) {
            this.remove(entry.companionId());
            this.ownerOf.put(entry.companionId(), owner);
        }
        Map<UUID, Payloads.HudEntry> list = this.byOwner.computeIfAbsent(owner, k -> new LinkedHashMap<>());
        Payloads.HudEntry old = list.put(entry.companionId(), entry);
        if (!entry.equals(old)) {
            this.dirty.add(owner);
        }
    }

    /** The companion's chunk unloaded: still LIVE, no longer loaded. */
    public synchronized void unloaded(UUID companionId) {
        UUID owner = this.ownerOf.get(companionId);
        if (owner == null) {
            return;
        }
        Map<UUID, Payloads.HudEntry> list = this.byOwner.get(owner);
        if (list == null) {
            return;
        }
        Payloads.HudEntry old = list.get(companionId);
        if (old != null) {
            list.put(companionId, new Payloads.HudEntry(old.companionId(), old.type(), old.name(), old.mode(),
                    old.energyPct(), old.bondLevel(), old.flags() & ~Payloads.HudEntry.LOADED, -1, old.modes()));
            this.dirty.add(owner);
        }
    }

    /** No longer LIVE (recalled, parked, killed). */
    public synchronized void remove(UUID companionId) {
        UUID owner = this.ownerOf.remove(companionId);
        if (owner == null) {
            return;
        }
        Map<UUID, Payloads.HudEntry> list = this.byOwner.get(owner);
        if (list != null && list.remove(companionId) != null) {
            this.dirty.add(owner);
            if (list.isEmpty()) {
                this.byOwner.remove(owner);
            }
        }
    }

    public synchronized List<Payloads.HudEntry> entriesFor(UUID owner) {
        Map<UUID, Payloads.HudEntry> list = this.byOwner.get(owner);
        return list == null ? List.of() : List.copyOf(list.values());
    }

    public synchronized int liveCount(UUID owner) {
        Map<UUID, Payloads.HudEntry> list = this.byOwner.get(owner);
        return list == null ? 0 : list.size();
    }

    /** Every 20 ticks: one HudSync to each online owner whose list changed. */
    public void tick(MinecraftServer server) {
        if (server.getTickCount() % 20 != 0) {
            return;
        }
        List<UUID> owners;
        synchronized (this) {
            if (this.dirty.isEmpty()) {
                return;
            }
            owners = new ArrayList<>(this.dirty);
            this.dirty.clear();
        }
        for (UUID owner : owners) {
            ServerPlayer player = server.getPlayerList().getPlayer(owner);
            if (player != null) {
                this.send(player);
            }
        }
    }

    /** Sends {@code player} their own list. */
    public void send(ServerPlayer player) {
        Services.NETWORK.sendToPlayer(player, new Payloads.HudSync(this.entriesFor(player.getUUID())));
    }
}
