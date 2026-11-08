package za.co.neroland.nerocompanion.data;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import za.co.neroland.nerolandcore.data.SavedDataCompat;
import za.co.neroland.nerolandcore.data.SavedDataRecovery;

import za.co.neroland.nerocompanion.NeroCompanionCommon;
import za.co.neroland.nerocompanion.entity.CompanionRecord;
import za.co.neroland.nerocompanion.entity.HomePos;

/**
 * The server-only part of every capsuled companion: owner UUID, home position and per-type job data, keyed by
 * {@code companionId}. A Capsule item carries only the shareable part of the record ({@link CompanionRecord#forNetwork}),
 * so the item component is identical on server and client (no 1.21.5+ hashed-stack resync on every click) and no
 * owner or base position ever reaches another client through an item.
 *
 * <p>Written on recall, consumed on deploy. A capsule whose entry is missing (copied in creative, carried to another
 * world) deploys with no home and empty job data, owned by the deployer. Player data: erased by owner UUID through
 * Core's {@code PlayerDataErasure} (registered in common init step 4), and bounded by {@code privacy.ledgerRetentionDays}
 * ({@link #purgeOlderThan}).
 */
public final class CapsuleVault extends SavedData {

    /**
     * The private part of one capsuled record.
     *
     * @param storedAt wall-clock millis it was written (retention); 0 for entries from before retention existed,
     *                 which are stamped on load
     */
    public record Entry(UUID owner, Optional<HomePos> home, CompoundTag job, long storedAt) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("owner").forGetter(Entry::owner),
                HomePos.CODEC.optionalFieldOf("home").forGetter(Entry::home),
                CompoundTag.CODEC.optionalFieldOf("job", new CompoundTag()).forGetter(Entry::job),
                Codec.LONG.optionalFieldOf("stored", 0L).forGetter(Entry::storedAt)
        ).apply(i, Entry::new));

        public Entry {
            job = job == null ? new CompoundTag() : job.copy();
        }

        public Entry(UUID owner, Optional<HomePos> home, CompoundTag job) {
            this(owner, home, job, System.currentTimeMillis());
        }
    }

    private static final Codec<Map<UUID, Entry>> MAP_CODEC = Codec.unboundedMap(UUIDUtil.STRING_CODEC, Entry.CODEC);
    private static final Codec<CapsuleVault> CODEC = MAP_CODEC.xmap(CapsuleVault::new, vault -> vault.entries);

    public static final SavedDataType<CapsuleVault> TYPE = SavedDataCompat.type(
            Identifier.fromNamespaceAndPath(NeroCompanionCommon.MOD_ID, "capsule_vault"), CapsuleVault::new, CODEC);
    public static final String RECOVERY_NAME = "nerocompanion:capsule_vault";

    private final Map<UUID, Entry> entries;

    public CapsuleVault() {
        this(Map.of());
    }

    private CapsuleVault(Map<UUID, Entry> entries) {
        this.entries = new HashMap<>();
        long now = System.currentTimeMillis();
        // Entries saved before retention existed start their clock at this load.
        entries.forEach((id, entry) -> this.entries.put(id, entry.storedAt() > 0L ? entry
                : new Entry(entry.owner(), entry.home(), entry.job(), now)));
    }

    public static CapsuleVault get(MinecraftServer server) {
        return SavedDataRecovery.get(server.overworld(), TYPE, CapsuleVault::new, RECOVERY_NAME);
    }

    /** Keeps the private part of {@code record} (nothing is kept for a record with no owner). */
    public void store(CompanionRecord record) {
        if (CompanionRecord.NO_OWNER.equals(record.ownerId())) {
            return;
        }
        this.entries.put(record.companionId(), new Entry(record.ownerId(), record.home(), record.jobData()));
        this.trimOwner(record.ownerId());
        this.setDirty();
    }

    /** Most vault entries one owner keeps; the oldest go first (a bound on Capsules recalled and never used). */
    public static final int MAX_PER_OWNER = 256;

    private void trimOwner(UUID owner) {
        java.util.List<Map.Entry<UUID, Entry>> mine = new java.util.ArrayList<>();
        for (Map.Entry<UUID, Entry> e : this.entries.entrySet()) {
            if (e.getValue().owner().equals(owner)) {
                mine.add(e);
            }
        }
        if (mine.size() <= MAX_PER_OWNER) {
            return;
        }
        mine.sort(java.util.Comparator.comparingLong(e -> e.getValue().storedAt()));
        for (int i = 0; i < mine.size() - MAX_PER_OWNER; i++) {
            this.entries.remove(mine.get(i).getKey());
        }
    }

    /** The private part for {@code companionId}, left in place. */
    public Optional<Entry> peek(UUID companionId) {
        return Optional.ofNullable(this.entries.get(companionId));
    }

    /** Removes and returns the private part for {@code companionId}. */
    public Optional<Entry> take(UUID companionId) {
        Entry entry = this.entries.remove(companionId);
        if (entry != null) {
            this.setDirty();
        }
        return Optional.ofNullable(entry);
    }

    /** {@code shared} completed with its private part, if the vault has one (consumed). */
    public CompanionRecord restore(CompanionRecord shared) {
        return this.take(shared.companionId()).map(entry -> merge(shared, entry)).orElse(shared);
    }

    /** Pure merge of a shared record and its private part (see {@link CapsuleTransfer}). */
    public static CompanionRecord merge(CompanionRecord shared, Entry entry) {
        return CapsuleTransfer.merge(shared, entry);
    }

    /** Erasure: forget every entry owned by {@code player}. Tolerates an unknown player. */
    public int forgetOwner(UUID player) {
        int before = this.entries.size();
        this.entries.values().removeIf(entry -> entry.owner().equals(player));
        int removed = before - this.entries.size();
        if (removed > 0) {
            this.setDirty();
        }
        return removed;
    }

    /**
     * Retention: entries written more than {@code retentionMillis} ago are dropped - a Capsule burnt in lava, voided or
     * forgotten in a chest no longer keeps its owner's UUID and base position on the server. Such a Capsule still
     * deploys afterwards, without its home and job data (like a Capsule that changed hands).
     *
     * @return how many were purged
     */
    public int purgeOlderThan(long nowMillis, long retentionMillis) {
        int before = this.entries.size();
        this.entries.values().removeIf(entry -> nowMillis - entry.storedAt() > retentionMillis);
        int removed = before - this.entries.size();
        if (removed > 0) {
            this.setDirty();
        }
        return removed;
    }

    public boolean holdsFor(UUID player) {
        return this.entries.values().stream().anyMatch(entry -> entry.owner().equals(player));
    }

    /** Every owner with an entry (the inactive-owner retention sweep). */
    public java.util.Set<UUID> owners() {
        java.util.Set<UUID> out = new java.util.HashSet<>();
        this.entries.values().forEach(entry -> out.add(entry.owner()));
        return out;
    }

    /** {@code player}'s entries (their own export): companion id to private part. */
    public Map<UUID, Entry> entriesOf(UUID player) {
        Map<UUID, Entry> out = new java.util.LinkedHashMap<>();
        this.entries.forEach((id, entry) -> {
            if (entry.owner().equals(player)) {
                out.put(id, entry);
            }
        });
        return out;
    }

    public int size() {
        return this.entries.size();
    }
}
