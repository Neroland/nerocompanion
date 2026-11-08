package za.co.neroland.nerocompanion.ai.pet;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import za.co.neroland.nerocompanion.job.CompanionJob;

/**
 * The ball a pet is carrying back to its owner. Kept as the companion's job so it survives a chunk unload, a recall
 * (Capsule) or Bay parking: the pet still brings it back afterwards. Dropped where the pet dies.
 */
public record FetchJob(ItemStack carried) implements CompanionJob {

    public static final Identifier ID = Identifier.fromNamespaceAndPath("nerocompanion", "pet_fetch_job");

    public static final Codec<FetchJob> CODEC = RecordCodecBuilder.create(i -> i.group(
            ItemStack.CODEC.fieldOf("carried").forGetter(FetchJob::carried)
    ).apply(i, FetchJob::new));

    @Override
    public Identifier jobType() {
        return ID;
    }
}
