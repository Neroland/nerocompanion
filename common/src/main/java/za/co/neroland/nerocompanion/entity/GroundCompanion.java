package za.co.neroland.nerocompanion.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;

/**
 * A ground companion chassis (bots, the Scrapling): {@link GroundPathNavigation}, step height 1.0, cannot open
 * doors. No ground type ships in Stage 1; Stage 3 registers the bots on this class.
 */
public class GroundCompanion extends CompanionEntity {

    public GroundCompanion(EntityType<? extends GroundCompanion> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.ATTACK_DAMAGE, 0.0D)
                .add(Attributes.ARMOR, 0.0D)
                .add(Attributes.FOLLOW_RANGE, 32.0D)
                .add(Attributes.STEP_HEIGHT, 1.0D);
    }

    @Override
    public Locomotion locomotion() {
        return Locomotion.GROUND;
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        GroundPathNavigation navigation = new GroundPathNavigation(this, level);
        navigation.setCanOpenDoors(false);
        navigation.setCanFloat(true);
        return navigation;
    }

    @Override
    protected void playStepSound(net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        // Soft servo steps instead of the block's footstep (010 section 7).
        this.playSound(za.co.neroland.nerocompanion.registry.ModSounds.BOT_STEP.get(), 0.15F,
                za.co.neroland.nerocompanion.registry.ModSounds.pitch(this.getRandom()));
    }
}
