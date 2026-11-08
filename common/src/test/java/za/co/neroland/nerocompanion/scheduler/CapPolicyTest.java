package za.co.neroland.nerocompanion.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerocompanion.authority.Decision;
import za.co.neroland.nerocompanion.entity.CompanionMode;
import za.co.neroland.nerocompanion.entity.CompanionRecord;
import za.co.neroland.nerocompanion.entity.HomePos;
import za.co.neroland.nerocompanion.recovery.RecoveryBook;

/**
 * {@code maxCompanionsPerPlayer}, {@code maxActiveBotsPerChunkRegion} (a per-chunk-region active cap) and the
 * duplicate-deploy guard, counted from the Recovery Ledger.
 */
class CapPolicyTest {

    private static final RegionKey HERE = RegionKey.ofBlock("minecraft:overworld", 10, 10);

    private static HomePos at(String dimension, int x, int z) {
        String[] id = dimension.split(":");
        return new HomePos(Identifier.fromNamespaceAndPath(id[0], id[1]), new BlockPos(x, 64, z));
    }

    private static CompanionRecord record(UUID owner) {
        return new CompanionRecord(CompanionRecord.SCHEMA, UUID.randomUUID(), owner,
                Identifier.fromNamespaceAndPath("nerocompanion", "personal_drone"), CompanionMode.FOLLOW, 1L, List.of(),
                0, Optional.empty(), Optional.empty(), Optional.empty(), 0L, Optional.empty(), new CompoundTag());
    }

    private static CompanionRecord live(RecoveryBook book, UUID owner, HomePos where) {
        CompanionRecord r = record(owner);
        book.live(r, UUID.randomUUID(), where, 0L, 0L);
        return r;
    }

    @Test
    void playerCapCountsOnlyThatOwnersLiveCompanions() {
        RecoveryBook book = new RecoveryBook();
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        CompanionLimits limits = new CompanionLimits(2, 100, 32, 64, 10, 96);
        live(book, alice, at("minecraft:overworld", 10, 10));
        live(book, bob, at("minecraft:overworld", 10, 10));
        assertTrue(CapPolicy.canDeploy(book, alice, UUID.randomUUID(), HERE, limits).allowed());
        live(book, alice, at("minecraft:the_nether", 0, 0));
        Decision third = CapPolicy.canDeploy(book, alice, UUID.randomUUID(), HERE, limits);
        assertEquals("authority.nerocompanion.player_cap", third.reasonKey());
        assertTrue(CapPolicy.canDeploy(book, bob, UUID.randomUUID(), HERE, limits).allowed());
    }

    @Test
    void recalledCompanionsNoLongerCount() {
        RecoveryBook book = new RecoveryBook();
        UUID owner = UUID.randomUUID();
        CompanionLimits limits = new CompanionLimits(1, 100, 32, 64, 10, 96);
        CompanionRecord r = live(book, owner, at("minecraft:overworld", 10, 10));
        assertTrue(CapPolicy.canDeploy(book, owner, UUID.randomUUID(), HERE, limits).denied());
        book.toCapsule(r, 1L, 0L);
        assertTrue(CapPolicy.canDeploy(book, owner, UUID.randomUUID(), HERE, limits).allowed());
    }

    @Test
    void regionCapCountsAllOwnersInThe8x8ChunkRegion() {
        RecoveryBook book = new RecoveryBook();
        CompanionLimits limits = new CompanionLimits(100, 3, 32, 64, 10, 96);
        for (int i = 0; i < 3; i++) {
            live(book, UUID.randomUUID(), at("minecraft:overworld", i * 40, 100));
        }
        Decision full = CapPolicy.canDeploy(book, UUID.randomUUID(), UUID.randomUUID(),
                RegionKey.ofBlock("minecraft:overworld", 127, 0), limits);
        assertEquals("authority.nerocompanion.region_cap", full.reasonKey());
        // Block 128 is chunk 8: the next region over.
        assertTrue(CapPolicy.canDeploy(book, UUID.randomUUID(), UUID.randomUUID(),
                RegionKey.ofBlock("minecraft:overworld", 128, 0), limits).allowed());
        // Same coordinates, other dimension: a different region.
        assertTrue(CapPolicy.canDeploy(book, UUID.randomUUID(), UUID.randomUUID(),
                RegionKey.ofBlock("minecraft:the_end", 0, 0), limits).allowed());
    }

    @Test
    void regionKeyHandlesNegativeCoordinates() {
        assertEquals(new RegionKey("d", -1, -1), RegionKey.ofBlock("d", -1, -1));
        assertEquals(new RegionKey("d", -1, 0), RegionKey.ofBlock("d", -128, 127));
        assertEquals(new RegionKey("d", -2, 0), RegionKey.ofBlock("d", -129, 0));
    }

    @Test
    void aLiveCompanionCannotBeDeployedTwice() {
        RecoveryBook book = new RecoveryBook();
        CompanionRecord r = live(book, UUID.randomUUID(), at("minecraft:overworld", 10, 10));
        assertEquals("authority.nerocompanion.already_deployed",
                CapPolicy.canDeploy(book, UUID.randomUUID(), r.companionId(), HERE, CompanionLimits.DEFAULTS)
                        .reasonKey());
    }

    @Test
    void loadedEntityIndexIsPerServer() {
        Object serverA = new Object();
        Object serverB = new Object();
        CompanionIndex a = CompanionIndex.forServer(serverA);
        a.touch(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), HERE);
        assertEquals(1, CompanionIndex.forServer(serverA).size());
        assertEquals(0, CompanionIndex.forServer(serverB).size());
    }
}
