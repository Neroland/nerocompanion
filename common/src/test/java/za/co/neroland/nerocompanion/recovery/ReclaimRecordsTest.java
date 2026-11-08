package za.co.neroland.nerocompanion.recovery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerocompanion.entity.ChipStack;
import za.co.neroland.nerocompanion.entity.CompanionMode;
import za.co.neroland.nerocompanion.entity.CompanionRecord;
import za.co.neroland.nerocompanion.entity.HomePos;
import za.co.neroland.nerocompanion.job.JobData;

/** The erasure unbind (decision 008 §3) and the {@code /nscomp recover} matching. */
class ReclaimRecordsTest {

    private static CompanionRecord record(String name, String type) {
        CompoundTag job = new CompoundTag();
        job.putString(JobData.KEY_BOUND, "base");
        return new CompanionRecord(CompanionRecord.SCHEMA, UUID.randomUUID(), UUID.randomUUID(),
                Identifier.fromNamespaceAndPath("nerocompanion", type), CompanionMode.FOLLOW, 1_000L,
                List.of(new ChipStack(Identifier.fromNamespaceAndPath("nerocompanion", "armour"), 1)), 1_500,
                Optional.ofNullable(name), Optional.empty(), Optional.empty(), 3L,
                Optional.of(new HomePos(Identifier.fromNamespaceAndPath("minecraft", "overworld"), BlockPos.ZERO)),
                job, 4.0F);
    }

    @Test
    void erasedUnbindsOwnerClearsNameBondHomeAndJobButKeepsTheItemsOwnParts() {
        CompanionRecord r = record("Rusty", "carry_bot");
        CompanionRecord erased = ReclaimRecords.erased(r);
        assertEquals(CompanionRecord.NO_OWNER, erased.ownerId());
        assertTrue(erased.name().isEmpty());
        assertEquals(0, erased.bondPoints());
        assertTrue(erased.home().isEmpty());
        assertTrue(erased.jobData().isEmpty());
        assertEquals(r.chips(), erased.chips());
        assertEquals(r.energyFe(), erased.energyFe());
        assertEquals(r.health(), erased.health());
        assertEquals(r.companionId(), erased.companionId());
    }

    @Test
    void recoverMatchesNameThenTypeAndListsWhenAmbiguous() {
        UUID owner = UUID.randomUUID();
        LedgerEntry rusty = entry(owner, record("Rusty", "carry_bot"));
        LedgerEntry pip = entry(owner, record("Pip", "scout_drone"));
        LedgerEntry unnamed = entry(owner, record(null, "carry_bot"));
        List<LedgerEntry> lost = List.of(rusty, pip, unnamed);
        assertEquals(List.of(rusty), RecoverMatch.select(lost, "rUsTy"));
        assertEquals(List.of(pip), RecoverMatch.select(lost, "scout drone"));
        assertEquals(List.of(rusty, unnamed), RecoverMatch.select(lost, "carry_bot"));
        assertEquals(lost, RecoverMatch.select(lost, ""), "no name: all (the command lists them)");
        assertTrue(RecoverMatch.select(lost, "nobody").isEmpty());
    }

    private static LedgerEntry entry(UUID owner, CompanionRecord r) {
        return new LedgerEntry(r.companionId(), owner, LedgerEntry.State.LOST, 0L, Optional.empty(), Optional.empty(),
                Optional.empty(), 0L, 0L, 0L, 0L, r);
    }
}
