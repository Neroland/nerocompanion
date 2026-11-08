package za.co.neroland.nerocompanion.entity.types;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerocompanion.entity.GroundCompanion;
import za.co.neroland.nerocompanion.mining.BotFocus;

/**
 * The Mining Bot: a ground chassis that works a {@code MINE} job (an area chosen with the Remote) one block at a
 * time, under its owner's authority, and routes its drops (own cargo, a carry bot, its bound container, the owner).
 * The work itself is {@code ai.mining.MineJobGoal}; this class only remembers the block being cracked, so the crack
 * overlay never outlives the bot.
 */
public class MiningBotEntity extends GroundCompanion {

    @Nullable
    private BlockPos cracking;
    private int crackStage = -1;

    public MiningBotEntity(EntityType<? extends MiningBotEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return GroundCompanion.createAttributes();
    }

    /** Remember this bot as the owner's focus for Remote corner picks; the click itself falls through. */
    @Override
    protected InteractionResult onInteractOwner(Player owner, InteractionHand hand) {
        BotFocus.focus(owner, this);
        return InteractionResult.PASS;
    }

    @Override
    protected void onLeaveWorld(Entity.RemovalReason reason) {
        this.clearCrack();
    }

    /**
     * Server: shows break progress {@code stage} (0-9) on {@code pos}; one block at a time. Sent only when the stage
     * changes (at most ten packets per block), like a player's.
     */
    public void showCrack(BlockPos pos, int stage) {
        if (this.cracking != null && !this.cracking.equals(pos)) {
            this.clearCrack();
        }
        if (this.cracking != null && stage == this.crackStage) {
            return;
        }
        this.cracking = pos.immutable();
        this.crackStage = stage;
        this.level().destroyBlockProgress(this.getId(), pos, stage);
    }

    /** Server: removes the crack overlay, if any. */
    public void clearCrack() {
        if (this.cracking != null) {
            this.level().destroyBlockProgress(this.getId(), this.cracking, -1);
            this.cracking = null;
            this.crackStage = -1;
        }
    }
}
