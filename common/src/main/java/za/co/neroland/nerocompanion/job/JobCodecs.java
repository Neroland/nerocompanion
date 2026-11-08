package za.co.neroland.nerocompanion.job;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;

/**
 * Job type id to codec. Packages register during common init (see {@code registry/ModCompanionContent}); the
 * companion writes its job as {@value JobData#KEY_JOB_TYPE} (the id) + {@value JobData#KEY_JOB} (the encoded value)
 * inside {@code CompanionRecord.jobData}.
 *
 * <p>Reading never throws: an id nobody registered (a package removed, a newer build) or a value its codec rejects
 * reads as {@link CompanionJob#none()} with {@link Read#keepRaw()} set, and the companion writes the original keys
 * back unchanged until something sets a new job.
 */
public final class JobCodecs {

    private static final Map<Identifier, Codec<? extends CompanionJob>> CODECS = new ConcurrentHashMap<>();

    private JobCodecs() {
    }

    /**
     * Registers the codec for jobs whose {@link CompanionJob#jobType()} is {@code type}.
     *
     * @throws IllegalStateException if {@code type} is already registered, or is the reserved empty-job id
     */
    public static void register(Identifier type, Codec<? extends CompanionJob> codec) {
        if (type == null || codec == null) {
            throw new IllegalArgumentException("job type and codec are required");
        }
        if (NoJob.ID.equals(type)) {
            throw new IllegalStateException(type + " is reserved for the empty job");
        }
        if (CODECS.putIfAbsent(type, codec) != null) {
            throw new IllegalStateException("job codec " + type + " is already registered");
        }
    }

    public static boolean isRegistered(Identifier type) {
        return CODECS.containsKey(type);
    }

    public static Set<Identifier> known() {
        return Set.copyOf(CODECS.keySet());
    }

    /** The outcome of reading a job: the job, and whether the stored keys must be kept as they are. */
    public record Read(CompanionJob job, boolean keepRaw, Optional<String> problem) {

        static Read none() {
            return new Read(CompanionJob.none(), false, Optional.empty());
        }

        static Read kept(String problem) {
            return new Read(CompanionJob.none(), true, Optional.of(problem));
        }
    }

    /** Reads the job from {@code jobData}. {@code problem} names the job type only, never the data. */
    public static Read read(CompoundTag jobData, DynamicOps<Tag> ops) {
        if (!jobData.contains(JobData.KEY_JOB_TYPE)) {
            return Read.none();
        }
        Identifier type = Identifier.CODEC.parse(ops, jobData.get(JobData.KEY_JOB_TYPE)).result().orElse(null);
        if (type == null) {
            return Read.kept("job type is not a resource id");
        }
        Codec<? extends CompanionJob> codec = CODECS.get(type);
        if (codec == null) {
            return Read.kept("no codec registered for job type " + type);
        }
        Tag value = jobData.get(JobData.KEY_JOB);
        if (value == null) {
            return Read.kept("job " + type + " has no value");
        }
        DataResult<? extends CompanionJob> result = codec.parse(ops, value);
        Optional<? extends CompanionJob> job = result.result();
        if (job.isEmpty()) {
            return Read.kept("job " + type + " could not be decoded");
        }
        if (!type.equals(job.get().jobType())) {
            return Read.kept("job " + type + " decoded as " + job.get().jobType());
        }
        return new Read(job.get(), false, Optional.empty());
    }

    /**
     * Writes {@code job} into {@code jobData} (removing the job keys for the empty job).
     *
     * @throws IllegalStateException if the job's type has no registered codec or the codec cannot encode it - a
     *                               programming error in the package that set the job, never a save-data issue
     */
    @SuppressWarnings("unchecked")
    public static void write(CompoundTag jobData, CompanionJob job, DynamicOps<Tag> ops) {
        jobData.remove(JobData.KEY_JOB_TYPE);
        jobData.remove(JobData.KEY_JOB);
        if (job == null || job.isNone()) {
            return;
        }
        Codec<CompanionJob> codec = (Codec<CompanionJob>) CODECS.get(job.jobType());
        if (codec == null) {
            throw new IllegalStateException("no codec registered for job type " + job.jobType());
        }
        Tag value = codec.encodeStart(ops, job).result().orElseThrow(
                () -> new IllegalStateException("job " + job.jobType() + " could not be encoded"));
        jobData.put(JobData.KEY_JOB_TYPE, Identifier.CODEC.encodeStart(ops, job.jobType()).getOrThrow());
        jobData.put(JobData.KEY_JOB, value);
    }
}
