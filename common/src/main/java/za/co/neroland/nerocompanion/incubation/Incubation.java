package za.co.neroland.nerocompanion.incubation;

import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;

/**
 * One Scrap Egg incubating on a Charging Pad or in a Companion Bay: who placed it (the hatchling's owner) and how
 * many loaded ticks it has incubated. Pure value; the clock only runs while the incubator ticks (its chunk is
 * loaded), so an egg left in an unloaded base does not hatch unseen.
 *
 * @param owner        the player who placed the egg (stored UUID; never sent to clients)
 * @param elapsedTicks incubation so far, {@code 0..}{@value #HATCH_TICKS}
 */
public record Incubation(UUID owner, int elapsedTicks) {

    /** Five minutes. */
    public static final int HATCH_TICKS = 6000;

    public static final Codec<Incubation> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("owner").forGetter(Incubation::owner),
            Codec.INT.optionalFieldOf("ticks", 0).forGetter(Incubation::elapsedTicks)
    ).apply(i, Incubation::new));

    public Incubation {
        elapsedTicks = clamp(elapsedTicks);
    }

    /** A freshly placed egg; {@code carriedTicks} is progress the egg item kept from an earlier incubator. */
    public static Incubation start(UUID owner, int carriedTicks) {
        return new Incubation(owner, carriedTicks);
    }

    /** This incubation after {@code ticks} more loaded ticks (negative counts as none). */
    public Incubation advance(int ticks) {
        return ticks <= 0 ? this : new Incubation(this.owner, (int) Math.min(HATCH_TICKS, (long) this.elapsedTicks + ticks));
    }

    public boolean ready() {
        return this.elapsedTicks >= HATCH_TICKS;
    }

    /** 0..1. */
    public float progress() {
        return progressOf(this.elapsedTicks);
    }

    /** Ticks left until it hatches. */
    public int remainingTicks() {
        return HATCH_TICKS - this.elapsedTicks;
    }

    public static float progressOf(int ticks) {
        return clamp(ticks) / (float) HATCH_TICKS;
    }

    /** The egg's look for a progress: 0 plain, 1 cracked (at half), 2 glowing crack (at 90 %). */
    public static int stageOf(float progress) {
        return progress >= 0.9F ? 2 : progress >= 0.5F ? 1 : 0;
    }

    public static int clamp(int ticks) {
        return Math.max(0, Math.min(HATCH_TICKS, ticks));
    }
}
