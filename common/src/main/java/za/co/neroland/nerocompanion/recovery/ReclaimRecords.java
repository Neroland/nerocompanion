package za.co.neroland.nerocompanion.recovery;

import java.util.Optional;

import net.minecraft.nbt.CompoundTag;

import za.co.neroland.nerocompanion.entity.CompanionRecord;
import za.co.neroland.nerocompanion.job.JobData;

/**
 * The record transforms the ledger, reclaim and erasure use (pure).
 *
 * <ul>
 *   <li>{@link #forLedger}: what the ledger keeps and a reclaim restores. Cargo and the job (a carried Fetch Ball
 *       is the job) are dropped: they are real items and stay with the entity that holds them, so a reclaim can
 *       never double them. The bound container stays (it is the owner's own setting).</li>
 *   <li>{@link #erased}: an erased owner's companion as an ownerless Capsule. Name cleared, bond reset, home and
 *       all job data (bound container, mining area) dropped. Chips, looks, charge and health stay: they belong to
 *       the item, not the person.</li>
 * </ul>
 */
public final class ReclaimRecords {

    private ReclaimRecords() {
    }

    /** {@code record} without cargo or job. */
    public static CompanionRecord forLedger(CompanionRecord record) {
        CompoundTag job = record.jobData();
        job.remove(JobData.KEY_CARGO);
        job.remove(JobData.KEY_JOB);
        job.remove(JobData.KEY_JOB_TYPE);
        return record.withPrivate(record.ownerId(), record.home(), job);
    }

    /** {@code record} unbound from its (erased) owner. */
    public static CompanionRecord erased(CompanionRecord record) {
        return new CompanionRecord(record.schema(), record.companionId(), CompanionRecord.NO_OWNER, record.type(),
                record.mode(), record.energyFe(), record.chips(), 0, Optional.empty(), record.skin(), record.hat(),
                record.personalitySeed(), Optional.empty(), new CompoundTag(), record.health());
    }
}
