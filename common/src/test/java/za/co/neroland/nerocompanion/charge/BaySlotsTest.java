package za.co.neroland.nerocompanion.charge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerocompanion.entity.ChipStack;
import za.co.neroland.nerocompanion.entity.CompanionMode;
import za.co.neroland.nerocompanion.entity.CompanionRecord;
import za.co.neroland.nerocompanion.entity.HomePos;

/** Bay parking: park/unpark round-trips the full record, four slots, no duplicates, and saving loses nothing. */
class BaySlotsTest {

    private static CompanionRecord record(String name) {
        CompoundTag job = new CompoundTag();
        job.putString("area", "north");
        return new CompanionRecord(CompanionRecord.SCHEMA, UUID.randomUUID(),
                UUID.fromString("99999999-8888-7777-6666-555555555555"),
                Identifier.fromNamespaceAndPath("nerocompanion", "test_drone"), CompanionMode.STAY, 1_234L,
                List.of(new ChipStack(Identifier.fromNamespaceAndPath("nerocompanion", "speed"), 2)), 120,
                Optional.of(name), Optional.empty(), Optional.empty(), 42L,
                Optional.of(new HomePos(Identifier.fromNamespaceAndPath("minecraft", "overworld"), new BlockPos(1, 2, 3))),
                job);
    }

    @Test
    void parkThenUnparkReturnsTheIdenticalRecord() {
        BaySlots bay = new BaySlots();
        CompanionRecord pip = record("Pip");
        assertEquals(0, bay.park(pip));
        assertEquals(1, bay.occupied());
        Optional<CompanionRecord> back = bay.unpark(pip.companionId());
        assertEquals(pip, back.orElseThrow());
        assertEquals(pip.jobData(), back.get().jobData());
        assertEquals(0, bay.occupied());
        assertTrue(bay.unpark(pip.companionId()).isEmpty());
    }

    @Test
    void fourSlotsNoDuplicatesFirstFreeSlot() {
        BaySlots bay = new BaySlots();
        CompanionRecord a = record("A");
        assertEquals(0, bay.park(a));
        assertEquals(-1, bay.park(a));
        assertEquals(1, bay.park(record("B")));
        assertEquals(2, bay.park(record("C")));
        assertEquals(3, bay.park(record("D")));
        assertTrue(bay.isFull());
        assertEquals(-1, bay.park(record("E")));
        bay.unpark(a.companionId());
        assertEquals(0, bay.park(record("F")));
    }

    @Test
    void savedSlotsRoundTripThroughNbtAndJsonKeepingOwnerHomeAndJob() {
        BaySlots bay = new BaySlots();
        CompanionRecord a = record("A");
        CompanionRecord c = record("C");
        bay.park(a);
        bay.park(record("B"));
        bay.park(c);
        bay.unpark(bay.get(1).orElseThrow().companionId());
        Tag nbt = BaySlots.CODEC.encodeStart(NbtOps.INSTANCE, bay).getOrThrow();
        BaySlots fromNbt = BaySlots.CODEC.parse(NbtOps.INSTANCE, nbt).getOrThrow();
        assertEquals(a, fromNbt.get(0).orElseThrow());
        assertTrue(fromNbt.get(1).isEmpty());
        assertEquals(c, fromNbt.get(2).orElseThrow());
        assertEquals(a.ownerId(), fromNbt.get(0).orElseThrow().ownerId());
        assertEquals(a.home(), fromNbt.get(0).orElseThrow().home());
        JsonElement json = BaySlots.CODEC.encodeStart(JsonOps.INSTANCE, bay).getOrThrow();
        BaySlots fromJson = BaySlots.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertEquals(c, fromJson.get(2).orElseThrow());
    }

    @Test
    void drainHandsEveryRecordOutExactlyOnce() {
        BaySlots bay = new BaySlots();
        bay.park(record("A"));
        bay.park(record("B"));
        List<CompanionRecord> out = bay.drain();
        assertEquals(2, out.size());
        assertEquals(0, bay.occupied());
        assertTrue(bay.drain().isEmpty());
    }

    @Test
    void chargingOnlyChangesEnergy() {
        BaySlots bay = new BaySlots();
        CompanionRecord a = record("A");
        bay.park(a);
        bay.set(0, BaySlots.withEnergy(a, 5_000L));
        CompanionRecord charged = bay.get(0).orElseThrow();
        assertEquals(5_000L, charged.energyFe());
        assertEquals(BaySlots.withEnergy(charged, a.energyFe()), a);
    }

    @Test
    void theCapsuleKeepsOnlyTheShareablePartAndTheVaultRestoresTheRest() {
        CompanionRecord full = record("Pip");
        CompanionRecord shared = full.forNetwork();
        assertEquals(CompanionRecord.NO_OWNER, shared.ownerId());
        assertTrue(shared.home().isEmpty());
        assertTrue(shared.jobData().isEmpty());
        // the item component is identical on both sides: stripping a stripped record changes nothing
        assertEquals(shared, shared.forNetwork());
        assertEquals(full, shared.withPrivate(full.ownerId(), full.home(), full.jobData()));
    }
}
