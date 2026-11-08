package za.co.neroland.nerocompanion.job;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerocompanion.entity.CompanionMode;
import za.co.neroland.nerocompanion.entity.CompanionRecord;

/** Job codecs: a job round-trips through {@code CompanionRecord.jobData}, and bad stored jobs are kept, never lost. */
class JobCodecsTest {

    static final Identifier DIG = Identifier.fromNamespaceAndPath("nerocompanion_test", "dig");

    record DigJob(int progress, String area) implements CompanionJob {
        static final Codec<DigJob> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.fieldOf("progress").forGetter(DigJob::progress),
                Codec.STRING.fieldOf("area").forGetter(DigJob::area)
        ).apply(instance, DigJob::new));

        @Override
        public Identifier jobType() {
            return DIG;
        }
    }

    static synchronized void ensureRegistered() {
        if (!JobCodecs.isRegistered(DIG)) {
            JobCodecs.register(DIG, DigJob.CODEC);
        }
    }

    private static CompanionRecord recordWith(CompoundTag jobData) {
        return new CompanionRecord(CompanionRecord.SCHEMA, UUID.randomUUID(), UUID.randomUUID(),
                Identifier.fromNamespaceAndPath("nerocompanion", "mining_bot"), CompanionMode.MINE, 100L, List.of(),
                0, Optional.empty(), Optional.empty(), Optional.empty(), 7L, Optional.empty(), jobData);
    }

    private static CompanionRecord nbtRoundTrip(CompanionRecord record) {
        Tag tag = CompanionRecord.CODEC.encodeStart(NbtOps.INSTANCE, record).getOrThrow();
        return CompanionRecord.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
    }

    @Test
    void aJobRoundTripsThroughTheRecord() {
        ensureRegistered();
        DigJob job = new DigJob(17, "quarry");
        CompoundTag jobData = new CompoundTag();
        jobData.putString("someone_elses_key", "kept");
        JobCodecs.write(jobData, job, NbtOps.INSTANCE);

        CompanionRecord back = nbtRoundTrip(recordWith(jobData));
        JobCodecs.Read read = JobCodecs.read(back.jobData(), NbtOps.INSTANCE);
        assertEquals(job, read.job());
        assertFalse(read.keepRaw());
        assertTrue(read.problem().isEmpty());
        assertEquals(StringTag.valueOf("kept"), back.jobData().get("someone_elses_key"));
        // The network copy never carries the job.
        assertTrue(back.forNetwork().jobData().isEmpty());
    }

    @Test
    void theEmptyJobWritesNothingAndReadsBackEmpty() {
        ensureRegistered();
        CompoundTag jobData = new CompoundTag();
        JobCodecs.write(jobData, new DigJob(1, "a"), NbtOps.INSTANCE);
        JobCodecs.write(jobData, CompanionJob.none(), NbtOps.INSTANCE);
        assertTrue(jobData.isEmpty());
        JobCodecs.Read read = JobCodecs.read(jobData, NbtOps.INSTANCE);
        assertTrue(read.job().isNone());
        assertFalse(read.keepRaw());
    }

    @Test
    void anUnknownJobTypeIsKeptAndReportedWithoutItsData() {
        CompoundTag jobData = new CompoundTag();
        jobData.putString(JobData.KEY_JOB_TYPE, "nerocompanion_test:gone");
        CompoundTag value = new CompoundTag();
        value.putString("secret", "base-at-12-64-99");
        jobData.put(JobData.KEY_JOB, value);
        JobCodecs.Read read = JobCodecs.read(jobData, NbtOps.INSTANCE);
        assertTrue(read.job().isNone());
        assertTrue(read.keepRaw());
        assertTrue(read.problem().orElseThrow().contains("nerocompanion_test:gone"));
        assertFalse(read.problem().orElseThrow().contains("base-at"));
    }

    @Test
    void anUnreadableValueIsKept() {
        ensureRegistered();
        CompoundTag jobData = new CompoundTag();
        jobData.putString(JobData.KEY_JOB_TYPE, DIG.toString());
        jobData.putString(JobData.KEY_JOB, "not an object");
        JobCodecs.Read read = JobCodecs.read(jobData, NbtOps.INSTANCE);
        assertTrue(read.job().isNone());
        assertTrue(read.keepRaw());

        CompoundTag badType = new CompoundTag();
        badType.putString(JobData.KEY_JOB_TYPE, "Not A Resource Id!");
        assertTrue(JobCodecs.read(badType, NbtOps.INSTANCE).keepRaw());
    }

    @Test
    void registrationIsCheckedAndUnregisteredJobsCannotBeWritten() {
        ensureRegistered();
        assertThrows(IllegalStateException.class, () -> JobCodecs.register(DIG, DigJob.CODEC));
        assertThrows(IllegalStateException.class, () -> JobCodecs.register(
                Identifier.fromNamespaceAndPath("nerocompanion", "none"), DigJob.CODEC));
        CompanionJob stray = () -> Identifier.fromNamespaceAndPath("nerocompanion_test", "stray");
        assertThrows(IllegalStateException.class, () -> JobCodecs.write(new CompoundTag(), stray, NbtOps.INSTANCE));
        assertTrue(JobCodecs.known().contains(DIG));
    }
}
