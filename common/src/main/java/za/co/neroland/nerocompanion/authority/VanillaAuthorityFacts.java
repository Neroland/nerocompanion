package za.co.neroland.nerocompanion.authority;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.progression.ProgressionGates;

import za.co.neroland.nerocompanion.entity.CompanionEntity;
import za.co.neroland.nerocompanion.platform.Services;
import za.co.neroland.nerocompanion.registry.ModTags;

/**
 * {@link AuthorityFacts} answered from the live server, as the owner. The owner is the {@link ServerPlayer} for
 * the companion's stored owner UUID ({@code PlayerList.getPlayer}); never a nearby player, never a FakePlayer.
 * Each fact is computed on demand, so a chain that denies early never touches the rest.
 */
final class VanillaAuthorityFacts implements AuthorityFacts {

    private final CompanionEntity bot;
    private final ServerLevel level;
    @Nullable
    private final ServerPlayer owner;
    @Nullable
    private final BlockPos pos;
    @Nullable
    private final Entity target;

    VanillaAuthorityFacts(CompanionEntity bot, ServerLevel level, @Nullable BlockPos pos, @Nullable Entity target) {
        this.bot = bot;
        this.level = level;
        this.owner = bot.resolveOwner();
        this.pos = pos;
        this.target = target;
    }

    @Override
    public boolean ownerOnline() {
        return this.owner != null && !this.owner.isRemoved() && !Services.ENTITIES.isFakePlayer(this.owner);
    }

    @Override
    public boolean ownerSameDimension() {
        return this.owner != null && this.owner.level() == this.level;
    }

    @Override
    public OwnerMode ownerMode() {
        if (this.owner == null) {
            return OwnerMode.SPECTATOR;
        }
        if (this.owner.isSpectator()) {
            return OwnerMode.SPECTATOR;
        }
        if (this.owner.isCreative()) {
            return OwnerMode.CREATIVE;
        }
        // Adventure is the game mode whose abilities forbid building; the build check itself is ownerMayBuild.
        return this.owner.mayBuild() ? OwnerMode.SURVIVAL : OwnerMode.ADVENTURE;
    }

    @Override
    public boolean ownerMayBuild() {
        return this.owner != null && this.owner.mayBuild();
    }

    @Override
    public boolean spawnProtected() {
        return this.owner != null && this.pos != null
                && this.level.getServer().isUnderSpawnProtection(this.level, this.pos, this.owner);
    }

    @Override
    public boolean withinWorldBorder() {
        return this.pos == null || this.level.getWorldBorder().isWithinBounds(this.pos);
    }

    @Override
    public boolean ownerMayInteract() {
        return this.owner != null && this.pos != null && this.level.mayInteract(this.owner, this.pos);
    }

    @Override
    public float destroySpeed() {
        if (this.pos == null) {
            return -1.0F;
        }
        return this.level.getBlockState(this.pos).getDestroySpeed(this.level, this.pos);
    }

    @Override
    public boolean unmineable() {
        if (this.pos == null) {
            return true;
        }
        BlockState state = this.level.getBlockState(this.pos);
        return state.is(ModTags.UNMINEABLE);
    }

    @Override
    public boolean hasBlockEntity() {
        return this.pos != null && this.level.getBlockEntity(this.pos) != null;
    }

    @Override
    public boolean containerLocked() {
        if (this.pos == null || this.owner == null) {
            return true;
        }
        BlockEntity blockEntity = this.level.getBlockEntity(this.pos);
        if (blockEntity instanceof BaseContainerBlockEntity container) {
            // canOpen applies the vanilla lock (LockCode) against what the owner holds.
            return !container.canOpen(this.owner);
        }
        return false;
    }

    @Override
    public boolean gateOpen() {
        Identifier gate = this.bot.gateForCurrentMode();
        return gate == null || (this.owner != null && ProgressionGates.isOpen(this.owner, gate));
    }

    @Override
    public TargetKind targetKind() {
        Entity t = this.target;
        if (t == null || this.owner == null) {
            return TargetKind.NONE;
        }
        if (t == this.owner || t.getUUID().equals(this.owner.getUUID())) {
            return TargetKind.OWNER;
        }
        if (t.isAlliedTo(this.owner) || this.owner.isAlliedTo(t)) {
            return TargetKind.ALLY;
        }
        if (t instanceof CompanionEntity companion) {
            return this.isAggressorCompanion(companion) ? TargetKind.AGGRESSOR_COMPANION : TargetKind.OWNED_BY_SOMEONE;
        }
        if (isOwnedAnimal(t)) {
            return TargetKind.OWNED_BY_SOMEONE;
        }
        EntityType<?> type = t.getType();
        if (isVillagerOrGolem(type) || type.builtInRegistryHolder().is(ModTags.PROTECTED_MOBS)) {
            return TargetKind.PROTECTED_MOB;
        }
        if (t instanceof Player) {
            return TargetKind.PLAYER;
        }
        return TargetKind.OTHER;
    }

    /** The fixed protected set: not configurable, so not only a tag a datapack could replace. */
    private static boolean isVillagerOrGolem(EntityType<?> type) {
        //? if >=26.2 {
        return type == net.minecraft.world.entity.EntityTypes.VILLAGER
                || type == net.minecraft.world.entity.EntityTypes.WANDERING_TRADER
                || type == net.minecraft.world.entity.EntityTypes.IRON_GOLEM
                || type == net.minecraft.world.entity.EntityTypes.SNOW_GOLEM;
        //?} else {
        /*return type == EntityType.VILLAGER || type == EntityType.WANDERING_TRADER
                || type == EntityType.IRON_GOLEM || type == EntityType.SNOW_GOLEM;
        *///?}
    }

    /** Tamed pets, owned horses, any other ownable mob with an owner recorded - whoever that owner is. */
    private static boolean isOwnedAnimal(Entity entity) {
        if (entity instanceof TamableAnimal tamable && tamable.isTame()) {
            return true;
        }
        if (!(entity instanceof OwnableEntity ownable)) {
            return false;
        }
        //? if >=1.21.5 {
        return ownable.getOwnerReference() != null;
        //?} else {
        /*return ownable.getOwnerUUID() != null;
        *///?}
    }

    /**
     * Another owner's companion that hurt our owner or our bot (their last attacker) or is targeting either right now.
     * Never our own owner's companion.
     */
    private boolean isAggressorCompanion(CompanionEntity companion) {
        ServerPlayer us = this.owner;
        if (us == null || companion.ownerId() == null || companion.ownerId().equals(us.getUUID())) {
            return false;
        }
        LivingEntity companionTarget = companion.getTarget();
        return companion == us.getLastHurtByMob() || companion == this.bot.getLastHurtByMob()
                || companionTarget == us || companionTarget == this.bot;
    }

    /** The player whose PvP standing is judged: a player target itself, or an aggressor companion's owner. */
    @Nullable
    private Player judgedPlayer() {
        if (this.target instanceof Player player) {
            return player;
        }
        if (this.target instanceof CompanionEntity companion) {
            return companion.resolveOwner();
        }
        return null;
    }

    @Override
    public boolean targetPlayerInvulnerable() {
        Player player = this.judgedPlayer();
        // An aggressor companion whose owner is offline cannot be judged: treat it as protected.
        return player == null || player.isCreative() || player.isSpectator();
    }

    @Override
    public boolean ownerCanHarmTarget() {
        Player player = this.judgedPlayer();
        return this.owner != null && player != null && player != this.owner && this.owner.canHarmPlayer(player);
    }
}
