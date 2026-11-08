package za.co.neroland.nerocompanion.entity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.Identifier;

/**
 * One installed chip: its id and tier (I-III). Slot order is the list order in {@link CompanionRecord}. The
 * chip registry itself arrives in Stage 2; this is only the persisted shape, so a companion keeps its chips
 * through recall, parking and recovery from the first build on.
 */
public record ChipStack(Identifier chip, int tier) {

    public static final int MIN_TIER = 1;
    public static final int MAX_TIER = 3;

    public static final Codec<ChipStack> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Identifier.CODEC.fieldOf("chip").forGetter(ChipStack::chip),
            Codec.intRange(MIN_TIER, MAX_TIER).optionalFieldOf("tier", MIN_TIER).forGetter(ChipStack::tier)
    ).apply(instance, ChipStack::new));
}
