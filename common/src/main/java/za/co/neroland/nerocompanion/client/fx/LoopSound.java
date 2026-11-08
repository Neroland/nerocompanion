package za.co.neroland.nerocompanion.client.fx;

import java.util.function.Predicate;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;

/**
 * A looping companion sound (drone hover, charging hum, drill) that follows its entity and stops itself when the
 * entity is gone or {@code keep} turns false. Client only.
 */
final class LoopSound extends AbstractTickableSoundInstance {

    private final Entity entity;
    private final Predicate<Entity> keep;

    LoopSound(SoundEvent sound, Entity entity, float volume, Predicate<Entity> keep) {
        super(sound, SoundSource.NEUTRAL, RandomSource.create(entity.getId()));
        this.entity = entity;
        this.keep = keep;
        this.looping = true;
        this.delay = 0;
        this.volume = volume;
        this.x = entity.getX();
        this.y = entity.getY();
        this.z = entity.getZ();
    }

    @Override
    public void tick() {
        if (this.entity.isRemoved() || !this.keep.test(this.entity)) {
            this.stop();
            return;
        }
        this.x = this.entity.getX();
        this.y = this.entity.getY();
        this.z = this.entity.getZ();
    }

    boolean done() {
        return this.isStopped();
    }
}
