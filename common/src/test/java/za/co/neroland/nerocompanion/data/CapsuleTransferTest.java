package za.co.neroland.nerocompanion.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerocompanion.entity.CompanionMode;
import za.co.neroland.nerocompanion.entity.CompanionRecord;
import za.co.neroland.nerocompanion.entity.HomePos;

/** A Capsule that changes hands never carries the previous owner's home or job data into the new owner's companion. */
class CapsuleTransferTest {

    private static final UUID A = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID B = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final HomePos BASE = new HomePos(Identifier.fromNamespaceAndPath("minecraft", "overworld"),
            new BlockPos(100, 64, -200));

    private static CompanionRecord ownedByA() {
        CompoundTag job = new CompoundTag();
        job.putString("container", "chest");
        return new CompanionRecord(CompanionRecord.SCHEMA, UUID.randomUUID(), A,
                Identifier.fromNamespaceAndPath("nerocompanion", "test_drone"), CompanionMode.STAY, 500L, List.of(), 120,
                Optional.of("Pip"), Optional.empty(), Optional.empty(), 7L, Optional.of(BASE), job);
    }

    private static CapsuleVault.Entry entryOf(CompanionRecord record) {
        return new CapsuleVault.Entry(record.ownerId(), record.home(), record.jobData());
    }

    @Test
    void theOwnersOwnRedeployKeepsHomeAndJob() {
        CompanionRecord full = ownedByA();
        CompanionRecord deployed = CapsuleTransfer.forDeploy(full.forNetwork(), Optional.of(entryOf(full)), A)
                .forDeployer(A);
        assertEquals(A, deployed.ownerId());
        assertEquals(Optional.of(BASE), deployed.home());
        assertEquals(full.jobData(), deployed.jobData());
    }

    @Test
    void someoneElsesCapsuleDeploysWithoutHomeOrJob() {
        CompanionRecord full = ownedByA();
        CompanionRecord deployed = CapsuleTransfer.forDeploy(full.forNetwork(), Optional.of(entryOf(full)), B)
                .forDeployer(B);
        assertEquals(B, deployed.ownerId());
        assertTrue(deployed.home().isEmpty());
        assertTrue(deployed.jobData().isEmpty());
        assertEquals(full.chips(), deployed.chips());
        assertEquals(full.companionId(), deployed.companionId());
    }

    @Test
    void aCapsuleWithoutAVaultEntryDeploysTheShareablePart() {
        CompanionRecord deployed = CapsuleTransfer.forDeploy(ownedByA().forNetwork(), Optional.empty(), A)
                .forDeployer(A);
        assertEquals(A, deployed.ownerId());
        assertTrue(deployed.home().isEmpty());
    }

    @Test
    void theEntityGuardDropsAnotherOwnersHomeEvenForAFullRecord() {
        CompanionRecord full = ownedByA();
        CompanionRecord forB = full.forDeployer(B);
        assertEquals(B, forB.ownerId());
        assertTrue(forB.home().isEmpty());
        assertTrue(forB.jobData().isEmpty());
        CompanionRecord forA = full.forDeployer(A);
        assertEquals(full, forA);
    }
}
