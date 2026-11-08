package za.co.neroland.nerocompanion.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;

/**
 * A flying companion chassis (drones): {@link FlyingMoveControl} (raw constructor, so one line compiles on 26.1.2
 * where it is not generic and on 26.2+ where it is), {@link FlyingPathNavigation}, no gravity, no fall damage.
 * It hovers awake and asleep alike: a sleeping flyer never sinks into water or lava.
 */
public class FlyingCompanion extends CompanionEntity {

    // this-escape: idiomatic Minecraft constructor wiring. rawtypes/unchecked: FlyingMoveControl is generic from
    // 26.2 on but not on 26.1.2.
    @SuppressWarnings({"this-escape", "rawtypes", "unchecked"})
    public FlyingCompanion(EntityType<? extends FlyingCompanion> type, Level level) {
        super(type, level);
        this.moveControl = new FlyingMoveControl(this, 20, true);
        this.setNoGravity(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 10.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.FLYING_SPEED, 0.5D)
                .add(Attributes.ATTACK_DAMAGE, 0.0D)
                .add(Attributes.ARMOR, 0.0D)
                .add(Attributes.FOLLOW_RANGE, 32.0D)
                .add(Attributes.FALL_DAMAGE_MULTIPLIER, 0.0D);
    }

    @Override
    public Locomotion locomotion() {
        return Locomotion.FLYING;
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation navigation = new FlyingPathNavigation(this, level);
        navigation.setCanOpenDoors(false);
        navigation.setCanFloat(true);
        return navigation;
    }
}
