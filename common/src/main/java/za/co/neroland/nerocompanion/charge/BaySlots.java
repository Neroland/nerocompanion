package za.co.neroland.nerocompanion.charge;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import za.co.neroland.nerocompanion.entity.CompanionRecord;

/**
 * The four parking slots of a Companion Bay, as full {@link CompanionRecord}s (the entity is removed while
 * parked). Pure: park/unpark/charge are value operations; the block entity owns the instance and persists it with
 * {@link #CODEC}. A companion is in at most one slot; a record never leaves a slot except by being handed out
 * (unpark, or the Bay breaking, which turns each into a Capsule).
 */
public final class BaySlots {

    public static final int SIZE = ChargeMath.BAY_SLOTS;

    private record Slot(int index, CompanionRecord record) {
        static final Codec<Slot> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(0, SIZE - 1).fieldOf("slot").forGetter(Slot::index),
                CompanionRecord.CODEC.fieldOf("companion").forGetter(Slot::record)
        ).apply(i, Slot::new));
    }

    public static final Codec<BaySlots> CODEC = Slot.CODEC.listOf().xmap(BaySlots::fromSlots, BaySlots::toSlots);

    private final CompanionRecord[] slots = new CompanionRecord[SIZE];

    public BaySlots() {
    }

    private static BaySlots fromSlots(List<Slot> list) {
        BaySlots bay = new BaySlots();
        for (Slot slot : list) {
            if (bay.slots[slot.index()] == null && bay.indexOf(slot.record().companionId()) < 0) {
                bay.slots[slot.index()] = slot.record();
            }
        }
        return bay;
    }

    private List<Slot> toSlots() {
        List<Slot> out = new ArrayList<>();
        for (int i = 0; i < SIZE; i++) {
            if (this.slots[i] != null) {
                out.add(new Slot(i, this.slots[i]));
            }
        }
        return out;
    }

    public Optional<CompanionRecord> get(int index) {
        return index < 0 || index >= SIZE ? Optional.empty() : Optional.ofNullable(this.slots[index]);
    }

    public int indexOf(UUID companionId) {
        for (int i = 0; i < SIZE; i++) {
            if (this.slots[i] != null && this.slots[i].companionId().equals(companionId)) {
                return i;
            }
        }
        return -1;
    }

    public int occupied() {
        int count = 0;
        for (CompanionRecord record : this.slots) {
            if (record != null) {
                count++;
            }
        }
        return count;
    }

    public boolean isFull() {
        return this.occupied() >= SIZE;
    }

    /**
     * Parks {@code record} in the first free slot.
     *
     * @return the slot, or -1 when full or this companion is already parked here
     */
    public int park(CompanionRecord record) {
        if (this.indexOf(record.companionId()) >= 0) {
            return -1;
        }
        for (int i = 0; i < SIZE; i++) {
            if (this.slots[i] == null) {
                this.slots[i] = record;
                return i;
            }
        }
        return -1;
    }

    /** Takes the companion out of its slot (empty if it is not parked here). */
    public Optional<CompanionRecord> unpark(UUID companionId) {
        int index = this.indexOf(companionId);
        if (index < 0) {
            return Optional.empty();
        }
        CompanionRecord record = this.slots[index];
        this.slots[index] = null;
        return Optional.of(record);
    }

    /** Replaces a slot's record (charging updates the stored energy). */
    public void set(int index, CompanionRecord record) {
        if (index >= 0 && index < SIZE && this.slots[index] != null
                && this.slots[index].companionId().equals(record.companionId())) {
            this.slots[index] = record;
        }
    }

    /** Empties every slot and returns what was in them (the Bay is breaking). */
    public List<CompanionRecord> drain() {
        List<CompanionRecord> out = new ArrayList<>();
        for (int i = 0; i < SIZE; i++) {
            if (this.slots[i] != null) {
                out.add(this.slots[i]);
                this.slots[i] = null;
            }
        }
        return out;
    }

    /**
     * Only the player who parked a companion may let it out: a different owner can never unpark
     * it - not even to get it without its home and job data. The only change-of-hands route out of a Bay is breaking
     * it (each record becomes a Capsule).
     */
    public static boolean mayUnpark(CompanionRecord record, UUID player) {
        return player != null && !CompanionRecord.NO_OWNER.equals(record.ownerId())
                && record.ownerId().equals(player);
    }

    /** A copy of {@code record} with {@code energyFe}. */
    public static CompanionRecord withEnergy(CompanionRecord r, long energyFe) {
        return new CompanionRecord(r.schema(), r.companionId(), r.ownerId(), r.type(), r.mode(), energyFe, r.chips(),
                r.bondPoints(), r.name(), r.skin(), r.hat(), r.personalitySeed(), r.home(), r.jobData(), r.health());
    }
}
