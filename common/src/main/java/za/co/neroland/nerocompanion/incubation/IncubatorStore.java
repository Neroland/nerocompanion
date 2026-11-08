package za.co.neroland.nerocompanion.incubation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import za.co.neroland.nerolandcore.data.SavedDataCompat;
import za.co.neroland.nerolandcore.data.SavedDataRecovery;

import za.co.neroland.nerocompanion.NeroCompanionCommon;
import za.co.neroland.nerocompanion.entity.HomePos;

/**
 * Every incubating Scrap Egg on the server, keyed by its incubator (dimension + block position). Server only, in
 * the overworld's data storage, read through Core's {@link SavedDataRecovery} guard. Personal data: an owner UUID
 * next to a base position; erased by owner ({@link #forgetOwner}) through Core's {@code PlayerDataErasure}.
 */
public final class IncubatorStore extends SavedData {

    /** One stored egg. */
    record Entry(HomePos at, Incubation egg) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                HomePos.CODEC.fieldOf("at").forGetter(Entry::at),
                Incubation.CODEC.fieldOf("egg").forGetter(Entry::egg)
        ).apply(i, Entry::new));
    }

    private static final Codec<IncubatorStore> CODEC = Entry.CODEC.listOf().xmap(IncubatorStore::new,
            store -> store.eggs.entrySet().stream().map(e -> new Entry(e.getKey(), e.getValue())).toList());

    public static final SavedDataType<IncubatorStore> TYPE = SavedDataCompat.type(
            Identifier.fromNamespaceAndPath(NeroCompanionCommon.MOD_ID, "incubators"), IncubatorStore::new, CODEC);
    public static final String RECOVERY_NAME = "nerocompanion:incubators";

    private final Map<HomePos, Incubation> eggs = new HashMap<>();

    public IncubatorStore() {
        this(List.of());
    }

    private IncubatorStore(List<Entry> entries) {
        for (Entry entry : entries) {
            this.eggs.put(entry.at(), entry.egg());
        }
    }

    public static IncubatorStore get(MinecraftServer server) {
        return SavedDataRecovery.get(server.overworld(), TYPE, IncubatorStore::new, RECOVERY_NAME);
    }

    public Optional<Incubation> at(HomePos pos) {
        return Optional.ofNullable(this.eggs.get(pos));
    }

    public boolean isEmpty() {
        return this.eggs.isEmpty();
    }

    public void put(HomePos pos, Incubation egg) {
        this.eggs.put(pos, egg);
        this.setDirty();
    }

    public Optional<Incubation> remove(HomePos pos) {
        Incubation removed = this.eggs.remove(pos);
        if (removed != null) {
            this.setDirty();
        }
        return Optional.ofNullable(removed);
    }

    /** Where {@code owner}'s eggs are. */
    public List<HomePos> placedBy(UUID owner) {
        List<HomePos> out = new ArrayList<>();
        this.eggs.forEach((pos, egg) -> {
            if (egg.owner().equals(owner)) {
                out.add(pos);
            }
        });
        return out;
    }

    /** Every player with an incubating egg (the inactive-owner retention sweep). */
    public java.util.Set<UUID> owners() {
        java.util.Set<UUID> out = new java.util.HashSet<>();
        this.eggs.values().forEach(egg -> out.add(egg.owner()));
        return out;
    }

    /** Erasure: forget every egg {@code owner} placed (the eggs are gone with the data). */
    public int forgetOwner(UUID owner) {
        int before = this.eggs.size();
        this.eggs.values().removeIf(egg -> egg.owner().equals(owner));
        int removed = before - this.eggs.size();
        if (removed > 0) {
            this.setDirty();
        }
        return removed;
    }
}
