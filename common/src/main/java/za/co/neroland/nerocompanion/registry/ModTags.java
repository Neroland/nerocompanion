package za.co.neroland.nerocompanion.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;

import za.co.neroland.nerocompanion.NeroCompanionCommon;

/** NeroCompanion's own tags. Optional sibling entries inside them use {@code "required": false}. */
public final class ModTags {

    /** Blocks no companion ever breaks. */
    public static final TagKey<Block> UNMINEABLE = TagKey.create(Registries.BLOCK,
            Identifier.fromNamespaceAndPath(NeroCompanionCommon.MOD_ID, "unmineable"));

    /** Mobs no companion ever attacks, on top of the fixed villager/golem rule. */
    public static final TagKey<EntityType<?>> PROTECTED_MOBS = TagKey.create(Registries.ENTITY_TYPE,
            Identifier.fromNamespaceAndPath(NeroCompanionCommon.MOD_ID, "protected_mobs"));

    private ModTags() {
    }
}
