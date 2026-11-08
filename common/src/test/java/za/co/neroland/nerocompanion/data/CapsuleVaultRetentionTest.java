package za.co.neroland.nerocompanion.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerocompanion.entity.CompanionMode;
import za.co.neroland.nerocompanion.entity.CompanionRecord;

/** The Capsule Vault is bounded: retention by age, a per-owner cap, erasure by owner. */
class CapsuleVaultRetentionTest {

    private static CompanionRecord record(UUID owner) {
        return new CompanionRecord(CompanionRecord.SCHEMA, UUID.randomUUID(), owner,
                Identifier.fromNamespaceAndPath("nerocompanion", "mining_bot"), CompanionMode.MINE, 1L, List.of(), 0,
                Optional.empty(), Optional.empty(), Optional.empty(), 0L, Optional.empty(), new CompoundTag());
    }

    @Test
    void entriesOlderThanTheRetentionArePurged() {
        CapsuleVault vault = new CapsuleVault();
        UUID owner = UUID.randomUUID();
        vault.store(record(owner));
        long now = System.currentTimeMillis();
        long day = 24L * 60L * 60L * 1000L;
        assertEquals(0, vault.purgeOlderThan(now + 179 * day, 180 * day));
        assertEquals(1, vault.purgeOlderThan(now + 181 * day, 180 * day));
        assertFalse(vault.holdsFor(owner));
    }

    @Test
    void oneOwnerKeepsAtMostTheCapAndErasureRemovesAll() {
        CapsuleVault vault = new CapsuleVault();
        UUID owner = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        for (int i = 0; i < CapsuleVault.MAX_PER_OWNER + 10; i++) {
            vault.store(record(owner));
        }
        vault.store(record(other));
        assertEquals(CapsuleVault.MAX_PER_OWNER + 1, vault.size());
        assertEquals(CapsuleVault.MAX_PER_OWNER, vault.forgetOwner(owner));
        assertTrue(vault.holdsFor(other));
        assertFalse(vault.holdsFor(owner));
    }

    @Test
    void anOwnerlessRecordIsNeverStored() {
        CapsuleVault vault = new CapsuleVault();
        vault.store(record(CompanionRecord.NO_OWNER));
        assertEquals(0, vault.size());
    }
}
