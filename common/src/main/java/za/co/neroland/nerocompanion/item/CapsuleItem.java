package za.co.neroland.nerocompanion.item;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
//? if >=1.21.5 {
import net.minecraft.world.item.component.TooltipDisplay;
//?}
import net.minecraft.world.item.context.UseOnContext;

import za.co.neroland.nerocompanion.data.CapsuleVault;
import za.co.neroland.nerocompanion.entity.CompanionEntity;
import za.co.neroland.nerocompanion.entity.CompanionRecord;
import za.co.neroland.nerocompanion.registry.ModDataComponents;

/**
 * The Capsule: a companion's portable form.
 *
 * <ul>
 *   <li><b>Full</b> (carries {@code nerocompanion:companion}): use on a block to deploy it there. The user becomes
 *       the owner; refused over {@code maxCompanionsPerPlayer} or the region cap, or if this companion is already
 *       deployed. The capsule stays in hand, empty.</li>
 *   <li><b>Empty</b>: use it on one of your own companions to recall it into this capsule (sneak-use with an
 *       empty hand recalls into a new one). Nobody can recall someone else's companion.</li>
 * </ul>
 *
 * <p>The item carries only the shareable part of the record (no owner, no home, no job data): the private part
 * waits server-side in the {@link CapsuleVault} until the capsule is deployed. So the component is the same on the
 * server and on every client (no 1.21.5+ hashed-stack resync per click) and an item in someone's hand, a chest or
 * an item frame never reveals an owner or a base position.
 */
public class CapsuleItem extends Item {

    public CapsuleItem(Properties properties) {
        super(properties);
    }

    /**
     * Writes a companion into {@code stack}: the shareable part on the item, the rest in the vault, and the Recovery
     * Ledger {@code ticket} that lets this Capsule (and no copy of an older one) deploy it. {@code ticket} 0 = none
     * (an ownerless Capsule, e.g. after erasure).
     */
    public static void store(net.minecraft.server.MinecraftServer server, ItemStack stack, CompanionRecord record,
            int chargePercent, long ticket) {
        CapsuleVault.get(server).store(record);
        stack.set(ModDataComponents.COMPANION.get(), record.forNetwork());
        stack.set(ModDataComponents.CHARGE.get(), Math.max(0, Math.min(100, chargePercent)));
        if (ticket != 0L) {
            stack.set(ModDataComponents.TICKET.get(), ticket);
        } else {
            stack.remove(ModDataComponents.TICKET.get());
        }
    }

    /** Empties {@code stack}. */
    public static void clear(ItemStack stack) {
        stack.remove(ModDataComponents.COMPANION.get());
        stack.remove(ModDataComponents.CHARGE.get());
        stack.remove(ModDataComponents.TICKET.get());
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        ItemStack stack = context.getItemInHand();
        CompanionRecord record = stack.get(ModDataComponents.COMPANION.get());
        net.minecraft.resources.Identifier blueprint = stack.get(ModDataComponents.BLUEPRINT.get());
        if (record == null && blueprint == null) {
            return InteractionResult.PASS;
        }
        if (!(context.getLevel() instanceof ServerLevel level) || !(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.SUCCESS;
        }
        if (record == null) {
            // A crafted companion: a fresh record of the blueprint's type, owned by the user.
            CompanionEntity fresh = CompanionDeployer.deploy(level, player, CompanionDeployer.spawnPos(context),
                    CompanionDeployer.freshRecord(level, blueprint, player), false);
            if (fresh != null) {
                stack.remove(ModDataComponents.BLUEPRINT.get());
            }
            return InteractionResult.SUCCESS_SERVER;
        }
        // A Capsule of a companion whose owner was erased loses the name they gave it before anyone deploys it.
        java.util.Optional<CompanionRecord> scrubbed = za.co.neroland.nerocompanion.privacy.CompanionPrivacy
                .scrubbedCapsule(record, za.co.neroland.nerocompanion.recovery.Recovery.book(level.getServer())
                        ::isTombstoned);
        if (scrubbed.isPresent()) {
            record = scrubbed.get();
            stack.set(ModDataComponents.COMPANION.get(), record);
        }
        // Peek, deploy, and only then consume the private part: a refused deploy leaves the vault entry in place.
        CapsuleVault vault = CapsuleVault.get(level.getServer());
        // The private part is merged only for its own owner; someone else's Capsule deploys without it.
        CompanionRecord full = za.co.neroland.nerocompanion.data.CapsuleTransfer.forDeploy(record,
                vault.peek(record.companionId()), player.getUUID());
        Long ticket = stack.get(ModDataComponents.TICKET.get());
        CompanionEntity companion = CompanionDeployer.tryDeploy(level, player, CompanionDeployer.spawnPos(context),
                full, false, new za.co.neroland.nerocompanion.recovery.Recovery.Source.Capsule(
                        ticket == null ? 0L : ticket)).companion();
        if (companion != null) {
            // Consumed on deploy: the owner's own entry was merged; anyone else's is stale and is dropped.
            vault.take(record.companionId());
            clear(stack);
        }
        return InteractionResult.SUCCESS_SERVER;
    }

    @Override
    //? if >=1.21.5 {
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
            Consumer<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, tooltip, flag);
    //?} else {
    /*public void appendHoverText(ItemStack stack, Item.TooltipContext context, java.util.List<Component> lines,
            TooltipFlag flag) {
        super.appendHoverText(stack, context, lines, flag);
        Consumer<Component> tooltip = lines::add;
    *///?}
        CompanionRecord record = stack.get(ModDataComponents.COMPANION.get());
        net.minecraft.resources.Identifier blueprint = stack.get(ModDataComponents.BLUEPRINT.get());
        if (record == null && blueprint != null) {
            tooltip.accept(Component.translatable("item.nerocompanion.capsule.new",
                    Component.translatable("companion_type." + blueprint.getNamespace() + "."
                            + blueprint.getPath().replace('/', '.'))).withStyle(ChatFormatting.GRAY));
            return;
        }
        if (record == null) {
            tooltip.accept(Component.translatable("item.nerocompanion.capsule.empty").withStyle(ChatFormatting.GRAY));
            return;
        }
        tooltip.accept(Component.translatable("item.nerocompanion.capsule.type",
                Component.translatable("companion_type." + record.type().getNamespace() + "."
                        + record.type().getPath().replace('/', '.'))).withStyle(ChatFormatting.GRAY));
        record.name().ifPresent(name -> tooltip.accept(Component.translatable("item.nerocompanion.capsule.name",
                name).withStyle(ChatFormatting.GRAY)));
        Integer charge = stack.get(ModDataComponents.CHARGE.get());
        tooltip.accept(Component.translatable("item.nerocompanion.capsule.charge", charge == null ? 0 : charge)
                .withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("item.nerocompanion.capsule.chips", record.chips().size())
                .withStyle(ChatFormatting.GRAY));
    }
}
