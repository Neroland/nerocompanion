package za.co.neroland.nerocompanion.mining;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

import za.co.neroland.nerocompanion.job.CompanionJob;

/**
 * A mining bot's persisted job: the area, the dimension it lies in, and how far through it the bot is. Personal
 * data (it locates a player's dig site): server only, inside {@code CompanionRecord.jobData}, which the network copy
 * strips and a change of hands drops.
 *
 * @param dimension the level the area is in; the job only runs there
 * @param cursor    index of the next block to look at ({@link MineArea#at})
 * @param mined     blocks broken so far
 * @param skipped   blocks passed over (unmineable, filtered, unreachable...)
 */
public record MineJob(Identifier dimension, MineArea area, int cursor, int mined, int skipped)
        implements CompanionJob {

    public static final Identifier TYPE = Identifier.fromNamespaceAndPath("nerocompanion", "mine_job");

    public static final Codec<MineJob> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Identifier.CODEC.fieldOf("dimension").forGetter(MineJob::dimension),
            MineArea.CODEC.fieldOf("area").forGetter(MineJob::area),
            Codec.INT.optionalFieldOf("cursor", 0).forGetter(MineJob::cursor),
            Codec.INT.optionalFieldOf("mined", 0).forGetter(MineJob::mined),
            Codec.INT.optionalFieldOf("skipped", 0).forGetter(MineJob::skipped)
    ).apply(instance, MineJob::new));

    public MineJob {
        cursor = Math.max(0, Math.min(cursor, area.volume()));
        mined = Math.max(0, mined);
        skipped = Math.max(0, skipped);
    }

    public static MineJob start(Identifier dimension, MineArea area) {
        return new MineJob(dimension, area, 0, 0, 0);
    }

    @Override
    public Identifier jobType() {
        return TYPE;
    }

    public boolean finished() {
        return this.cursor >= this.area.volume();
    }

    /** The block under the cursor; only valid while not {@link #finished()}. */
    public BlockPos current() {
        return this.area.at(this.cursor);
    }

    public MineJob skip() {
        return new MineJob(this.dimension, this.area, this.cursor + 1, this.mined, this.skipped + 1);
    }

    /** Past an air (or already gone) block: no count either way. */
    public MineJob pass() {
        return new MineJob(this.dimension, this.area, this.cursor + 1, this.mined, this.skipped);
    }

    public MineJob broke() {
        return new MineJob(this.dimension, this.area, this.cursor + 1, this.mined + 1, this.skipped);
    }
}
