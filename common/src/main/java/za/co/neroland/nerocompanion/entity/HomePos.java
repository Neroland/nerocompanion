package za.co.neroland.nerocompanion.entity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;

/**
 * A companion's home point (its Charging Pad or Bay): dimension id + block position. Used instead of vanilla
 * {@code GlobalPos} so the record codec stays loadable on the plain JVM (GlobalPos initialises {@code Level}).
 * Personal data (it locates a player's base): never sent to clients.
 */
public record HomePos(Identifier dimension, BlockPos pos) {

    public static final Codec<HomePos> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Identifier.CODEC.fieldOf("dimension").forGetter(HomePos::dimension),
            BlockPos.CODEC.fieldOf("pos").forGetter(HomePos::pos)
    ).apply(instance, HomePos::new));

    public HomePos {
        pos = pos.immutable();
    }

    public static HomePos of(Level level, BlockPos pos) {
        return new HomePos(level.dimension().identifier(), pos);
    }

    public boolean isIn(Level level) {
        return this.dimension.equals(level.dimension().identifier());
    }
}
