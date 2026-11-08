package za.co.neroland.nerocompanion.entity.types;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.PathType;

import za.co.neroland.nerocompanion.ai.combat.GuardRules;
import za.co.neroland.nerocompanion.entity.GroundCompanion;

/**
 * The Combat Bot: a tripod turret that guards its owner (GUARD) or its post (STAY) through
 * {@code ai/combat/GuardTargetGoal} and {@code GuardMeleeGoal}, every target filtered by the owner's authority.
 *
 * <p><b>Fragile by design:</b> low max health from {@code companion_types/combat_bot.json} and <b>no natural
 * regeneration</b>. It heals only while docked on a powered Charging Pad ({@link #onChargerTick}), at
 * {@link GuardRules#HEAL_PER_SECOND_CHARGING} health per second, awake or asleep - not in the Bay, not on the Crank.
 * Its health travels with the record, so recall, parking and reclaim do not heal it. Potions and beacons still work
 * (they cost the owner something).
 *
 * <p>Signals for the client (010 section 4.3): WORKING while a target is held (in GUARD and STAY alike, see
 * {@link GuardRules#playsGuardLoop}); {@link #EVENT_LUNGE} once per hit, which starts {@link #lungeTicks()}.
 * Paths around fire (not only lava) so a chase does not end in flames.
 */
public class CombatBotEntity extends GroundCompanion {

    /** Entity event: one melee hit (server -> client); the renderer plays the 6-tick lunge. */
    public static final byte EVENT_LUNGE = 4;

    /** Lunge length in ticks (010 section 4.3). */
    public static final int LUNGE_TICKS = 6;

    /** Fractional health owed by charger ticks (charger calls come in 5-tick slices). */
    private float healCredit;

    /** Client: ticks left of the current lunge. */
    private int lungeTicks;

    public CombatBotEntity(EntityType<? extends CombatBotEntity> type, Level level) {
        super(type, level);
        // Every fire path type (the names differ per line: DANGER_FIRE/DAMAGE_FIRE up to 1.21.x) is a wall.
        for (PathType pathType : PathType.values()) {
            if (pathType.name().contains("FIRE")) {
                this.setPathfindingMalus(pathType, -1.0F);
            }
        }
    }

    /** Client: ticks left of the current hit lunge (0 when none), for the renderer. */
    public int lungeTicks() {
        return this.lungeTicks;
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == EVENT_LUNGE) {
            this.lungeTicks = LUNGE_TICKS;
            return;
        }
        super.handleEntityEvent(id);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.lungeTicks > 0) {
            this.lungeTicks--;
        }
    }

    /** Defaults before the type file resolves; {@code companion_types/combat_bot.json} sets the live values. */
    public static AttributeSupplier.Builder createAttributes() {
        return GroundCompanion.createAttributes()
                .add(Attributes.MAX_HEALTH, GuardRules.FRAGILE_MAX_HEALTH)
                .add(Attributes.ATTACK_DAMAGE, 4.0D)
                .add(Attributes.ARMOR, 2.0D);
    }

    /** Heals while docked on a powered Charging Pad - asleep included (a flat bot docks asleep). */
    @Override
    protected void onChargerTick(int ticks) {
        this.healCredit += GuardRules.healPerSecond(true) * ticks / 20.0F;
        if (this.healCredit >= 1.0F) {
            float heal = (float) Math.floor(this.healCredit);
            this.healCredit -= heal;
            if (this.getHealth() < this.getMaxHealth()) {
                this.heal(heal);
            }
        }
    }
}
