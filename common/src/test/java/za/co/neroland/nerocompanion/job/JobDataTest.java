package za.co.neroland.nerocompanion.job;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerocompanion.chip.ChipEffects;
import za.co.neroland.nerocompanion.entity.ChipStack;
import za.co.neroland.nerocompanion.entity.CompanionMode;
import za.co.neroland.nerocompanion.entity.CompanionRecord;
import za.co.neroland.nerocompanion.entity.HomePos;

/**
 * Cargo and bound-container persistence inside {@code CompanionRecord.jobData}. Cargo is generic over the stack
 * type, so a string stands in for {@code ItemStack} (which needs a bootstrapped game); "" is the empty stack.
 */
class JobDataTest {

    private static boolean empty(String stack) {
        return stack.isEmpty();
    }

    private static CompanionRecord roundTrip(CompoundTag jobData) {
        CompanionRecord record = new CompanionRecord(CompanionRecord.SCHEMA, UUID.randomUUID(), UUID.randomUUID(),
                Identifier.fromNamespaceAndPath("nerocompanion", "carry_bot"), CompanionMode.CARRY, 5L, List.of(), 0,
                Optional.empty(), Optional.empty(), Optional.empty(), 1L, Optional.empty(), jobData);
        Tag tag = CompanionRecord.CODEC.encodeStart(NbtOps.INSTANCE, record).getOrThrow();
        return CompanionRecord.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
    }

    @Test
    void cargoRoundTripsWithItsSlotIndices() {
        CompoundTag jobData = new CompoundTag();
        JobData.writeCargo(jobData, List.of("iron", "", "", "coal", ""), JobDataTest::empty, Codec.STRING,
                NbtOps.INSTANCE);
        CompoundTag back = roundTrip(jobData).jobData();
        assertEquals(List.of("iron", "", "", "coal"),
                JobData.readCargo(back, Codec.STRING, NbtOps.INSTANCE, ""));
    }

    @Test
    void emptyCargoStoresNoKey() {
        CompoundTag jobData = new CompoundTag();
        JobData.writeCargo(jobData, List.of("x"), JobDataTest::empty, Codec.STRING, NbtOps.INSTANCE);
        JobData.writeCargo(jobData, List.of("", ""), JobDataTest::empty, Codec.STRING, NbtOps.INSTANCE);
        assertFalse(jobData.contains(JobData.KEY_CARGO));
        assertEquals(List.of(), JobData.readCargo(jobData, Codec.STRING, NbtOps.INSTANCE, ""));
    }

    @Test
    void badEntriesAreSkippedNotThrown() {
        CompoundTag jobData = new CompoundTag();
        ListTag list = new ListTag();
        CompoundTag good = new CompoundTag();
        good.putInt("slot", 1);
        good.putString("item", "gold");
        CompoundTag huge = new CompoundTag();
        huge.putInt("slot", 1_000_000);
        huge.putString("item", "never");
        CompoundTag negative = new CompoundTag();
        negative.putInt("slot", -3);
        negative.putString("item", "never");
        CompoundTag noItem = new CompoundTag();
        noItem.putInt("slot", 0);
        list.add(good);
        list.add(huge);
        list.add(negative);
        list.add(noItem);
        jobData.put(JobData.KEY_CARGO, list);
        assertEquals(List.of("", "gold"), JobData.readCargo(jobData, Codec.STRING, NbtOps.INSTANCE, ""));
    }

    @Test
    void overflowIsWhatNoLongerFits() {
        List<String> slots = List.of("a", "", "b", "", "c");
        assertEquals(List.of("b", "c"), JobData.overflow(slots, 2, JobDataTest::empty));
        assertEquals(List.of(), JobData.overflow(slots, 5, JobDataTest::empty));
        assertEquals(List.of("a", "b", "c"), JobData.overflow(slots, 0, JobDataTest::empty));
    }

    @Test
    void boundContainerRoundTripsAndClears() {
        HomePos chest = new HomePos(Identifier.fromNamespaceAndPath("minecraft", "overworld"), new BlockPos(4, 70, -9));
        CompoundTag jobData = new CompoundTag();
        JobData.writeBound(jobData, Optional.of(chest));
        CompanionRecord back = roundTrip(jobData);
        assertEquals(Optional.of(chest), JobData.readBound(back.jobData()));
        // Personal data: never in the network copy.
        assertEquals(Optional.empty(), JobData.readBound(back.forNetwork().jobData()));
        CompoundTag cleared = back.jobData();
        JobData.writeBound(cleared, Optional.empty());
        assertEquals(Optional.empty(), JobData.readBound(cleared));
    }

    @Test
    void jobBoundAndCargoLiveSideBySide() {
        JobCodecsTest.ensureRegistered();
        CompoundTag jobData = new CompoundTag();
        JobCodecs.write(jobData, new JobCodecsTest.DigJob(3, "pit"), NbtOps.INSTANCE);
        JobData.writeBound(jobData, Optional.of(new HomePos(Identifier.fromNamespaceAndPath("minecraft", "overworld"),
                BlockPos.ZERO)));
        JobData.writeCargo(jobData, List.of("stone"), JobDataTest::empty, Codec.STRING, NbtOps.INSTANCE);
        CompoundTag back = roundTrip(jobData).jobData();
        assertEquals(new JobCodecsTest.DigJob(3, "pit"), JobCodecs.read(back, NbtOps.INSTANCE).job());
        assertTrue(JobData.readBound(back).isPresent());
        assertEquals(List.of("stone"), JobData.readCargo(back, Codec.STRING, NbtOps.INSTANCE, ""));
    }

    @Test
    void cargoRackAddsNineSlots() {
        assertEquals(0, ChipEffects.cargoSlots(List.of()));
        assertEquals(9, ChipEffects.cargoSlots(List.of(new ChipStack(
                Identifier.fromNamespaceAndPath("nerocompanion", "cargo_rack"), 1))));
    }
}
