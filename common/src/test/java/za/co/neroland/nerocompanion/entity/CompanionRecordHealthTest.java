package za.co.neroland.nerocompanion.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;

import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerocompanion.charge.BaySlots;

/** Schema 2: health travels with the record (fragility survives recall, parking and reclaim). */
class CompanionRecordHealthTest {

    private static CompanionRecord hurt() {
        return CompanionRecordTest.everyField().withHealth(3.5F);
    }

    @Test
    void schemaIsTwo() {
        assertEquals(2, CompanionRecord.SCHEMA);
    }

    @Test
    void healthRoundTripsThroughJsonAndNbt() {
        CompanionRecord record = hurt();
        JsonElement json = CompanionRecord.CODEC.encodeStart(JsonOps.INSTANCE, record).getOrThrow();
        assertEquals(record, CompanionRecord.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
        Tag tag = CompanionRecord.CODEC.encodeStart(NbtOps.INSTANCE, record).getOrThrow();
        CompanionRecord back = CompanionRecord.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
        assertEquals(3.5F, back.health());
        assertFalse(back.fullHealth());
    }

    @Test
    void schemaOneUpgradesToFullHealth() {
        JsonElement json = CompanionRecord.CODEC.encodeStart(JsonOps.INSTANCE, hurt()).getOrThrow();
        json.getAsJsonObject().addProperty("schema", 1);
        json.getAsJsonObject().remove("health");
        CompanionRecord upgraded = CompanionRecord.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertEquals(CompanionRecord.SCHEMA, upgraded.schema());
        assertTrue(upgraded.fullHealth());
        assertEquals(hurt().withHealth(CompanionRecord.FULL_HEALTH), upgraded);
    }

    @Test
    void zeroOrNegativeOrNanHealthReadsAsFull() {
        assertTrue(hurt().withHealth(0.0F).fullHealth());
        assertTrue(hurt().withHealth(-7.0F).fullHealth());
        assertTrue(hurt().withHealth(Float.NaN).fullHealth());
        assertTrue(CompanionRecordTest.everyField().fullHealth());
    }

    @Test
    void copiesKeepHealth() {
        CompanionRecord record = hurt();
        assertEquals(3.5F, record.forNetwork().health());
        assertEquals(3.5F, record.withOwner(java.util.UUID.randomUUID()).health());
        assertEquals(3.5F, record.forDeployer(java.util.UUID.randomUUID()).health());
        assertEquals(3.5F, BaySlots.withEnergy(record, 5L).health());
    }
}
