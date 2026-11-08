package za.co.neroland.nerocompanion.job;

import net.minecraft.resources.Identifier;

/**
 * A companion's persisted per-type working state (a mining area and cursor, a carry run, a repair queue...). Each
 * package defines its own immutable job type, registers its codec once with
 * {@link JobCodecs#register(Identifier, com.mojang.serialization.Codec)} under {@link #jobType()}, and the
 * companion persists it inside {@code CompanionRecord.jobData} (see {@link JobData} for the layout).
 *
 * <p>Privacy: a job may hold positions (a player's base). Job data stays on the server: the record's network copy
 * strips it, and a companion that changes hands loses it.
 */
public interface CompanionJob {

    /** The codec key this job was registered under. */
    Identifier jobType();

    /** True only for {@link #none()}. */
    default boolean isNone() {
        return false;
    }

    /** The empty job: nothing to do, nothing persisted. */
    static CompanionJob none() {
        return NoJob.INSTANCE;
    }
}
