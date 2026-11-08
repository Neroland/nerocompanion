package za.co.neroland.nerocompanion.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.context.UseOnContext;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerocompanion.authority.Decision;
import za.co.neroland.nerocompanion.config.NeroCompanionConfig;
import za.co.neroland.nerocompanion.entity.CompanionEntity;
import za.co.neroland.nerocompanion.entity.CompanionRecord;
import za.co.neroland.nerocompanion.entity.type.CompanionType;
import za.co.neroland.nerocompanion.entity.type.CompanionTypes;
import za.co.neroland.nerocompanion.platform.Services;
import za.co.neroland.nerocompanion.recovery.Recovery;
import za.co.neroland.nerocompanion.registry.ModEntities;
import za.co.neroland.nerocompanion.scheduler.CapPolicy;
import za.co.neroland.nerocompanion.scheduler.CompanionIndex;
import za.co.neroland.nerocompanion.scheduler.RegionKey;

/**
 * The one deploy path (Capsule, spawn egg, debug spawn): the deployer becomes the owner, the caps and the
 * duplication guard are checked first, and the companion appears at the clicked face. Server only.
 */
public final class CompanionDeployer {

    private CompanionDeployer() {
    }

    /** Where a companion appears for a use-on-block: inside a collision-free clicked block, else on the face. */
    public static BlockPos spawnPos(UseOnContext context) {
        BlockPos clicked = context.getClickedPos();
        Direction face = context.getClickedFace();
        return context.getLevel().getBlockState(clicked).getCollisionShape(context.getLevel(), clicked).isEmpty()
                ? clicked : clicked.relative(face);
    }

    /** A brand-new record for a companion type, fully charged, in the type's default mode. */
    public static CompanionRecord freshRecord(ServerLevel level, net.minecraft.resources.Identifier typeId,
            ServerPlayer owner) {
        CompanionType type = CompanionTypes.get(level.getServer(), typeId).orElse(null);
        long capacity = type != null ? type.energy().capacity() : 24_000L;
        return CompanionRecord.fresh(typeId, owner.getUUID(),
                type != null ? type.defaultMode() : za.co.neroland.nerocompanion.entity.CompanionMode.FOLLOW,
                capacity, level.getRandom().nextLong());
    }

