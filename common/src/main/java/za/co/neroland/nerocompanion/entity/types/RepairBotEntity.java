package za.co.neroland.nerocompanion.entity.types;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

import za.co.neroland.nerocompanion.entity.GroundCompanion;

/**
 * The Repair Bot: a narrow ground tower that keeps its owner's gear in shape (REPAIR mode,
 * {@code ai/repair/RepairGoal}). Everything it does lives in its work goal; the class only gives it its own entity
 * type, renderer and hitbox.
 */
public class RepairBotEntity extends GroundCompanion {

    public RepairBotEntity(EntityType<? extends RepairBotEntity> type, Level level) {
        super(type, level);
    }

    /** Defaults before the type file resolves; {@code companion_types/repair_bot.json} sets the live values. */
    public static AttributeSupplier.Builder createAttributes() {
        return GroundCompanion.createAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.ARMOR, 2.0D);
    }
}
