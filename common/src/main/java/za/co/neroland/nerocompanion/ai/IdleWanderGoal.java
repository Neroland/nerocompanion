package za.co.neroland.nerocompanion.ai;

import java.util.EnumSet;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import za.co.neroland.nerocompanion.entity.CompanionEntity;
import za.co.neroland.nerocompanion.entity.CompanionMode;
import za.co.neroland.nerocompanion.entity.Locomotion;
import za.co.neroland.nerocompanion.entity.type.CompanionType;

/**
 * Idle wander (priority 8): an occasional short wander within {@value #RADIUS} blocks of the owner (FOLLOW) or of
 * the stay point (STAY). The chance per check is the type's {@code goal_weights.wander_chance}. A target that
 * cannot be pathed to is simply dropped.
 */
public class IdleWanderGoal extends Goal {

    private static final int RADIUS = 4;

    private final CompanionEntity companion;

    public IdleWanderGoal(CompanionEntity companion) {
        this.companion = companion;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (this.companion.isPerched() || !this.companion.getNavigation().isDone()) {
            return false;
        }
        CompanionType type = this.companion.typeDef();
        double chance = type == null ? 0.05D : type.goalWeights().wanderChance();
        return this.companion.getRandom().nextDouble() < chance && this.center() != null;
    }

    private Vec3 center() {
        if (this.companion.mode() == CompanionMode.STAY) {
            return this.companion.stayAnchor();
        }
        ServerPlayer owner = this.companion.owner();
        return owner == null || owner.level() != this.companion.level() ? null : owner.position();
    }

    @Override
    public boolean canContinueToUse() {
        return !this.companion.getNavigation().isDone();
    }

    @Override
    public void start() {
        Vec3 center = this.center();
        if (center == null) {
            return;
        }
        double x = center.x + this.companion.getRandom().nextInt(RADIUS * 2 + 1) - RADIUS;
        double z = center.z + this.companion.getRandom().nextInt(RADIUS * 2 + 1) - RADIUS;
        double y = this.companion.locomotion() == Locomotion.FLYING
                ? center.y + 1.0D + this.companion.getRandom().nextInt(3)
                : center.y;
        this.companion.getNavigation().moveTo(x, y, z, 0.8D);
    }
}