    /**
     * Places a creative-gallery display companion: a fresh {@code record} owned by {@code owner}, frozen in
     * {@code pose}. Not a deploy - no Recovery Ledger entry, no index or HUD entry, no caps, no milestones, no sound -
     * and it removes itself if it ever loads without its gallery (see {@code GalleryDisplays}).
     *
     * @return the companion, or null when it could not be added
     */
    @Nullable
    public static CompanionEntity deployDisplay(ServerLevel level, ServerPlayer owner, BlockPos pos,
            CompanionRecord record, za.co.neroland.nerocompanion.entity.GalleryPose pose) {
        CompanionType type = CompanionTypes.get(level.getServer(), record.type()).orElse(null);
        net.minecraft.resources.Identifier entityId = type != null ? type.entity() : record.type();
        EntityType<? extends CompanionEntity> entityType = ModEntities.entityType(entityId).orElse(null);
        if (entityType == null) {
            return null;
        }
        CompanionEntity companion = entityType.create(level, EntitySpawnReason.SPAWN_ITEM_USE);
        if (companion == null) {
            return null;
        }
        companion.snapTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 180.0F, 0.0F);
        companion.setYHeadRot(180.0F);
        companion.setYBodyRot(180.0F);
        companion.deploy(record, owner);
        companion.markGalleryDisplay(pose);
        if (!level.addFreshEntity(companion)) {
            return null;
        }
        za.co.neroland.nerocompanion.entity.GalleryDisplays.put(companion.getUUID(), pose);
        return companion;
    }

    /** The outcome of a deploy: the companion, or the reason (a lang key + argument) it was refused. */
    public record Result(@Nullable CompanionEntity companion, String reasonKey, String arg) {
        public boolean ok() {
            return this.companion != null;
        }
    }

    /**
     * Deploys {@code record} at {@code pos} for {@code owner}.
     *
     * @param bypassCaps only for the operator debug spawn
     * @return the companion, or null when refused (the owner has been told why)
     */
    @Nullable
    public static CompanionEntity deploy(ServerLevel level, ServerPlayer owner, BlockPos pos, CompanionRecord record,
            boolean bypassCaps) {
        return tryDeploy(level, owner, pos, record, bypassCaps).companion();
    }

    /** {@link #deploy} of a brand-new companion, also returning why a refusal happened. */
    public static Result tryDeploy(ServerLevel level, ServerPlayer owner, BlockPos pos, CompanionRecord record,
            boolean bypassCaps) {
        return tryDeploy(level, owner, pos, record, bypassCaps, Recovery.Source.FRESH);
    }

    /**
     * Deploys {@code record} from {@code source}: the Recovery Ledger must agree that this companion is where the
     * source says (a fresh id, the Capsule with the current ticket, the Bay that parked it) - otherwise the source is
     * inert and the owner is told why. Then the caps (read from the ledger, so they hold across restarts), then the
     * entity, then the ledger moves it to LIVE. All on the server thread: check and move are one step.
     */
    public static Result tryDeploy(ServerLevel level, ServerPlayer owner, BlockPos pos, CompanionRecord record,
            boolean bypassCaps, Recovery.Source source) {
        if (Services.ENTITIES.isFakePlayer(owner)) {
            return new Result(null, "gui.nerocompanion.reason.not_owner", "");
        }
        String stale = Recovery.check(level.getServer(), source, record.companionId());
        if (stale != null) {
            owner.sendSystemMessage(stale.startsWith("authority.") ? Component.translatable(
                    "message.nerocompanion.deploy_refused", Component.translatable(stale))
                    : Component.translatable(stale), true);
            return new Result(null, stale, "");
        }
        CompanionType type = CompanionTypes.get(level.getServer(), record.type()).orElse(null);
        net.minecraft.resources.Identifier entityId = type != null ? type.entity() : record.type();
        EntityType<? extends CompanionEntity> entityType = ModEntities.entityType(entityId).orElse(null);
        if (entityType == null) {
            owner.sendSystemMessage(Component.translatable("message.nerocompanion.unknown_type",
                    record.type().toString()), true);
            return new Result(null, "message.nerocompanion.unknown_type", record.type().toString());
        }
        if (!bypassCaps) {
            RegionKey region = RegionKey.ofBlock(level.dimension().identifier().toString(), pos.getX(), pos.getZ());
            Decision caps = CapPolicy.canDeploy(Recovery.caps(level.getServer()), owner.getUUID(),
                    record.companionId(), region, NeroCompanionConfig.limits());
            if (caps.denied()) {
                owner.sendSystemMessage(Component.translatable("message.nerocompanion.deploy_refused",
                        Component.translatable(caps.reasonKey())), true);
                return switch (caps.reasonKey()) {
                    case "authority.nerocompanion.player_cap" -> new Result(null, "gui.nerocompanion.reason.cap",
                            String.valueOf(NeroCompanionConfig.limits().maxCompanionsPerPlayer()));
                    case "authority.nerocompanion.region_cap" -> new Result(null,
                            "gui.nerocompanion.reason.region_full", "");
                    default -> new Result(null, caps.reasonKey(), "");
                };
            }
        }
        CompanionEntity companion = entityType.create(level, EntitySpawnReason.SPAWN_ITEM_USE);
        if (companion == null) {
            return new Result(null, "gui.nerocompanion.reason.not_live", "");
        }
        companion.snapTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, owner.getYRot(), 0.0F);
        companion.deploy(record, owner);
        if (!level.addFreshEntity(companion)) {
            return new Result(null, "gui.nerocompanion.reason.not_live", "");
        }
        CompanionIndex.forServer(level.getServer()).touch(record.companionId(), owner.getUUID(), companion.getUUID(),
                companion.region());
        Recovery.deployed(companion);
        // Milestones for the deployer (the owner): first deploy, and full squad once the LIVE count reaches the cap.
        za.co.neroland.nerocompanion.progression.CompanionProgress.deployed(level.getServer(), owner.getUUID(),
                Recovery.caps(level.getServer()).liveFor(owner.getUUID(), null),
                NeroCompanionConfig.limits().maxCompanionsPerPlayer());
        level.playSound(null, companion.getX(), companion.getY(), companion.getZ(), za.co.neroland.nerocompanion.registry.ModSounds.DEPLOY.get(),
                net.minecraft.sounds.SoundSource.NEUTRAL, 0.7F, za.co.neroland.nerocompanion.registry.ModSounds.pitch(level.getRandom()));
        return new Result(companion, "", "");
    }
}
