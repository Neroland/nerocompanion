package za.co.neroland.nerocompanion.item;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;

import za.co.neroland.nerocompanion.entity.CompanionEntity;

/**
 * Creative-only spawn egg: deploys a brand-new companion of one type, owned by the player who used it. A plain
 * {@link Item} (vanilla {@code SpawnEggItem} binds its entity type at construction and changes shape across MC
 * lines); the type is looked up lazily at use time. Same caps as a Capsule.
 */
public class CompanionSpawnEggItem extends Item {

    private final Identifier typeId;

    public CompanionSpawnEggItem(Properties properties, Identifier typeId) {
        super(properties);
        this.typeId = typeId;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level) || !(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.SUCCESS;
        }
        CompanionEntity companion = CompanionDeployer.deploy(level, player, CompanionDeployer.spawnPos(context),
                CompanionDeployer.freshRecord(level, this.typeId, player), false);
        ItemStack stack = context.getItemInHand();
        if (companion != null && !player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        return InteractionResult.SUCCESS_SERVER;
    }
}
