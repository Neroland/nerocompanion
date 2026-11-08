package za.co.neroland.nerocompanion.ai.drone;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

import za.co.neroland.nerocompanion.job.CompanionJob;

/**
 * The drone's persisted lamp state: where its light block is (and, after a crash or a lost unload cleanup, where a
 * stale one may still be), so that a light is never left behind across a save. At most three spots: the current light
 * and up to two stale lights awaiting cleanup. A position of the owner's surroundings: server-only job data, stripped from
 * the network copy and dropped when the companion changes hands, like every job.
 */
public record LampJob(List<Spot> spots) implements CompanionJob {

    public static final Identifier TYPE = Identifier.fromNamespaceAndPath("nerocompanion", "drone_lamp");
    public static final int MAX_SPOTS = 3;

    /** One light position in a dimension. */
    public record Spot(Identifier dimension, BlockPos pos) {
        public static final Codec<Spot> CODEC = RecordCodecBuilder.create(i -> i.group(
                Identifier.CODEC.fieldOf("dim").forGetter(Spot::dimension),
                BlockPos.CODEC.fieldOf("pos").forGetter(Spot::pos)).apply(i, Spot::new));
    }

    public static final Codec<LampJob> CODEC = RecordCodecBuilder.create(i -> i.group(
            Spot.CODEC.listOf().fieldOf("spots").forGetter(LampJob::spots)).apply(i, LampJob::new));

    public LampJob {
        spots = List.copyOf(spots.size() > MAX_SPOTS ? spots.subList(0, MAX_SPOTS) : spots);
    }

    @Override
    public Identifier jobType() {
        return TYPE;
    }
}
