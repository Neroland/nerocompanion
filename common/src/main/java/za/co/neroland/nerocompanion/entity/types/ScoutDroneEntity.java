package za.co.neroland.nerocompanion.entity.types;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

import za.co.neroland.nerocompanion.ai.drone.DroneLamp;
import za.co.neroland.nerocompanion.ai.drone.LampCarrier;
import za.co.neroland.nerocompanion.entity.FlyingCompanion;

/**
 * The Scout Drone: a flat delta flyer that, in SCOUT, flies ahead of its owner and marks ores and hostiles for the
 * owner only ({@code nerocompanion:scout_ahead}), warns of hostiles behind the owner
 * ({@code nerocompanion:scout_danger_alert}), and carries the Lamp Chip well. Behaviour lives in {@code ai.drone} and
 * {@code scout}; this class only owns the lamp so its light is removed whenever the drone leaves the world
 * (see {@link DroneLamp} for every removal path).
 */
public class ScoutDroneEntity extends FlyingCompanion implements LampCarrier {

    private final DroneLamp lamp = new DroneLamp();

    public ScoutDroneEntity(EntityType<? extends ScoutDroneEntity> type, Level level) {
        super(type, level);
    }

    @Override
    public DroneLamp lamp() {
        return this.lamp;
    }

    @Override
    protected void onLeaveWorld(Entity.RemovalReason reason) {
        this.lamp.clear(this);
    }

    /** Lamp housekeeping in every mode: off while asleep, stale lights retried (cheap; see {@link DroneLamp}). */
    @Override
    public void tick() {
        super.tick();
        if (!this.isRemoved()) {
            this.lamp.serverTick(this);
        }
    }

    /** Dimension change never reaches {@code remove()}: clear the light while its chunk is still loaded. */
    @Override
    protected void removeAfterChangingDimensions() {
        this.lamp.clear(this);
        super.removeAfterChangingDimensions();
    }

    // Every removal reason, including chunk unload (setRemoved), on the lines that have this hook (1.21.4+).
    //? if >=1.21.4 {
    @Override
    public void onRemoval(Entity.RemovalReason reason) {
        this.lamp.clear(this);
        super.onRemoval(reason);
    }
    //?}
}
