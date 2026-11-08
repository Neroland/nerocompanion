package za.co.neroland.nerocompanion.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.brigadier.Command;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;

import za.co.neroland.nerocompanion.NeroCompanionCommon;
import za.co.neroland.nerocompanion.entity.CompanionEntity;
import za.co.neroland.nerocompanion.entity.CompanionMode;
import za.co.neroland.nerocompanion.entity.GalleryDisplays;
import za.co.neroland.nerocompanion.incubation.Incubation;
import za.co.neroland.nerocompanion.item.CompanionDeployer;
import za.co.neroland.nerocompanion.item.ScrapEggItem;
import za.co.neroland.nerocompanion.look.Looks;
import za.co.neroland.nerocompanion.recovery.Recovery;
import za.co.neroland.nerocompanion.registry.ModBlocks;
import za.co.neroland.nerocompanion.registry.ModDataComponents;
import za.co.neroland.nerocompanion.registry.ModEntities;
import za.co.neroland.nerocompanion.registry.ModItems;

/**
 * The creative showcase, {@code /nscomp gallery [clear]} (operators, creative, nobody else within
 * {@value #OTHERS_RANGE} blocks - it replaces blocks, so it is for a test world). It works out a {@link GalleryLayout}
 * from everything the mod adds, lays a graphite stage of that size east and south of the caller's feet, and shows:
 * every type standard, every type skin, each family skin on every type, every hat on every type that may wear it,
 * the poses (asleep, napping, working, charging on a Charging Pad, the energy bands, bond levels 0-4) and, on the
 * back wall, every item in the namespace plus a filled and a blueprint Capsule per type and each Scrap Egg stage.
 * A perched companion is not shown: perching pins a companion to a player's shoulder and cannot be staged.
 *
 * <p>The companions are display companions owned by the caller, frozen in their pose: no AI, no drain, never in the
 * Recovery Ledger and never counted toward caps. The build refuses when its space holds any block with contents.
 * {@code gallery clear} removes the companions and item frames and puts back exactly the blocks the build replaced.
 * The record lives for this server run only; nothing here names a player in a log or a file.
 * {@code GalleryCaptureHarness} on the client frames the same plan.
 */
public final class GalleryCommands {

    /** Other players within this many blocks stop a build or a clear. */
    public static final int OTHERS_RANGE = 96;

    private record Change(BlockPos pos, BlockState before) {
    }

    private record Gallery(ServerLevel level, BlockPos origin, List<Change> changes, Set<UUID> companions,
            Set<UUID> frames) {
    }

    private static final Map<String, Gallery> GALLERIES = new ConcurrentHashMap<>();

    private GalleryCommands() {
    }

    private static String key(ServerLevel level, BlockPos origin) {
        return level.dimension().identifier() + "@" + origin.asLong();
    }

