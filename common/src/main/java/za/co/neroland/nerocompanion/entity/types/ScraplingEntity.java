package za.co.neroland.nerocompanion.entity.types;

import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerocompanion.ai.pet.FetchJob;
import za.co.neroland.nerocompanion.ai.pet.FetchRules;
import za.co.neroland.nerocompanion.ai.pet.NapPadMemory;
import za.co.neroland.nerocompanion.ai.pet.PetFetchGoal;
import za.co.neroland.nerocompanion.charge.ChargingPadBlock;
import za.co.neroland.nerocompanion.entity.GroundCompanion;
import za.co.neroland.nerocompanion.entity.HomePos;

/**
 * The Scrapling: a small four-legged pet built from scrap, hatched from a Scrap Egg on a Charging Pad or in a
 * Companion Bay. It follows, perches on the owner's shoulder (its type is {@code small}), fetches its owner's
 * Fetch Ball, plays, and naps on its pad. One cosmetic chip slot; no utility.
 *
 * <p><b>Comfort, never combat.</b> A Scrapling never deals damage, never takes a target, has no attack goal and no
 * chip or perk that touches a stat; {@link #canAttack} is always false and {@link #setTarget} always clears. This
 * is the same rule NeroCreatures pets follow, and it is not configurable.
 */
public class ScraplingEntity extends GroundCompanion implements NapPadMemory {

    /** The last Charging Pad that charged it (server memory; see {@link NapPadMemory}). */
    @Nullable
    private HomePos lastPad;

    public ScraplingEntity(EntityType<? extends ScraplingEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return GroundCompanion.createAttributes()
                .add(Attributes.MAX_HEALTH, 8.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.ATTACK_DAMAGE, 0.0D);
    }

    /** Comfort, never combat. */
    @Override
    public boolean canAttack(LivingEntity target) {
        return false;
    }

    /** Comfort, never combat: a Scrapling never holds a target. */
    @Override
    public void setTarget(@Nullable LivingEntity target) {
        super.setTarget(null);
    }

    @Override
    protected void onWorkTick() {
        // On the shoulder, every fetch-scan tick: hand back a ball it was still carrying when it perched, and hop
        // off when there is something to do - the owner went to bed (it naps) or threw a ball.
        if (!this.isPerched() || this.tickCount % FetchRules.SCAN_INTERVAL != 0) {
            return;
        }
        ServerPlayer owner = this.owner();
        if (owner == null) {
            return;
        }
        if (this.job() instanceof FetchJob) {
            PetFetchGoal.handBack(this, owner);
            return;
        }
        if (owner.isSleeping() || (this.job().isNone() && PetFetchGoal.findBall(this, owner.getUUID()) != null)) {
            this.stopPerch();
        }
    }

    /** Remembers the Pad it is docked on, so a Bay-homed Scrapling still naps on the Pad it uses. */
    @Override
    protected void onChargerTick(int ticks) {
        BlockPos feet = this.blockPosition();
        BlockPos pad = this.level().getBlockState(feet).getBlock() instanceof ChargingPadBlock ? feet
                : this.level().getBlockState(feet.below()).getBlock() instanceof ChargingPadBlock ? feet.below()
                : null;
        if (pad != null) {
            this.lastPad = HomePos.of(this.level(), pad);
        }
    }

    @Override
    public Optional<HomePos> lastNapPad() {
        return Optional.ofNullable(this.lastPad);
    }

    @Override
    protected void onLeaveWorld(Entity.RemovalReason reason) {
        // A carried ball travels with the record on recall, parking and unload (it is the job). Only a death
        // leaves it behind, where the Scrapling fell.
        if (reason == Entity.RemovalReason.KILLED && this.job() instanceof FetchJob job && !job.carried().isEmpty()) {
            ItemEntity item = new ItemEntity(this.level(), this.getX(), this.getY() + 0.25D, this.getZ(),
                    job.carried().copy());
            item.setDefaultPickUpDelay();
            this.level().addFreshEntity(item);
            this.setJob(null);
        }
    }
}
