package za.co.neroland.nerocompanion.entity.types;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import za.co.neroland.nerocompanion.entity.GroundCompanion;
import za.co.neroland.nerocompanion.mining.BotFocus;

/**
 * The Carry Bot: a ground hauler. In {@code CARRY} it picks up loose items around itself, takes hand-offs from its
 * owner's mining bots, and deposits into the container it is bound to (sneak-use the Remote on a container). The
 * work is {@code ai.carry.CarryGoal}.
 *
 * <p><b>Death salvage.</b> Every carry bot salvages its owner's death drops by default
 * ({@link #salvagesByDefault()}, config {@code salvage.carryBotsByDefault}); the gather goal and the Salvage Hold are
 * in {@code salvage/}.
 */
public class CarryBotEntity extends GroundCompanion {

    private boolean depositRequested;

    public CarryBotEntity(EntityType<? extends CarryBotEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return GroundCompanion.createAttributes();
    }

    /** Remember this bot as the owner's focus for Remote container binding; the click itself falls through. */
    @Override
    protected InteractionResult onInteractOwner(Player owner, InteractionHand hand) {
        BotFocus.focus(owner, this);
        return InteractionResult.PASS;
    }

    /** Server: the owner asked for a deposit run (sneak-use the Remote on the bound container). Not persisted. */
    public void requestDeposit() {
        this.depositRequested = true;
    }

    public boolean depositRequested() {
        return this.depositRequested;
    }

    public void clearDepositRequest() {
        this.depositRequested = false;
    }

    /** Carry bots salvage their owner's death drops without a Salvage chip (config may turn it off). */
    @Override
    public boolean salvagesByDefault() {
        return true;
    }
}
