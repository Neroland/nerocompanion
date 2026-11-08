package za.co.neroland.nerocompanion.job;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;

import za.co.neroland.nerocompanion.entity.HomePos;

/**
 * The layout of {@code CompanionRecord.jobData}, the companion's server-only per-type state. Pure, so the
 * round-trip is unit-tested without a game.
 *
 * <pre>
 * job_type  string     the job's codec id            (absent = no job)          {@link JobCodecs}
 * job       any        the job, encoded by that codec
 * bound     HomePos    the bound container            (absent = none)          {@link #readBound}
 * cargo     list       [{slot:int, item:...}], non-empty slots only             {@link #readCargo}
 * </pre>
 *
 * Keys this class does not know are left alone (a newer build's data survives a round-trip through an older one).
 * Cargo is generic over the stack type so the test can use a stand-in; the entity passes {@code ItemStack.CODEC}
 * and registry-aware ops.
 */
public final class JobData {

    public static final String KEY_JOB_TYPE = "job_type";
    public static final String KEY_JOB = "job";
    public static final String KEY_BOUND = "bound";
    public static final String KEY_CARGO = "cargo";

    /** Slot indices at or above this are ignored on read (a corrupt index must not allocate a huge list). */
    public static final int MAX_CARGO_INDEX = 256;

    private JobData() {
    }

    /** The bound container, if one is stored and readable. */
    public static Optional<HomePos> readBound(CompoundTag jobData) {
        Tag tag = jobData.get(KEY_BOUND);
        if (tag == null) {
            return Optional.empty();
        }
        return HomePos.CODEC.parse(NbtOps.INSTANCE, tag).result();
    }

    public static void writeBound(CompoundTag jobData, Optional<HomePos> bound) {
        jobData.remove(KEY_BOUND);
        bound.flatMap(pos -> HomePos.CODEC.encodeStart(NbtOps.INSTANCE, pos).result())
                .ifPresent(tag -> jobData.put(KEY_BOUND, tag));
    }

    private record Slot<T>(int slot, T stack) {
    }

    private static <T> Codec<Slot<T>> slotCodec(Codec<T> stackCodec) {
        return RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.fieldOf("slot").forGetter(Slot<T>::slot),
                stackCodec.fieldOf("item").forGetter(Slot<T>::stack)
        ).apply(instance, Slot::new));
    }

    /**
     * The stored cargo as a slot-indexed list (holes are {@code empty}), as long as the highest stored slot + 1.
     * Unreadable entries and indices outside {@code 0..}{@link #MAX_CARGO_INDEX}{@code -1} are skipped; nothing
     * throws.
     */
    public static <T> List<T> readCargo(CompoundTag jobData, Codec<T> stackCodec, DynamicOps<Tag> ops, T empty) {
        List<T> slots = new ArrayList<>();
        Tag tag = jobData.get(KEY_CARGO);
        if (tag == null) {
            return slots;
        }
        List<Tag> entries = ops.getStream(tag).result().map(stream -> stream.toList()).orElse(List.of());
        Codec<Slot<T>> codec = slotCodec(stackCodec);
        for (Tag entry : entries) {
            Optional<Slot<T>> read = codec.parse(ops, entry).result();
            if (read.isEmpty() || read.get().slot() < 0 || read.get().slot() >= MAX_CARGO_INDEX) {
                continue;
            }
            int index = read.get().slot();
            while (slots.size() <= index) {
                slots.add(empty);
            }
            slots.set(index, read.get().stack());
        }
        return slots;
    }

    /** Writes the non-empty slots of {@code slots} (removing the key when there are none). */
    public static <T> void writeCargo(CompoundTag jobData, List<T> slots, Predicate<T> isEmpty, Codec<T> stackCodec,
            DynamicOps<Tag> ops) {
        jobData.remove(KEY_CARGO);
        List<Slot<T>> stored = new ArrayList<>();
        for (int i = 0; i < slots.size(); i++) {
            T stack = slots.get(i);
            if (stack != null && !isEmpty.test(stack)) {
                stored.add(new Slot<>(i, stack));
            }
        }
        if (stored.isEmpty()) {
            return;
        }
        slotCodec(stackCodec).listOf().encodeStart(ops, stored).result().ifPresent(tag -> jobData.put(KEY_CARGO, tag));
    }

    /**
     * Splits a slot-indexed list at {@code size}: the first part fills a container of that size, the rest (non-empty
     * only) has nowhere to go and must be handed out by the caller.
     */
    public static <T> List<T> overflow(List<T> slots, int size, Predicate<T> isEmpty) {
        List<T> spill = new ArrayList<>();
        for (int i = Math.max(0, size); i < slots.size(); i++) {
            T stack = slots.get(i);
            if (stack != null && !isEmpty.test(stack)) {
                spill.add(stack);
            }
        }
        return spill;
    }
}