    /** The plan for what this server has: types, looks and every item in the namespace. */
    static GalleryLayout.Plan plan(net.minecraft.server.MinecraftServer server) {
        List<Identifier> items = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            Identifier id = BuiltInRegistries.ITEM.getKey(item);
            if (id != null && NeroCompanionCommon.MOD_ID.equals(id.getNamespace())) {
                items.add(id);
            }
        }
        return GalleryLayout.plan(
                GalleryLayout.displays(ModEntities.COMPANION_TYPES, Looks.skins(server).values(),
                        Looks.hats(server).values()),
                GalleryLayout.wallItems(items, ModEntities.COMPANION_TYPES));
    }

    static int build(CommandSourceStack source) {
        ServerPlayer player = requireCreativeAlone(source);
        if (player == null) {
            return 0;
        }
        ServerLevel level = player.level();
        BlockPos origin = player.blockPosition();
        Gallery old = GALLERIES.remove(key(level, origin));
        if (old != null) {
            clear(old);
        }
        GalleryLayout.Plan plan = plan(source.getServer());
        int length = plan.length();
        int depth = plan.depth();
        int height = plan.height();
        int occupied = 0;
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(0, -1, 0),
                origin.offset(length - 1, height, depth - 1))) {
            if (level.getBlockEntity(pos) != null) {
                occupied++;
            }
        }
        if (occupied > 0) {
            int count = occupied;
            source.sendFailure(Component.translatable("command.nerocompanion.gallery.occupied", count));
            return 0;
        }
        List<Change> changes = new ArrayList<>();
        int wallZ = plan.wallZ() + 1;
        for (int dx = 0; dx < length; dx++) {
            for (int dz = 0; dz < depth; dz++) {
                boolean edge = dx == 0 || dz == 0 || dx == length - 1 || dz == depth - 1;
                place(level, origin.offset(dx, -1, dz),
                        (edge ? Blocks.POLISHED_BLACKSTONE : Blocks.POLISHED_DEEPSLATE).defaultBlockState(), changes);
                for (int dy = 0; dy < height; dy++) {
                    BlockState state = dz == wallZ ? Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState()
                            : Blocks.AIR.defaultBlockState();
                    place(level, origin.offset(dx, dy, dz), state, changes);
                }
            }
        }
        List<BlockState> chargers = List.of(ModBlocks.CHARGING_PAD.get().defaultBlockState(),
                ModBlocks.COMPANION_BAY.get().defaultBlockState(),
                ModBlocks.HAND_CRANK_CHARGER.get().defaultBlockState());
        for (int i = 0; i < chargers.size() && i < plan.chargers().size(); i++) {
            int[] at = plan.chargers().get(i);
            place(level, origin.offset(at[0], 0, at[1]), chargers.get(i), changes);
        }

        int missed = 0;
        Set<UUID> spawned = ConcurrentHashMap.newKeySet();
        for (GalleryLayout.Slot slot : plan.companions()) {
            if (!show(level, player, origin, slot, changes, spawned)) {
                missed++;
            }
        }
        Set<UUID> frames = ConcurrentHashMap.newKeySet();
        for (GalleryLayout.WallSlot slot : plan.wall()) {
            if (!hang(level, player, origin.offset(slot.dx(), slot.dy(), plan.wallZ()), slot.item(), frames)) {
                missed++;
            }
        }
        for (GalleryLayout.Label label : plan.labels()) {
            sign(level, origin.offset(label.dx(), 0, plan.wallZ()), label.group(), changes);
        }
        GALLERIES.put(key(level, origin), new Gallery(level, origin.immutable(), changes, spawned, frames));
        int companions = spawned.size();
        int items = frames.size();
        source.sendSuccess(() -> Component.translatable("command.nerocompanion.gallery.built", companions, items,
                length, depth), true);
        // Perching needs a player's shoulder, so it is never staged; anything else that failed is counted here.
        source.sendSuccess(() -> Component.translatable("command.nerocompanion.gallery.no_perch"), false);
        if (missed > 0) {
            int count = missed;
            source.sendFailure(Component.translatable("command.nerocompanion.gallery.missed", count));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static boolean show(ServerLevel level, ServerPlayer player, BlockPos origin, GalleryLayout.Slot slot,
            List<Change> changes, Set<UUID> spawned) {
        GalleryLayout.Display display = slot.display();
        BlockPos at = origin.offset(slot.dx(), 0, slot.dz());
        if (display.onPad()) {
            place(level, at, ModBlocks.CHARGING_PAD.get().defaultBlockState(), changes);
        }
        CompanionEntity companion = CompanionDeployer.deployDisplay(level, player, at,
                CompanionDeployer.freshRecord(level, display.type(), player), display.pose());
        if (companion == null) {
            return false;
        }
        spawned.add(companion.getUUID());
        companion.setMode(CompanionMode.STAY, player);
        companion.setLook(false, display.skin());
        companion.setLook(true, display.hat());
        if (!display.name().isEmpty()) {
            companion.setCompanionName(java.util.Optional.of(display.name()));
        }
        return true;
    }

    private static boolean hang(ServerLevel level, ServerPlayer player, BlockPos pos, GalleryLayout.WallItem item,
            Set<UUID> frames) {
        ItemStack stack = stackFor(level, player, item);
        if (stack.isEmpty()) {
            return false;
        }
        ItemFrame frame = new ItemFrame(level, pos, Direction.NORTH);
        frame.setItem(stack);
        if (!level.addFreshEntity(frame)) {
            return false;
        }
        frames.add(frame.getUUID());
        return true;
    }

    /** The stack a wall slot shows; empty when its item is not registered. */
    static ItemStack stackFor(ServerLevel level, ServerPlayer player, GalleryLayout.WallItem item) {
        return switch (item.kind()) {
            case ITEM -> item.item() == null ? ItemStack.EMPTY
                    : new ItemStack(BuiltInRegistries.ITEM.getValue(item.item()));
            case FILLED_CAPSULE -> {
                ItemStack capsule = new ItemStack(ModItems.CAPSULE.get());
                // The shareable part only (no owner), full charge, no ticket and nothing in the vault: a display.
                capsule.set(ModDataComponents.COMPANION.get(),
                        CompanionDeployer.freshRecord(level, item.type(), player).forNetwork());
                capsule.set(ModDataComponents.CHARGE.get(), 100);
                yield capsule;
            }
            case BLUEPRINT -> {
                ItemStack capsule = new ItemStack(ModItems.CAPSULE.get());
                capsule.set(ModDataComponents.BLUEPRINT.get(), item.type());
                yield capsule;
            }
            case EGG_STAGE -> ScrapEggItem.withTicks(new ItemStack(ModItems.SCRAP_EGG.get()), eggTicks(item.stage()));
        };
    }

    /** Incubation ticks that show egg stage {@code stage} (plain, cracked at half, glowing at 90 %). */
    static int eggTicks(int stage) {
        return stage <= 0 ? 0 : stage == 1 ? Incubation.HATCH_TICKS / 2 : Incubation.HATCH_TICKS * 9 / 10;
    }

    private static void sign(ServerLevel level, BlockPos pos, String group, List<Change> changes) {
        place(level, pos, Blocks.OAK_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING, Direction.NORTH),
                changes);
        if (level.getBlockEntity(pos) instanceof SignBlockEntity sign) {
            Component label = Component.translatable("gallery.nerocompanion.wall." + group);
            //? if >=26.3 {
            /*List<Component> lines = List.of(Component.empty(), label, Component.empty(), Component.empty());
            sign.setText(new SignText(lines, lines, net.minecraft.world.item.DyeColor.BLACK, false),
                    net.minecraft.world.level.block.entity.SignTextSlot.FRONT);
            *///?} else {
            sign.setText(new SignText().setMessage(1, label), true);
            //?}
            sign.setWaxed(true);
        }
    }

    private static void place(ServerLevel level, BlockPos pos, BlockState state, List<Change> changes) {
        BlockState before = level.getBlockState(pos);
        if (before.equals(state)) {
            return;
        }
        changes.add(new Change(pos.immutable(), before));
        level.setBlock(pos, state, Block.UPDATE_CLIENTS);
    }

    static int clear(CommandSourceStack source) {
        ServerPlayer player = requireCreativeAlone(source);
        if (player == null) {
            return 0;
        }
        ServerLevel level = player.level();
        Gallery nearest = null;
        double best = Double.MAX_VALUE;
        for (Gallery gallery : GALLERIES.values()) {
            double d = gallery.origin().distSqr(player.blockPosition());
            if (gallery.level() == level && d < best && d <= 128.0D * 128.0D) {
                best = d;
                nearest = gallery;
            }
        }
        if (nearest == null) {
            source.sendFailure(Component.translatable("command.nerocompanion.gallery.none"));
            return 0;
        }
        GALLERIES.remove(key(level, nearest.origin()));
        int removed = clear(nearest);
        source.sendSuccess(() -> Component.translatable("command.nerocompanion.gallery.cleared", removed), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int clear(Gallery gallery) {
        int removed = 0;
        for (UUID id : gallery.frames()) {
            Entity entity = gallery.level().getEntity(id);
            if (entity instanceof ItemFrame frame) {
                frame.discard();
            }
        }
        for (UUID id : gallery.companions()) {
            // An unloaded one finds no pose when it next loads and removes itself.
            GalleryDisplays.remove(id);
            Entity entity = gallery.level().getEntity(id);
            if (entity instanceof CompanionEntity companion) {
                if (companion.companionId() != null) {
                    // Never in the ledger; forgetting is a no-op kept for companions from older builds.
                    Recovery.forget(gallery.level().getServer(), companion.companionId());
                }
                companion.discard();
                removed++;
            }
        }
        List<Change> changes = gallery.changes();
        for (int i = changes.size() - 1; i >= 0; i--) {
            Change change = changes.get(i);
            gallery.level().setBlock(change.pos(), change.before(), Block.UPDATE_CLIENTS);
        }
        return removed;
    }

    private static ServerPlayer requireCreativeAlone(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null || !player.isCreative()) {
            source.sendFailure(Component.translatable("command.nerocompanion.debug.creative_only"));
            return null;
        }
        for (ServerPlayer other : source.getServer().getPlayerList().getPlayers()) {
            if (other != player && other.level() == player.level()
                    && other.distanceToSqr(player) <= (double) OTHERS_RANGE * OTHERS_RANGE) {
                source.sendFailure(Component.translatable("command.nerocompanion.gallery.others", OTHERS_RANGE));
                return null;
            }
        }
        return player;
    }

    /** Server stopped: the build records point at that server's levels. */
    public static void serverStopped() {
        GALLERIES.clear();
        GalleryDisplays.serverStopped();
    }
}
