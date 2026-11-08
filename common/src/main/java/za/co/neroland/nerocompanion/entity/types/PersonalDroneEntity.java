package za.co.neroland.nerocompanion.entity.types;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import za.co.neroland.nerocompanion.ai.drone.DroneCargo;
import za.co.neroland.nerocompanion.ai.drone.DroneLamp;
import za.co.neroland.nerocompanion.ai.drone.LampCarrier;
import za.co.neroland.nerocompanion.entity.FlyingCompanion;

/**
 * The Personal ("Helper") Drone: a small flyer that follows with a hover bob, perches, carries up to eight stacks
 * (carry-lite, {@code nerocompanion:drone_carry_lite}) and can carry a Lamp Chip. Its behaviour is in
 * {@code ai.drone}; this class only owns the lamp (so the light is cleaned up whenever the drone leaves the world -
 * see {@link DroneLamp})
 * and the "hand me what you carry" interaction.
 */
public class PersonalDroneEntity extends FlyingCompanion implements LampCarrier {

    private final DroneLamp lamp = new DroneLamp();

    public PersonalDroneEntity(EntityType<? extends PersonalDroneEntity> type, Level level) {
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

    /**
     * Owner, empty main hand, not sneaking, while the drone carries something: the cargo goes into the owner's
     * inventory (what does not fit stays in the drone). With empty cargo this passes, so petting works as usual.
     */
    @Override
    protected InteractionResult onInteractOwner(Player owner, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND || owner.isSecondaryUseActive()
                || !owner.getItemInHand(hand).isEmpty() || !(owner instanceof ServerPlayer serverOwner)) {
            return InteractionResult.PASS;
        }
        SimpleContainer cargo = this.cargo();
        if (cargo.isEmpty()) {
            return InteractionResult.PASS;
        }
        if (DroneCargo.handAll(serverOwner, cargo)) {
            this.jobDone();
            serverOwner.sendSystemMessage(Component.translatable("message.nerocompanion.drone.handed_over",
                    this.getDisplayName()), true);
        } else {
            serverOwner.sendSystemMessage(Component.translatable("message.nerocompanion.drone.no_room"), true);
        }
        return InteractionResult.SUCCESS_SERVER;
    }
}
