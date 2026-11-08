package za.co.neroland.nerocompanion.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.google.gson.JsonElement;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;

import org.junit.jupiter.api.Test;

/**
 * The single persistence shape round-trips every field through JSON and NBT (the entity, the Capsule, and later
 * the Bay and the ledger all store this record), the network copy strips owner and home, and names are capped.
 */
class CompanionRecordTest {

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("nerocompanion", path);
    }

    static CompanionRecord everyField() {
        CompoundTag job = new CompoundTag();
        job.putString("area", "north");
        HomePos home = new HomePos(Identifier.fromNamespaceAndPath("minecraft", "the_nether"),
                new BlockPos(12, -40, 99));
        return new CompanionRecord(CompanionRecord.SCHEMA,
                UUID.fromString("11111111-2222-3333-4444-555555555555"),
                UUID.fromString("99999999-8888-7777-6666-555555555555"),
                id("test_drone"), CompanionMode.STAY, 12_345L,
                List.of(new ChipStack(id("speed"), 2), new ChipStack(id("lamp"), 1)),
                321, Optional.of("Sparky"), Optional.of(id("test_drone/aurora")), Optional.of(id("tophat")),
                -42L, Optional.of(home), job);
    }

    @Test
    void jsonRoundTripPreservesEveryField() {
        CompanionRecord record = everyField();
        JsonElement json = CompanionRecord.CODEC.encodeStart(JsonOps.INSTANCE, record).getOrThrow();
        CompanionRecord back = CompanionRecord.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertEquals(record, back);
        assertEquals(record.jobData(), back.jobData());
        assertEquals(StringTag.valueOf("north"), back.jobData().get("area"));
    }

    @Test
    void nbtRoundTripPreservesEveryField() {
        CompanionRecord record = everyField();
        Tag tag = CompanionRecord.CODEC.encodeStart(NbtOps.INSTANCE, record).getOrThrow();
        assertEquals(record, CompanionRecord.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow());
    }

    @Test
    void minimalRecordRoundTripsWithDefaults() {
        CompanionRecord fresh = CompanionRecord.fresh(id("test_drone"), UUID.randomUUID(), CompanionMode.FOLLOW,
                24_000L, 7L);
        JsonElement json = CompanionRecord.CODEC.encodeStart(JsonOps.INSTANCE, fresh).getOrThrow();
        assertEquals(fresh, CompanionRecord.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
        assertTrue(fresh.chips().isEmpty());
        assertTrue(fresh.name().isEmpty() && fresh.home().isEmpty());
    }

    @Test
    void companionIdIsNotOwnerAndSurvivesRebinding() {
        CompanionRecord record = everyField();
        UUID newOwner = UUID.randomUUID();
        CompanionRecord rebound = record.withOwner(newOwner);
        assertEquals(record.companionId(), rebound.companionId());
        assertEquals(newOwner, rebound.ownerId());
    }

    @Test
    void networkCopyStripsOwnerAndHome() {
        CompanionRecord net = everyField().forNetwork();
        assertEquals(CompanionRecord.NO_OWNER, net.ownerId());
        assertTrue(net.home().isEmpty());
        assertTrue(net.jobData().isEmpty());
        assertEquals(everyField().name(), net.name());
        assertEquals(everyField().chips(), net.chips());
    }

    @Test
    void newerSchemaIsRejectedNotGuessed() {
        CompanionRecord record = everyField();
        JsonElement json = CompanionRecord.CODEC.encodeStart(JsonOps.INSTANCE, record).getOrThrow();
        json.getAsJsonObject().addProperty("schema", CompanionRecord.SCHEMA + 1);
        DataResult<CompanionRecord> result = CompanionRecord.CODEC.parse(JsonOps.INSTANCE, json);
        assertTrue(result.error().isPresent());
    }

    @Test
    void undecodableRecordIsReportedWithoutItsData() {
        JsonElement json = CompanionRecord.CODEC.encodeStart(JsonOps.INSTANCE, everyField()).getOrThrow();
        json.getAsJsonObject().addProperty("schema", CompanionRecord.SCHEMA + 1);
        Tag tag = JsonOps.INSTANCE.convertTo(NbtOps.INSTANCE, json);
        java.util.List<String> reasons = new java.util.ArrayList<>();
        assertTrue(CompanionRecord.tryDecode(tag, reasons::add).isEmpty());
        assertEquals(java.util.List.of("saved by a newer version (schema " + (CompanionRecord.SCHEMA + 1) + ")"),
                reasons);
        Tag good = CompanionRecord.CODEC.encodeStart(NbtOps.INSTANCE, everyField()).getOrThrow();
        assertEquals(everyField(), CompanionRecord.tryDecode(good, reasons::add).orElseThrow());
        assertTrue(CompanionRecord.tryDecode(new CompoundTag(), reasons::add).isEmpty());
        assertEquals("unreadable record", reasons.get(reasons.size() - 1));
        assertTrue(reasons.stream().noneMatch(r -> r.contains("9999")), "no owner UUID in the reason");
    }

    @Test
    void unknownModeIsAnError() {
        JsonElement json = CompanionRecord.CODEC.encodeStart(JsonOps.INSTANCE, everyField()).getOrThrow();
        json.getAsJsonObject().addProperty("mode", "dance");
        assertTrue(CompanionRecord.CODEC.parse(JsonOps.INSTANCE, json).error().isPresent());
    }

    @Test
    void namesAreCappedAndCleaned() {
        String longName = "  An extremely long companion name indeed\u0007 ";
        CompanionRecord record = CompanionRecord.fresh(id("test_drone"), UUID.randomUUID(), CompanionMode.FOLLOW,
                1L, 1L);
        CompanionRecord named = new CompanionRecord(record.schema(), record.companionId(), record.ownerId(),
                record.type(), record.mode(), record.energyFe(), record.chips(), record.bondPoints(),
                Optional.of(longName), record.skin(), record.hat(), record.personalitySeed(), record.home(),
                record.jobData());
        assertEquals(CompanionRecord.MAX_NAME_LENGTH, named.name().orElseThrow().length());
        assertEquals("An extremely long compan", named.name().orElseThrow());
    }

    @Test
    void recordIsImmutableThroughJobData() {
        CompanionRecord record = everyField();
        record.jobData().putString("area", "tampered");
        assertEquals(StringTag.valueOf("north"), record.jobData().get("area"));
    }
}
