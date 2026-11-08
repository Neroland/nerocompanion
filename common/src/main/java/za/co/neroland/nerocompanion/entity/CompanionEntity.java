package za.co.neroland.nerocompanion.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.InteractionResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerocompanion.NeroCompanionCommon;
import za.co.neroland.nerocompanion.ai.ModeController;
import za.co.neroland.nerocompanion.budget.BudgetProbe;
import za.co.neroland.nerocompanion.chip.ChipEffects;
import za.co.neroland.nerocompanion.chip.ChipItem;
import za.co.neroland.nerocompanion.chip.ChipKind;
import za.co.neroland.nerocompanion.chip.ChipRules;
import za.co.neroland.nerocompanion.chip.ChipSlots;
import za.co.neroland.nerocompanion.chip.ChipValidation;
import za.co.neroland.nerocompanion.hud.HudBoard;
import za.co.neroland.nerocompanion.item.RemoteItem;
import za.co.neroland.nerocompanion.network.payload.Payloads;
import za.co.neroland.nerocompanion.config.NeroCompanionConfig;
import za.co.neroland.nerocompanion.energy.CompanionEnergy;
import za.co.neroland.nerocompanion.entity.type.CompanionType;
import za.co.neroland.nerocompanion.entity.type.CompanionTypes;
import za.co.neroland.nerocompanion.item.CapsuleItem;
import za.co.neroland.nerocompanion.job.CompanionJob;
import za.co.neroland.nerocompanion.job.JobCodecs;
import za.co.neroland.nerocompanion.recovery.Recovery;
import za.co.neroland.nerocompanion.recovery.RecoveryBook;
import za.co.neroland.nerocompanion.recovery.ReclaimRecords;
import za.co.neroland.nerocompanion.salvage.Salvage;
import za.co.neroland.nerocompanion.alert.AlertText;
import za.co.neroland.nerocompanion.alert.CompanionAlerts;
import za.co.neroland.nerocompanion.job.JobData;
import za.co.neroland.nerocompanion.personality.PersonalityHooks;
import za.co.neroland.nerocompanion.platform.Services;
import za.co.neroland.nerocompanion.registry.ModDataComponents;
import za.co.neroland.nerocompanion.registry.ModItems;
import za.co.neroland.nerocompanion.scheduler.CompanionIndex;
import za.co.neroland.nerocompanion.scheduler.CompanionLimits;
import za.co.neroland.nerocompanion.scheduler.CompanionScheduler;
import za.co.neroland.nerocompanion.scheduler.RegionKey;
import za.co.neroland.nerocompanion.scheduler.Throttle;

/**
 * The one companion base entity. Holds owner, type, mode, energy, chips, bond, name, skin, hat,
 * personality seed and home; concrete classes differ only in locomotion ({@link FlyingCompanion},
 * {@link GroundCompanion}). Deliberately not {@code TamableAnimal} and not {@code OwnableEntity}: the owner is a
 * plain UUID set once on deploy by the deploying player - never re-derived, never from proximity, never synched.
 *
 * <p>Persistence goes through {@link CompanionRecord} only (key {@value #RECORD_KEY}). The scheduler
 * ({@link CompanionScheduler}) runs once every 20 ticks per companion: it resolves the owner, applies the
 * throttle (full / every 4th tick / forced SLEEP), drains energy, swaps goal sets through {@link ModeController}
 * and re-indexes the companion for the caps.
 *
 * <p>Privacy: the owner UUID is stored in the entity's own save data (as vanilla does for a tamed animal) and
 * is never sent to clients, logged, or put in telemetry.
 *
 * <p><b>Per-type classes</b> extend {@link FlyingCompanion} or {@link GroundCompanion} with the constructor
 * {@code (EntityType<? extends X>, Level)} and override only the hooks below: {@link #onWorkTick()},
 * {@link #onInteractOwner}, {@link #onLeaveWorld}. Work state lives in {@link #job()} (codec-persisted),
 * {@link #cargo()} and {@link #boundContainer()}, all stored inside {@link CompanionRecord#jobData()} (layout:
 * {@link JobData}); work goals come from {@code ai.work.WorkGoals}; personality from {@link PersonalityHooks}.
 */
public abstract class CompanionEntity extends PathfinderMob {

    public static final String RECORD_KEY = "nerocompanion";

    /** Flag bits in {@link #DATA_FLAGS}. */
    public static final byte FLAG_PERCHED = 1;
    public static final byte FLAG_SLEEPING = 2;
    public static final byte FLAG_WORKING = 4;
    public static final byte FLAG_NAME_HIDDEN = 8;
    /** A nap pose (Scrapling on its pad, a sleepy trait nap): pose only, not a mode; the AI keeps running. */
    public static final byte FLAG_NAPPING = 16;

    /** One-shot animation impulses ({@code 019} section 4.4), sent as entity events {@code IMPULSE_EVENT + n}. */
    public static final int IMPULSE_EMOTE = 0;
    public static final int IMPULSE_PET = 1;
    public static final int IMPULSE_TRICK = 2;
    public static final int IMPULSE_STOMP = 3;
    public static final int IMPULSE_PICKUP = 4;
    private static final byte IMPULSE_EVENT = 90;
    private static final int IMPULSES = 5;

    /** Number of personality traits (curious, loyal, grumpy, sleepy, showoff, shy). */
    public static final int TRAITS = 6;

    /** Ticks after unperching before the companion will perch again. */
    private static final int PERCH_COOLDOWN = 200;

    private static final EntityDataAccessor<Byte> DATA_MODE =
            SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> DATA_ENERGY_PCT =
            SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<String> DATA_SKIN =
            SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> DATA_HAT =
            SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Byte> DATA_BOND_LEVEL =
            SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> DATA_TRAIT =
            SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> DATA_FLAGS =
            SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.BYTE);
    /** Network id of the owner while perched, else -1. Session-local, not personal data. */
    private static final EntityDataAccessor<Integer> DATA_OWNER_ENTITY_ID =
            SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.INT);

    // --- persisted (mirrors CompanionRecord) ---------------------------------------------------------
    @Nullable
    private UUID companionId;
    @Nullable
    private UUID ownerId;
    @Nullable
    private Identifier typeId;
    private CompanionMode mode = CompanionMode.FOLLOW;
    private final CompanionEnergy energy = new CompanionEnergy(1_000_000_000_000L, 0L);
    private List<ChipStack> chips = List.of();
    /**
     * The chips that are valid together on this chassis (slot count, category, duplicates, exclusive pairs): the
     * only ones any effect reads. A record carrying more (creative copies, a later slot change) keeps the rest in
     * {@link #chips} until they can be handed back to an online owner - never silently deleted.
     */
    private List<ChipStack> activeChips = List.of();
    private int bondPoints;
    private Optional<String> name = Optional.empty();
    private Optional<Identifier> skin = Optional.empty();
    private Optional<Identifier> hat = Optional.empty();
    private long personalitySeed;
    private Optional<HomePos> home = Optional.empty();
    /**
     * The record's job data as last read or written. The job, bound container and cargo below are decoded from it
     * on apply and encoded back into a copy on {@link #toRecord()}; keys nobody here knows survive unchanged.
     */
    private CompoundTag jobData = new CompoundTag();
    private CompanionJob job = CompanionJob.none();
    /** The stored job could not be read (unknown type / bad value): write its keys back unchanged until replaced. */
    private boolean keepRawJob;
    private Optional<HomePos> boundContainer = Optional.empty();
    /** Cargo read from the record, waiting for the container (built once the chassis size is known). */
    private List<ItemStack> storedCargo = List.of();
    @Nullable
    private SimpleContainer cargo;

    // --- server runtime ------------------------------------------------------------------------------
    @Nullable
    private CompanionType type;
    private int typeGeneration = -1;
    /** Health from the record, applied once the type has set the max ({@code FULL_HEALTH} = full / none pending). */
    private float pendingHealth = CompanionRecord.FULL_HEALTH;
    private boolean healthPending;
    @Nullable
    private ModeController modeController;
    private Throttle throttle = new Throttle(Throttle.State.FULL, Throttle.Reason.NEAR_OWNER);
    @Nullable
    private ServerPlayer ownerCache;
    @Nullable
    private Vec3 stayAnchor;
    private int perchCooldown;
    private boolean needsEvaluation = true;
    /** Milestones already reported by this entity since it loaded (transient: the gates themselves are idempotent). */
    private boolean chargeNoted;
    private final java.util.EnumSet<za.co.neroland.nerocompanion.progression.CompanionProgress.Work> workNoted =
            java.util.EnumSet.noneOf(za.co.neroland.nerocompanion.progression.CompanionProgress.Work.class);
    /** Off-world flight speed factor last applied (1.0 on Earth; see compat.nerospace.OffWorldRules). */
    private double flightFactor = 1.0D;

    /** Game time until which the companion counts as charging, and the rate it was last charged at. */
    private long chargingUntil = Long.MIN_VALUE;
    private long chargingFePerTick;
    /** Installed chips as a Core upgrade container (rebuilt from {@link #chips}). */
    @Nullable
    private ChipSlots chipSlots;
    /** Game time of the last energy drain; drains stay on a 20-tick clock even when evaluations are forced. */
    private long lastDrainTick = Long.MIN_VALUE;
    /** Set at the start of each server {@link #aiStep()}: this tick's AI step is skipped by the throttle. */
    private boolean aiSkipped;
    /**
     * The saved record when it could not be decoded: kept and written back unchanged, and the companion stays
     * inert (asleep, unrecallable, no new identity) instead of becoming a silently ownerless copy.
     */
    @Nullable
    private CompoundTag brokenRecord;
    /** Something the ledger snapshot shows changed (mode, name, chips): write it at the next evaluation. */
    private boolean ledgerDirty;
    /** Leaving the world on the ledger's behalf (stale duplicate, erasure unbind): no ledger transition on remove. */
    private boolean leavingQuietly;
    /** A creative-gallery display companion (saved, so a reload finds its pose again or removes itself). */
    private boolean galleryDisplay;
    /** The gallery pose: transient, looked up again in {@link GalleryDisplays} after a reload. */
    @Nullable
    private GalleryPose galleryPose;
    /** Save key of the gallery-display marker. */
    private static final String GALLERY_KEY = "nerocompanion_gallery";

    /** One WARN per server run for undecodable records (no personal data in it). */
    private static final AtomicBoolean BROKEN_WARNED = new AtomicBoolean();
    /** One WARN per server run for an unreadable job (job type only, never the data). */
    private static final AtomicBoolean JOB_WARNED = new AtomicBoolean();

    protected CompanionEntity(EntityType<? extends CompanionEntity> entityType, Level level) {
        super(entityType, level);
    }

    /** How this chassis moves. */
    public abstract Locomotion locomotion();

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_MODE, (byte) CompanionMode.FOLLOW.ordinal());
        builder.define(DATA_ENERGY_PCT, (byte) 0);
        builder.define(DATA_SKIN, "");
        builder.define(DATA_HAT, "");
        builder.define(DATA_BOND_LEVEL, (byte) 0);
        builder.define(DATA_TRAIT, (byte) 0);
        builder.define(DATA_FLAGS, (byte) 0);
        builder.define(DATA_OWNER_ENTITY_ID, -1);
    }

    /** Goals are installed per mode by {@link ModeController}, not here (fields are not set yet in this call). */
    @Override
    protected void registerGoals() {
    }

    // =================================================================================================
    // Identity, ownership and the record
    // =================================================================================================

    @Nullable
    public UUID companionId() {
        return this.companionId;
    }

    /** The owner's UUID (server only; never synched, never logged). */
    @Nullable
    public UUID ownerId() {
        return this.ownerId;
    }

    /** Server: whether {@code player} deployed this companion. Always false on the client (no owner there). */
    public boolean isOwnedBy(Player player) {
        return this.ownerId != null && this.ownerId.equals(player.getUUID())
                && !Services.ENTITIES.isFakePlayer(player);
    }

    /**
     * The owner as an online server player in any dimension, looked up by UUID. Null when offline, unknown, or
     * a loader FakePlayer.
     */
    @Nullable
    public ServerPlayer resolveOwner() {
        if (this.ownerId == null || !(this.level() instanceof ServerLevel server)) {
            return null;
        }
        ServerPlayer player = server.getServer().getPlayerList().getPlayer(this.ownerId);
        if (player == null || Services.ENTITIES.isFakePlayer(player)) {
            return null;
        }
        return player;
    }

    /** The owner cached at the last evaluation, if still valid. Cheap; goals call this every tick. */
    @Nullable
    public ServerPlayer owner() {
        ServerPlayer cached = this.ownerCache;
        if (cached == null || cached.isRemoved()) {
            return null;
        }
        return cached;
    }

    /** Whether the owner is online and in this companion's level. */
    public boolean ownerHere() {
        ServerPlayer owner = this.owner();
        return owner != null && owner.level() == this.level();
    }

    public Identifier typeId() {
        if (this.typeId == null) {
            this.typeId = BuiltInRegistries.ENTITY_TYPE.getKey(this.getType());
        }
        return this.typeId;
    }

    /** Snapshot of everything this companion is. */
    public CompanionRecord toRecord() {
        UUID id = this.companionId != null ? this.companionId : UUID.randomUUID();
        this.companionId = id;
        this.jobData = this.encodeJobData();
        return new CompanionRecord(CompanionRecord.SCHEMA, id,
                this.ownerId != null ? this.ownerId : CompanionRecord.NO_OWNER, this.typeId(), this.mode,
                this.energy.stored(), this.chips, this.bondPoints, this.name, this.skin, this.hat,
                this.personalitySeed, this.home, this.jobData, this.recordHealth());
    }

    /** Health for the record: full unless hurt; a dead companion records full (a reclaim comes back repaired). */
    private float recordHealth() {
        float health = this.getHealth();
        return health <= 0.0F || health >= this.getMaxHealth() ? CompanionRecord.FULL_HEALTH : health;
    }

    private com.mojang.serialization.DynamicOps<Tag> jobOps() {
        return this.registryAccess().createSerializationContext(NbtOps.INSTANCE);
    }

    /** The job data with the live job, bound container and cargo written in. */
    private CompoundTag encodeJobData() {
        CompoundTag out = this.jobData.copy();
        com.mojang.serialization.DynamicOps<Tag> ops = this.jobOps();
        if (!this.keepRawJob) {
            JobCodecs.write(out, this.job, ops);
        }
        JobData.writeBound(out, this.boundContainer);
        JobData.writeCargo(out, this.cargoList(), ItemStack::isEmpty, ItemStack.CODEC, ops);
        return out;
    }

    /** Decodes job, bound container and cargo from {@link #jobData}. Never throws. */
    private void decodeJobData() {
        com.mojang.serialization.DynamicOps<Tag> ops = this.jobOps();
        JobCodecs.Read read = JobCodecs.read(this.jobData, ops);
        this.job = read.job();
        this.keepRawJob = read.keepRaw();
        read.problem().ifPresent(problem -> {
            if (JOB_WARNED.compareAndSet(false, true)) {
                NeroCompanionCommon.LOGGER.warn("[NeroCompanion] a companion's job could not be read ({}); it is kept "
                        + "unchanged and the companion starts without a job", problem);
            }
        });
        this.boundContainer = JobData.readBound(this.jobData);
        this.storedCargo = JobData.readCargo(this.jobData, ItemStack.CODEC, ops, ItemStack.EMPTY);
        this.cargo = null;
    }

    /** Restores everything from a record (load, deploy). */
    public void applyRecord(CompanionRecord record) {
        this.companionId = record.companionId();
        this.ownerId = CompanionRecord.NO_OWNER.equals(record.ownerId()) ? null : record.ownerId();
        this.typeId = record.type();
        this.mode = record.mode();
        this.energy.setStored(record.energyFe());
        // The alert flag is not persisted: a companion loaded already low counts as already alerted.
        this.energy.restoreAlertState(CompanionEnergy.DEFAULT_LOW_PERCENT);
        this.chips = new ArrayList<>(record.chips());
        this.chipSlots = null;
        this.bondPoints = record.bondPoints();
        this.name = record.name();
        this.skin = record.skin();
        this.hat = record.hat();
        this.personalitySeed = record.personalitySeed();
        this.home = record.home();
        this.jobData = record.jobData();
        this.decodeJobData();
        // Applied in resolveType, after the chassis (and chips) set the max: a hurt companion stays hurt through
        // recall, Bay parking and reclaim.
        this.pendingHealth = record.health();
        this.healthPending = true;
        // Decide name visibility before the vanilla custom name is set: the entity is tracked (and may be saved)
        // before its first evaluation, so a hidden name must never be published even for a tick.
        boolean serverSide = this.level() instanceof ServerLevel;
        net.minecraft.server.MinecraftServer server = serverSide ? ((ServerLevel) this.level()).getServer() : null;
        this.setFlag(FLAG_NAME_HIDDEN, za.co.neroland.nerocompanion.privacy.CompanionPrivacy.hideOnApply(serverSide,
                server != null && server.isSameThread(),
                () -> za.co.neroland.nerocompanion.privacy.CompanionPrivacy.namesShown(server, this.ownerId)));
        this.syncCustomName();
        this.typeGeneration = -1;
        this.needsEvaluation = true;
    }

    /**
     * Binds this (not yet added) entity to {@code owner} from {@code record}: the deployer becomes the owner
     *. The caller has already checked the caps.
     */
    public void deploy(CompanionRecord record, ServerPlayer owner) {
        this.applyRecord(record.forDeployer(owner.getUUID()));
        this.setPersistenceRequired();
        this.ownerCache = owner;
        this.stayAnchor = null;
    }

    /**
     * Recalls this companion into a Capsule: into {@code emptyCapsule} if given (the held one), else a new
     * Capsule into the player's inventory. Everything is preserved through the record. Owner only - callers
     * check {@link #isOwnedBy} first.
     */
    public void recall(Player player, @Nullable ItemStack emptyCapsule) {
        if (this.galleryDisplay) {
            return; // A display companion is never recalled; gallery clear removes it.
        }
        // Cargo never travels in a Capsule: it goes to the recalling player first (inventory, else their feet).
        for (ItemStack stack : this.takeAllCargo()) {
            giveTo(player, stack);
        }
        CompanionRecord record = this.toRecord();
        ItemStack capsule = emptyCapsule != null ? emptyCapsule : new ItemStack(ModItems.CAPSULE.get());
        if (this.level() instanceof ServerLevel server) {
            // LIVE -> CAPSULE: the new Capsule's ticket is the only one that may bring this companion out again.
            long ticket = Recovery.recalled(server.getServer(), record, this.getUUID());
            if (ticket == 0L) {
                // The ledger says this entity is not the companion (a stale copy): no Capsule, it just leaves.
                if (player instanceof ServerPlayer serverPlayer) {
                    serverPlayer.sendSystemMessage(Component.translatable(RecoveryBook.STALE_RECORD), true);
                }
                this.leavingQuietly = true;
                this.discard();
                return;
            }
            CapsuleItem.store(server.getServer(), capsule, record, this.energy.percent(), ticket);
            server.playSound(null, this.getX(), this.getY(), this.getZ(), za.co.neroland.nerocompanion.registry.ModSounds.RECALL.get(),
                    net.minecraft.sounds.SoundSource.NEUTRAL, 0.7F, za.co.neroland.nerocompanion.registry.ModSounds.pitch(this.getRandom()));
        }
        if (emptyCapsule == null) {
            // Into the inventory, or dropped at the player's feet when it is full.
            //? if >=26.3 {
            /*player.getInventory().placeItemBackInInventory(capsule, net.minecraft.util.Prediction.SERVER_ONLY);
            *///?} else {
            player.getInventory().placeItemBackInInventory(capsule);
            //?}
        }
        if (this.level() instanceof ServerLevel server && this.companionId != null) {
            CompanionIndex.forServer(server.getServer()).remove(this.companionId, this.getUUID());
            HudBoard.forServer(server.getServer()).remove(this.companionId);
        }
        this.leavingQuietly = true;
        this.discard();
    }

    /** Carry bots salvage their owner's death drops without a chip; others need the Salvage Chip. */
    public boolean salvagesByDefault() {
        return false;
    }

    /**
     * A second copy of a companion the ledger knows is elsewhere: what it carries (cargo, a Fetch
     * Ball) drops here - the older entity's load wins - and it leaves without touching the ledger.
     */
    public void discardAsStale() {
        if (!(this.level() instanceof ServerLevel)) {
            return;
        }
        for (ItemStack stack : this.takeAllCargo()) {
            this.dropAtSelf(stack);
        }
        this.leavingQuietly = true;
        this.onLeaveWorld(Entity.RemovalReason.KILLED);
        super.remove(Entity.RemovalReason.DISCARDED);
    }

    /**
     * Erasure: this companion's owner was erased. It becomes an ownerless Capsule - name cleared,
     * bond reset, home and job data dropped - dropped at its home when that is loaded here, else where it stands;
     * its cargo drops beside it. Leaves without touching the ledger (already cleared).
     */
    public void unbindForErasure() {
        if (!(this.level() instanceof ServerLevel server) || this.isRemoved()) {
            return;
        }
        for (ItemStack stack : this.takeAllCargo()) {
            this.dropAtSelf(stack);
        }
        CompanionRecord erased = ReclaimRecords.erased(this.toRecord());
        ItemStack capsule = new ItemStack(ModItems.CAPSULE.get());
        CapsuleItem.store(server.getServer(), capsule, erased, this.energy.percent(), 0L);
        double x = this.getX();
        double y = this.getY() + 0.25D;
        double z = this.getZ();
        if (this.home.isPresent() && this.home.get().isIn(server) && server.isLoaded(this.home.get().pos())) {
            BlockPos at = this.home.get().pos();
            x = at.getX() + 0.5D;
            y = at.getY() + 1.0D;
            z = at.getZ() + 0.5D;
        }
        ItemEntity item = new ItemEntity(server, x, y, z, capsule);
        item.setDefaultPickUpDelay();
        server.addFreshEntity(item);
        if (this.companionId != null) {
            CompanionIndex.forServer(server.getServer()).remove(this.companionId, this.getUUID());
            HudBoard.forServer(server.getServer()).remove(this.companionId);
        }
        this.leavingQuietly = true;
        this.discard();
    }

    // =================================================================================================
    // Type, mode and state accessors
    // =================================================================================================

    /** The resolved type (server; null until the first evaluation). */
    @Nullable
    public CompanionType typeDef() {
        return this.type;
    }

    public boolean isSmallChassis() {
        return this.type != null && this.type.small();
    }

    /** The player-chosen mode (persisted). */
    public CompanionMode mode() {
        return this.mode;
    }

    /** The mode the AI is running: {@link CompanionMode#SLEEP} while the scheduler forces sleep. */
    public CompanionMode effectiveMode() {
        return this.throttle.sleeping() ? CompanionMode.SLEEP : this.mode;
    }

    /** The mode shown to clients (synched). */
    public CompanionMode syncedMode() {
        return CompanionMode.byOrdinal(this.entityData.get(DATA_MODE));
    }

    /**
     * Sets the player-chosen mode on behalf of {@code actor}. Server only. Ownership is checked here, not by the
     * caller (screen, Remote, command), so a forgotten check cannot become a stranger's mode change.
     *
     * @return false if {@code actor} is not the owner, the companion is inert, or the mode is refused
     */
    public boolean setMode(CompanionMode newMode, ServerPlayer actor) {
        return this.trySetMode(newMode, actor, false).ok();
    }

    /** Whether this companion may use {@code mode}: its chassis lists it, or an installed chip unlocks it. */
    public boolean supportsMode(CompanionMode mode) {
        if (mode == CompanionMode.SLEEP) {
            return true;
        }
        boolean chassis = this.type == null || this.type.supports(mode);
        return chassis || ChipEffects.unlockedModes(this.activeChips).contains(mode);
    }

    /**
     * Why {@code mode} can't be chosen right now (a reason lang key plus its argument), or null. Shared by the
     * screen snapshot (disabled buttons) and {@link #trySetMode} (denials), so both always agree.
     */
    @Nullable
    public Payloads.Feedback modeDenial(CompanionMode mode) {
        if (!this.supportsMode(mode)) {
            java.util.Optional<ChipKind> chip = ChipEffects.unlockerOf(mode);
            if (chip.isPresent() && this.type != null && this.type.chipCategories().contains(chip.get().category())) {
                return Payloads.Feedback.deny("gui.nerocompanion.reason.chip",
                        "@item.nerocompanion." + chip.get().itemName(1));
            }
            return Payloads.Feedback.deny("gui.nerocompanion.reason.type",
                    "@entity." + this.typeId().getNamespace() + "." + this.typeId().getPath());
        }
        if (mode == CompanionMode.SLEEP) {
            return null;
        }
        if (this.energy.isEmpty()) {
            return Payloads.Feedback.deny("gui.nerocompanion.reason.empty");
        }
        if (mode.isActiveDrain() && this.energy.isLow(NeroCompanionConfig.limits().lowBatteryPercent())) {
            return Payloads.Feedback.deny("gui.nerocompanion.reason.low_battery");
        }
        return null;
    }

    /**
     * Sets the mode for {@code actor} (owner only). {@code comeHere} (FOLLOW only) also clears the stay point and,
     * when the owner is more than 16 blocks away in the same dimension, moves the companion to a safe spot beside
     * them.
     */
    public Payloads.Feedback trySetMode(CompanionMode newMode, ServerPlayer actor, boolean comeHere) {
        if (!(this.level() instanceof ServerLevel) || !this.isOwnedBy(actor)) {
            return Payloads.Feedback.deny("gui.nerocompanion.reason.not_owner");
        }
        if (this.brokenRecord != null) {
            return Payloads.Feedback.deny("gui.nerocompanion.reason.not_live");
        }
        if (comeHere && newMode != CompanionMode.FOLLOW) {
            return Payloads.Feedback.deny("gui.nerocompanion.reason.type", "@mode.nerocompanion." + newMode.id());
        }
        Payloads.Feedback denial = this.modeDenial(newMode);
        if (denial != null) {
            return denial;
        }
        if (newMode != this.mode) {
            this.mode = newMode;
            this.stayAnchor = null;
            this.needsEvaluation = true;
            this.ledgerDirty = true;
        }
        if (comeHere) {
            this.stayAnchor = null;
            if (this.isPerched()) {
                this.stopPerch();
            }
            if (actor.level() == this.level() && this.distanceToSqr(actor) > 16.0D * 16.0D) {
                this.relocateNear(actor.blockPosition(), 1);
            }
            this.needsEvaluation = true;
        }
        return Payloads.Feedback.ok("gui.nerocompanion.saved");
    }

    /**
     * The vanilla custom name - the only copy of the name every nearby client receives - is the companion's name
     * unless names are hidden ({@code privacy.allowNames} off, or the owner's {@code /nscomp privacy names off}); then
     * it is unset, so no other client is sent the name at all and none is written to the region file.
     */
    private void syncCustomName() {
        boolean hidden = (this.entityData.get(DATA_FLAGS) & FLAG_NAME_HIDDEN) != 0;
        this.setCustomName(hidden ? null : this.name.map(Component::literal).orElse(null));
        this.setCustomNameVisible(false);
    }

    /** Server, once a second: applies the owner's and the server's name visibility. */
    private void applyNamePrivacy(boolean shown) {
        boolean hidden = (this.entityData.get(DATA_FLAGS) & FLAG_NAME_HIDDEN) != 0;
        if (hidden == shown) {
            this.setFlag(FLAG_NAME_HIDDEN, !shown);
            this.syncCustomName();
        }
    }

    /** Server: sets (or clears) the already-filtered, capped name. */
    public void setCompanionName(java.util.Optional<String> newName) {
        this.name = newName.map(CompanionRecord::capName).filter(value -> !value.isEmpty());
        this.syncCustomName();
        this.needsEvaluation = true;
        this.ledgerDirty = true;
    }

    public java.util.Optional<String> companionName() {
        return this.name;
    }

    public int bondPoints() {
        return this.bondPoints;
    }

    public java.util.Optional<Identifier> skin() {
        return this.skin;
    }

    public java.util.Optional<Identifier> hat() {
        return this.hat;
    }

    /**
     * Server: wears a look ({@link za.co.neroland.nerocompanion.look.LookService} has checked the owner, the type and
     * the unlock). Empty = the standard skin / no hat. Persisted with the record; synched at once.
     */
    public void setLook(boolean isHat, java.util.Optional<Identifier> id) {
        if (isHat) {
            this.hat = id;
            this.entityData.set(DATA_HAT, id.map(Identifier::toString).orElse(""));
        } else {
            this.skin = id;
            this.entityData.set(DATA_SKIN, id.map(Identifier::toString).orElse(""));
        }
        this.needsEvaluation = true;
        this.ledgerDirty = true;
    }

    /** Server: plays a one-shot animation impulse on every watching client (no data beyond the entity id). */
    public void impulse(int impulse) {
        if (impulse >= 0 && impulse < IMPULSES && this.level() instanceof ServerLevel server) {
            server.broadcastEntityEvent(this, (byte) (IMPULSE_EVENT + impulse));
        }
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id >= IMPULSE_EVENT && id < IMPULSE_EVENT + IMPULSES) {
            za.co.neroland.nerocompanion.ClientBridge.get().onImpulse(this.getId(), id - IMPULSE_EVENT);
            return;
        }
        super.handleEntityEvent(id);
    }

    /** The synched skin id ({@code ""} = the standard skin). */
    public String syncedSkin() {
        return this.entityData.get(DATA_SKIN);
    }

    /** The synched hat id ({@code ""} = no hat). */
    public String syncedHat() {
        return this.entityData.get(DATA_HAT);
    }

    // --- chips ---------------------------------------------------------------------------------------

    /** What this chassis offers chips: slot count and accepted categories. */
    public ChipValidation.Chassis chassis() {
        CompanionType def = this.type;
        if (def == null) {
            def = CompanionType.defaults(this.typeId(), BuiltInRegistries.ENTITY_TYPE.getKey(this.getType()),
                    this.locomotion());
        }
        return new ChipValidation.Chassis(def.chipSlots(), def.chipCategories());
    }

    /** The installed chips as a Core upgrade container. */
    public ChipSlots chipSlots() {
        if (this.chipSlots == null) {
            this.chipSlots = ChipSlots.of(this.chassis().slots(), this.activeChips,
                    (kind, tier) -> za.co.neroland.nerocompanion.registry.ModItems.chip(kind, tier));
        }
        return this.chipSlots;
    }

    /**
     * Server: installs {@code chip} into {@code slot} ({@code -1} = first free) if the rule book allows it.
     *
     * @return the validation result (nothing changed unless ok)
     */
    public ChipValidation.Result insertChip(ChipItem chip, int slot) {
        ChipRules.Exclusions rules = this.level() instanceof ServerLevel server
                ? ChipRules.forServer(server.getServer()) : ChipRules.DEFAULT;
        ChipValidation.Result result = ChipValidation.insert(this.chips, this.chassis(), slot, chip.kind(),
                chip.tier(), rules);
        if (result.ok()) {
            this.chips = new ArrayList<>(ChipValidation.withInserted(this.chips, chip.toChipStack()));
            this.chipsChanged();
            if (this.level() instanceof ServerLevel server) {
                za.co.neroland.nerocompanion.progression.CompanionProgress.chipInstalled(server.getServer(),
                        this.ownerId);
            }
        }
        return result;
    }

    /** Server: takes the chip out of {@code slot}; returns it, or null when the slot is empty. */
    @Nullable
    public ChipStack removeChip(int slot) {
        if (!ChipValidation.remove(this.chips, slot).ok()) {
            return null;
        }
        ChipStack removed = this.chips.get(slot);
        this.chips = new ArrayList<>(ChipValidation.withRemoved(this.chips, slot));
        this.chipsChanged();
        return removed;
    }

    private void chipsChanged() {
        this.chipSlots = null;
        this.typeGeneration = -1;
        if (this.level() instanceof ServerLevel server) {
            this.resolveType(server);
            if (!this.supportsMode(this.mode)) {
                this.mode = this.type != null ? this.type.defaultMode() : CompanionMode.FOLLOW;
                this.stayAnchor = null;
            }
        }
        this.needsEvaluation = true;
        this.ledgerDirty = true;
    }

    // --- charging ------------------------------------------------------------------------------------

    /**
     * Server: a charger offers {@code fe} (a Pad, the Bay, the Hand Crank). Accepts what fits and marks the
     * companion as charging at {@code fePerTick} for the next second.
     *
     * @return the FE accepted
     */
    public long receiveCharge(long fe, long fePerTick) {
        long accepted = this.energy.insert(fe);
        if (accepted > 0) {
            if (!this.chargeNoted && this.level() instanceof ServerLevel server) {
                // Once per entity load: the gate is idempotent, this only saves the lookups on every pad pulse.
                this.chargeNoted = true;
                za.co.neroland.nerocompanion.progression.CompanionProgress.charged(server.getServer(), this.ownerId);
            }
            this.chargingUntil = this.level().getGameTime() + 20L;
            this.chargingFePerTick = Math.max(1L, fePerTick);
            this.entityData.set(DATA_ENERGY_PCT, (byte) this.energy.percent());
        }
        return accepted;
    }

    public boolean isCharging() {
        return this.level().getGameTime() <= this.chargingUntil;
    }

    public long chargingFePerTick() {
        return this.isCharging() ? this.chargingFePerTick : 0L;
    }

    /** Per-second drains after chips: {@code [idle, active]}. */
    public long[] drains() {
        CompanionType def = this.type;
        if (def == null) {
            return new long[] {0L, 0L};
        }
        return new long[] {ChipEffects.drain(def.energy().drainIdlePerSecond(), this.activeChips),
                ChipEffects.drain(def.energy().drainActivePerSecond(), this.activeChips)};
    }

    /** Server: leaves the world to be parked in a Bay; returns the record the Bay keeps, or null if refused. */
    @Nullable
    public CompanionRecord removeForParking(HomePos bay) {
        if (this.galleryDisplay) {
            return null; // Never parked: it is not a real companion.
        }
        java.util.Optional<HomePos> previousHome = this.home;
        this.home = java.util.Optional.of(bay);
        CompanionRecord record = this.toRecord();
        if (this.level() instanceof ServerLevel server && this.companionId != null) {
            if (!Recovery.parked(server.getServer(), record, this.getUUID(), bay)) {
                // Not the companion the ledger knows: refuse (its next evaluation removes it).
                this.home = previousHome;
                return null;
            }
            CompanionIndex.forServer(server.getServer()).remove(this.companionId, this.getUUID());
            HudBoard.forServer(server.getServer()).remove(this.companionId);
        }
        this.leavingQuietly = true;
        this.discard();
        return record;
    }

    /**
     * Server, before the entity is added: makes this a creative-gallery display companion showing {@code pose}. It
     * stays frozen (no AI, no drain, no work), never enters the Recovery Ledger and never counts toward caps.
     */
    public void markGalleryDisplay(GalleryPose pose) {
        this.galleryDisplay = true;
        this.galleryPose = pose;
        this.needsEvaluation = true;
    }

    /** Whether this is a creative-gallery display companion. */
    public boolean isGalleryDisplay() {
        return this.galleryDisplay;
    }

    /** Whether the brokenRecord guard made this companion inert. */
    public boolean isInert() {
        return this.brokenRecord != null;
    }

    public CompanionEnergy energy() {
        return this.energy;
    }

    public int energyPercentSynced() {
        return this.entityData.get(DATA_ENERGY_PCT);
    }

    public List<ChipStack> chips() {
        return List.copyOf(this.chips);
    }

    /**
     * The chips valid together on this chassis: the only ones any effect may read. {@link #chips()} may also hold
     * stored-but-inactive chips waiting to be handed back.
     */
    public List<ChipStack> activeChips() {
        return List.copyOf(this.activeChips);
    }

    /** Server: stores new bond points (bond service only) and refreshes the synched level. */
    public void setBondPoints(int points) {
        this.bondPoints = Math.max(0, points);
        this.entityData.set(DATA_BOND_LEVEL, (byte) Bond.level(this.bondPoints));
    }

    public Optional<HomePos> home() {
        return this.home;
    }

    public void setHome(@Nullable HomePos home) {
        this.home = Optional.ofNullable(home);
    }

    public Throttle throttle() {
        return this.throttle;
    }

    /** True while a work job runs (MINE, CARRY deposit run...); set by work goals with {@link #setWorking}. */
    public boolean isWorking() {
        return (this.entityData.get(DATA_FLAGS) & FLAG_WORKING) != 0;
    }

    public boolean isPerched() {
        return (this.entityData.get(DATA_FLAGS) & FLAG_PERCHED) != 0;
    }

    public boolean isSleepingCompanion() {
        return (this.entityData.get(DATA_FLAGS) & FLAG_SLEEPING) != 0;
    }

    public int perchCooldown() {
        return this.perchCooldown;
    }

    /** The Core gate the type requires for the current mode, or null. */
    @Nullable
    public Identifier gateForCurrentMode() {
        if (this.type == null) {
            return null;
        }
        Map<CompanionMode, Identifier> gates = this.type.requiresGate();
        return gates.get(this.mode);
    }

    /** Where STAY holds; set on entering STAY. */
    public Vec3 stayAnchor() {
        if (this.stayAnchor == null) {
            this.stayAnchor = this.position();
        }
        return this.stayAnchor;
    }

    public int trait() {
        return Math.floorMod(this.personalitySeed, TRAITS);
    }

    private void setFlag(byte flag, boolean on) {
        byte flags = this.entityData.get(DATA_FLAGS);
        byte next = (byte) (on ? flags | flag : flags & ~flag);
        if (next != flags) {
            this.entityData.set(DATA_FLAGS, next);
        }
    }

    // =================================================================================================
    // Work seams: hooks, job, cargo, bound container (per-type classes and work packages build on these)
    // =================================================================================================

    /**
     * Server, every tick the AI steps (every tick at full rate, 1 in 4 when throttled), after the scheduler pass;
     * never while the effective mode is SLEEP and never while inert. Default: nothing.
     */
    protected void onWorkTick() {
    }

    /**
     * Server: the owner used {@code hand} on this companion. Called before any base handling (Remote, chips,
     * Capsule recall, sneak recall, petting); a non-PASS result is returned as is and stops the rest. Return PASS
     * for anything you do not handle - in particular the Remote, chips and Capsules. Default: PASS.
     */
    protected InteractionResult onInteractOwner(Player owner, InteractionHand hand) {
        return InteractionResult.PASS;
    }

    /**
     * Server: this entity is leaving the world for {@code reason} - recall, parking, death, discard, chunk unload,
     * dimension change. Called once, before the base removal. Clean up anything placed in the world (lights, marks).
     * Default: nothing.
     */
    protected void onLeaveWorld(Entity.RemovalReason reason) {
    }

    /** Server: a work package finished a unit of work that no quest counts (a fetch, a lamp, a scout mark). */
    public void jobDone() {
        this.jobDone(null);
    }

    /**
     * Server: a work package finished a unit of {@code work} (a mined block batch, a deposit, a repair, a downed
     * target): bond points through the personality hooks, and the owner's matching milestone (once per entity load).
     */
    public void jobDone(@Nullable za.co.neroland.nerocompanion.progression.CompanionProgress.Work work) {
        if (this.level() instanceof ServerLevel server && this.brokenRecord == null) {
            PersonalityHooks.get().onJobDone(this);
            if (work != null && this.workNoted.add(work)) {
                za.co.neroland.nerocompanion.progression.CompanionProgress.workDone(server.getServer(), this.ownerId,
                        work);
            }
        }
    }

    /**
     * Server: marks the companion as working (a job in progress). While working the scheduler keeps it at full rate
     * near the owner, and the HUD shows it busy.
     */
    public void setWorking(boolean working) {
        if (working != this.isWorking()) {
            this.setFlag(FLAG_WORKING, working);
            this.needsEvaluation = true;
        }
    }

    /** The current job; {@link CompanionJob#none()} when there is none. Server only. */
    public CompanionJob job() {
        return this.job;
    }

    /**
     * Server: replaces the job ({@code null} or {@link CompanionJob#none()} clears it). Persisted with the record.
     *
     * @throws IllegalStateException if the job's type has no codec registered with {@link JobCodecs}
     */
    public void setJob(@Nullable CompanionJob newJob) {
        CompanionJob next = newJob == null ? CompanionJob.none() : newJob;
        if (!next.isNone() && !JobCodecs.isRegistered(next.jobType())) {
            throw new IllegalStateException("no codec registered for job type " + next.jobType());
        }
        this.job = next;
        this.keepRawJob = false;
    }

    /** The container this companion delivers to, if bound. Server only; personal data (a base position). */
    public Optional<HomePos> boundContainer() {
        return this.boundContainer;
    }

    /** Server: binds (or with {@code null} unbinds) the delivery container. The caller checks authority. */
    public void bindContainer(@Nullable HomePos container) {
        this.boundContainer = Optional.ofNullable(container);
    }

    /** Cargo slots: the chassis' {@code cargo_slots} plus chip slots (cargo rack). 0 until the type resolves. */
    public int cargoSlots() {
        CompanionType def = this.type;
        return def == null ? 0 : def.cargoSlots() + ChipEffects.cargoSlots(this.activeChips);
    }

    /**
     * The companion's item cargo, sized {@link #cargoSlots()} (0 slots when the chassis has none). Server only: the
     * client gets an empty container. The same instance is returned until the size changes (type reload, cargo
     * rack chip); then items in slots that no longer exist drop at the companion. Persisted with the record;
     * handed to the player on recall; dropped on death.
     */
    public SimpleContainer cargo() {
        if (!(this.level() instanceof ServerLevel server)) {
            return new SimpleContainer(0);
        }
        if (this.type == null) {
            this.resolveType(server);
        }
        int size = this.cargoSlots();
        if (this.cargo == null || this.cargo.getContainerSize() != size) {
            this.rebuildCargo(size);
        }
        return this.cargo;
    }

    /** The cargo as a slot-indexed list (the container's, or the stored one before the container exists). */
    private List<ItemStack> cargoList() {
        if (this.cargo == null) {
            return this.storedCargo;
        }
        List<ItemStack> list = new ArrayList<>(this.cargo.getContainerSize());
        for (int i = 0; i < this.cargo.getContainerSize(); i++) {
            list.add(this.cargo.getItem(i));
        }
        return list;
    }

    private void rebuildCargo(int size) {
        List<ItemStack> current = this.cargoList();
        SimpleContainer next = new SimpleContainer(Math.max(0, size));
        for (int i = 0; i < Math.min(next.getContainerSize(), current.size()); i++) {
            next.setItem(i, current.get(i));
        }
        List<ItemStack> spill = JobData.overflow(current, next.getContainerSize(), ItemStack::isEmpty);
        this.cargo = next;
        this.storedCargo = List.of();
        for (ItemStack stack : spill) {
            this.dropAtSelf(stack);
        }
    }

    /** Empties the cargo and returns what was in it (non-empty stacks only). */
    private List<ItemStack> takeAllCargo() {
        List<ItemStack> out = new ArrayList<>();
        for (ItemStack stack : this.cargoList()) {
            if (!stack.isEmpty()) {
                out.add(stack.copy());
            }
        }
        if (this.cargo != null) {
            this.cargo.clearContent();
        }
        this.storedCargo = List.of();
        return out;
    }

    private void dropAtSelf(ItemStack stack) {
        if (stack.isEmpty() || !(this.level() instanceof ServerLevel server)) {
            return;
        }
        ItemEntity item = new ItemEntity(server, this.getX(), this.getY() + 0.25D, this.getZ(), stack);
        item.setDefaultPickUpDelay();
        server.addFreshEntity(item);
    }

    private static void giveTo(Player player, ItemStack stack) {
        //? if >=26.3 {
        /*player.getInventory().placeItemBackInInventory(stack, net.minecraft.util.Prediction.SERVER_ONLY);
        *///?} else {
        player.getInventory().placeItemBackInInventory(stack);
        //?}
    }

    /** Server: shows (or ends) the nap pose. Set by nap goals; pose only, the mode and HUD are unchanged. */
    public void setNapping(boolean napping) {
        this.setFlag(FLAG_NAPPING, napping);
    }

    public boolean isNapping() {
        return (this.entityData.get(DATA_FLAGS) & FLAG_NAPPING) != 0;
    }

    /**
     * Server: a charger (Charging Pad) has this companion docked and powered for {@code ticks} ticks. Called
     * whether or not energy flowed (a full battery still docks) and while asleep. Calls {@link #onChargerTick}.
     */
    public final void chargerTick(int ticks) {
        if (this.brokenRecord == null && !this.isRemoved()) {
            this.onChargerTick(ticks);
        }
    }

    /** Server: docked on a powered charger for {@code ticks} ticks (see {@link #chargerTick}). Default: nothing. */
    protected void onChargerTick(int ticks) {
    }

    /** The personality trait index (0-5), as synched to clients. */
    public int syncedTrait() {
        return this.entityData.get(DATA_TRAIT);
    }

    /** The bond level, as synched to clients. */
    public int syncedBondLevel() {
        return this.entityData.get(DATA_BOND_LEVEL);
    }

    /** The {@code FLAG_*} bits, as synched to clients. */
    public byte syncedFlags() {
        return this.entityData.get(DATA_FLAGS);
    }

    // =================================================================================================
    // Ticking: scheduler, energy, perch pin
    // =================================================================================================

    @Override
    public void tick() {
        if (this.level() instanceof ServerLevel server) {
            long started = BudgetProbe.start();
            try {
                super.tick();
                this.serverCompanionTick(server);
            } finally {
                BudgetProbe.stop(started);
            }
        } else {
            super.tick();
            this.clientPerchTick();
        }
    }

    private void serverCompanionTick(ServerLevel server) {
        if (this.isRemoved()) {
            return;
        }
        if (this.perchCooldown > 0) {
            this.perchCooldown--;
        }
        if (this.needsEvaluation || CompanionScheduler.isEvaluationTick(this.tickCount, this.getId())) {
            this.needsEvaluation = false;
            this.evaluate(server);
        }
        if (this.isPerched()) {
            this.pinToShoulder();
        }
        if (!this.aiSkipped && this.brokenRecord == null && !this.isRemoved()
                && this.effectiveMode() != CompanionMode.SLEEP
                && this.throttle.reason() != Throttle.Reason.SALVAGING) {
            this.onWorkTick();
        }
    }

    /** The once-a-second scheduler pass. */
    private void evaluate(ServerLevel server) {
        if (this.brokenRecord != null) {
            // Inert: asleep, no drain, not indexed, no new identity minted.
            this.throttle = new Throttle(Throttle.State.SLEEP, Throttle.Reason.NO_OWNER);
            this.setFlag(FLAG_SLEEPING, true);
            this.entityData.set(DATA_MODE, (byte) CompanionMode.SLEEP.ordinal());
            return;
        }
        this.resolveType(server);
        if (this.galleryDisplay) {
            this.galleryEvaluate(server);
            return;
        }
        if (!this.isPerched() && this.isInWall()) {
            // Loaded (or pushed) inside a block: move to the nearest safe spot rather than sit in the wall.
            this.relocateNear(this.blockPosition(), 0);
        }
        if (this.companionId == null) {
            this.companionId = UUID.randomUUID();
        }
        // Who am I? The ledger keeps one copy of each companion LIVE.
        boolean forceTouch = this.ledgerDirty;
        this.ledgerDirty = false;
        RecoveryBook.Verdict verdict = Recovery.evaluate(this, forceTouch);
        if (verdict == RecoveryBook.Verdict.STALE) {
            this.discardAsStale();
            return;
        }
        if (verdict == RecoveryBook.Verdict.ERASED) {
            this.unbindForErasure();
            return;
        }
        this.applyNamePrivacy(za.co.neroland.nerocompanion.privacy.CompanionPrivacy.namesShown(server.getServer(),
                this.ownerId));
        Salvage.dropIfExpired(this);
        ServerPlayer owner = this.resolveOwner();
        this.ownerCache = owner;
        boolean online = owner != null;
        boolean same = owner != null && owner.level() == server;
        double distanceSq = owner != null && same ? this.distanceToSqr(owner) : Double.MAX_VALUE;
        CompanionLimits limits = NeroCompanionConfig.limits();
        boolean low = this.energy.isLow(limits.lowBatteryPercent());
        boolean homeReachable = this.homeReachable(server, limits);

        // Off-world (Nerospace through its façade; Earth on a Core-only server): flight speed follows the planet's
        // gravity, and radiation puts the companion to sleep unless it carries the Rad Shielding chip.
        za.co.neroland.nerocompanion.compat.nerospace.OffWorld where =
                za.co.neroland.nerocompanion.compat.nerospace.NerospaceCompat.environment(server, this.blockPosition());
        boolean hazard = za.co.neroland.nerocompanion.compat.nerospace.OffWorldRules.sleepsInHazard(where,
                ChipEffects.has(this.activeChips(), za.co.neroland.nerocompanion.chip.ChipKinds.RAD_SHIELDING));
        if (this.locomotion() == Locomotion.FLYING) {
            this.applyFlightFactor(za.co.neroland.nerocompanion.compat.nerospace.OffWorldRules.flightFactor(
                    where.gravity()));
        }

        Throttle previous = this.throttle;
        Throttle next = CompanionScheduler.evaluate(new CompanionScheduler.Input(this.ownerId != null, online, same,
                distanceSq, this.isWorking(), this.energy.isEmpty(), low, homeReachable,
                Salvage.keepsAwake(this, online), hazard), limits);
        this.throttle = next;
        if (owner != null && next.reason() == Throttle.Reason.HAZARD
                && (previous == null || previous.reason() != Throttle.Reason.HAZARD)) {
            owner.sendSystemMessage(Component.translatable("message.nerocompanion.radiation_sleep",
                    this.getDisplayName()), true);
        }

        CompanionMode effective = this.effectiveMode();
        CompanionType def = this.type;
        long now = server.getGameTime();
        if (def != null && now - this.lastDrainTick >= CompanionScheduler.EVALUATION_INTERVAL) {
            this.lastDrainTick = now;
            long[] drains = this.drains();
            this.energy.drainSecond(CompanionEnergy.activityFor(effective, this.isPerched(), this.isWorking()),
                    drains[0], drains[1]);
        }
        if (low && owner != null && this.energy.takeLowAlert(limits.lowBatteryPercent())) {
            owner.sendSystemMessage(Component.translatable(homeReachable
                    ? "message.nerocompanion.low_battery_home" : "message.nerocompanion.low_battery",
                    this.getDisplayName()), true);
            // Once per discharge (re-armed above 50 %), like the action-bar line.
            CompanionAlerts.raise(server.getServer(), this.ownerId, AlertText.Kind.LOW_BATTERY, this.companionId,
                    this.typeId(), this.name);
        }

        // Gathering death drops with the owner elsewhere: only the salvage goal (no work, follow or idle goals).
        this.modeController().apply(effective, this.throttle.reason() == Throttle.Reason.SALVAGING);
        // Flyers hover awake and asleep (a sleeping flyer must not sink into water or lava); enforced every pass
        // because a save from an older build may carry gravity on.
        if (this.locomotion() == Locomotion.FLYING && !this.isNoGravity()) {
            this.setNoGravity(true);
        }

        if (this.ownerId != null) {
            CompanionIndex.forServer(server.getServer()).touch(this.companionId, this.ownerId, this.getUUID(),
                    this.region());
        }
        this.syncData(effective);
        if (this.ownerId != null) {
            HudBoard.forServer(server.getServer()).publish(this.ownerId, this.hudEntry(effective, low));
        }
        PersonalityHooks.get().onTick(this);
    }

    /**
     * The once-a-second pass of a gallery display companion: no ledger, no index, no HUD, no drain, no goals - only
     * the synched pose. Without a pose for this run (its gallery is gone) it leaves the world.
     */
    private void galleryEvaluate(ServerLevel server) {
        if (this.galleryPose == null) {
            this.galleryPose = GalleryDisplays.poseFor(this.getUUID());
        }
        GalleryPose pose = this.galleryPose;
        if (pose == null) {
            this.leavingQuietly = true;
            this.discard();
            return;
        }
        this.applyNamePrivacy(za.co.neroland.nerocompanion.privacy.CompanionPrivacy.namesShown(server.getServer(),
                this.ownerId));
        this.getNavigation().stop();
        this.setDeltaMovement(Vec3.ZERO);
        if (this.locomotion() == Locomotion.FLYING && !this.isNoGravity()) {
            this.setNoGravity(true);
        }
        CompanionMode shown = pose.mode() != null ? pose.mode() : this.mode;
        this.entityData.set(DATA_MODE, (byte) shown.ordinal());
        this.entityData.set(DATA_ENERGY_PCT,
                (byte) (pose.energyPercent() >= 0 ? pose.energyPercent() : this.energy.percent()));
        this.entityData.set(DATA_SKIN, this.skin.map(Identifier::toString).orElse(""));
        this.entityData.set(DATA_HAT, this.hat.map(Identifier::toString).orElse(""));
        this.entityData.set(DATA_BOND_LEVEL,
                (byte) (pose.bondLevel() >= 0 ? pose.bondLevel() : Bond.level(this.bondPoints)));
        this.entityData.set(DATA_TRAIT, (byte) this.trait());
        byte nameHidden = (byte) (this.entityData.get(DATA_FLAGS) & FLAG_NAME_HIDDEN);
        byte shownFlags = (byte) (pose.flags() & (FLAG_SLEEPING | FLAG_WORKING | FLAG_NAPPING));
        this.entityData.set(DATA_FLAGS, (byte) (nameHidden | shownFlags));
        this.setCustomNameVisible(pose.nameVisible() && this.getCustomName() != null);
    }

    /** This companion's HUD summary (no position, no dimension, no owner). */
    public Payloads.HudEntry hudEntry(CompanionMode effective, boolean low) {
        int flags = Payloads.HudEntry.LOADED;
        if (effective == CompanionMode.SLEEP) {
            flags |= Payloads.HudEntry.SLEEPING;
        }
        if (low) {
            flags |= Payloads.HudEntry.LOW_BATTERY;
        }
        if (this.isWorking()) {
            flags |= Payloads.HudEntry.WORKING;
        }
        int modes = 0;
        ChipValidation.Chassis chassis = this.chassis();
        for (CompanionMode each : CompanionMode.values()) {
            boolean supported = this.supportsMode(each);
            boolean chip = !supported && ChipEffects.unlockerOf(each)
                    .filter(kind -> chassis.categories().contains(kind.category())).isPresent();
            modes |= Payloads.HudEntry.modeBits(supported, chip, each);
        }
        return new Payloads.HudEntry(this.companionId, java.util.Optional.of(this.typeId()), this.name.orElse(""),
                this.mode, this.energy.percent(), Bond.level(this.bondPoints), flags, this.getId(), modes);
    }

    private boolean homeReachable(ServerLevel server, CompanionLimits limits) {
        if (this.home.isEmpty()) {
            return false;
        }
        HomePos pos = this.home.get();
        double range = limits.returnHomeRange();
        return pos.isIn(server) && pos.pos().distToCenterSqr(this.position()) <= range * range;
    }

    public RegionKey region() {
        return RegionKey.ofBlock(this.level().dimension().identifier().toString(), this.getBlockX(),
                this.getBlockZ());
    }

    private void syncData(CompanionMode effective) {
        this.entityData.set(DATA_MODE, (byte) effective.ordinal());
        this.entityData.set(DATA_ENERGY_PCT, (byte) this.energy.percent());
        this.entityData.set(DATA_SKIN, this.skin.map(Identifier::toString).orElse(""));
        this.entityData.set(DATA_HAT, this.hat.map(Identifier::toString).orElse(""));
        this.entityData.set(DATA_BOND_LEVEL, (byte) Bond.level(this.bondPoints));
        this.entityData.set(DATA_TRAIT, (byte) this.trait());
        boolean asleep = effective == CompanionMode.SLEEP;
        if (asleep != this.isSleepingCompanion() && this.tickCount > 20 && this.level() instanceof ServerLevel server
                && !this.isSilent()) {
            // Powering down / waking up (not on the first sync after loading).
            server.playSound(null, this.getX(), this.getY(), this.getZ(),
                    asleep ? za.co.neroland.nerocompanion.registry.ModSounds.SLEEP_POWERDOWN.get()
                            : za.co.neroland.nerocompanion.registry.ModSounds.WAKE.get(),
                    net.minecraft.sounds.SoundSource.NEUTRAL, 0.5F,
                    za.co.neroland.nerocompanion.registry.ModSounds.pitch(this.getRandom()));
        }
        this.setFlag(FLAG_SLEEPING, asleep);
        if (effective == CompanionMode.SLEEP) {
            this.setFlag(FLAG_NAPPING, false);
        }
    }

    private void resolveType(ServerLevel server) {
        CompanionType loaded = CompanionTypes.forServer(server.getServer()).get(this.typeId());
        int generation = CompanionTypes.generation();
        if (this.type != null && generation == this.typeGeneration) {
            return;
        }
        this.typeGeneration = generation;
        CompanionType def = loaded != null ? loaded : CompanionType.defaults(this.typeId(),
                BuiltInRegistries.ENTITY_TYPE.getKey(this.getType()), this.locomotion());
        this.type = def;
        this.sanitizeChips(server);
        this.energy.setCapacity(ChipEffects.capacity(def.energy().capacity(), this.activeChips));
        if (!this.supportsMode(this.mode)) {
            this.mode = def.defaultMode();
        }
        // Chip stats are applied to the base values (bond, skins and hats never touch them).
        double speed = ChipEffects.speedMultiplier(this.activeChips);
        setBase(Attributes.MAX_HEALTH, def.maxHealth() + ChipEffects.healthBonus(this.activeChips));
        setBase(Attributes.ARMOR, def.armor() + ChipEffects.armourBonus(this.activeChips));
        setBase(Attributes.MOVEMENT_SPEED, def.speed() * speed);
        setBase(Attributes.FLYING_SPEED, def.flyingSpeed() * speed * this.flightFactor);
        setBase(Attributes.ATTACK_DAMAGE, def.attackDamage());
        if (this.healthPending) {
            this.healthPending = false;
            this.setHealth(this.pendingHealth == CompanionRecord.FULL_HEALTH ? this.getMaxHealth()
                    : Math.min(this.pendingHealth, this.getMaxHealth()));
        }
        if (this.getHealth() > this.getMaxHealth()) {
            this.setHealth(this.getMaxHealth());
        }
        if (this.modeController != null) {
            // First resolve or a /reload: the chassis (small, locomotion defaults) may differ, so rebuild the set.
            this.modeController.reset();
        }
        if ((this.cargo != null && this.cargo.getContainerSize() != this.cargoSlots())
                || (this.cargo == null && !this.storedCargo.isEmpty())) {
            this.rebuildCargo(this.cargoSlots());
        }
    }

    /** Off-world: re-applies the flying speed when the gravity factor changes (chips stay in the product). */
    private void applyFlightFactor(double factor) {
        if (factor == this.flightFactor) {
            return;
        }
        this.flightFactor = factor;
        CompanionType def = this.type;
        if (def != null) {
            setBase(Attributes.FLYING_SPEED, def.flyingSpeed() * ChipEffects.speedMultiplier(this.activeChips)
                    * factor);
        }
    }

    private void setBase(net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
            double value) {
        AttributeInstance instance = this.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    /**
     * Validates the record's chips against this chassis with the chip rule book: valid ones become
     * {@link #activeChips}; invalid ones go back to the owner's inventory (or feet) when the owner is online,
     * otherwise they stay stored but inactive.
     */
    private void sanitizeChips(ServerLevel server) {
        ChipValidation.Split split = ChipValidation.sanitize(this.chips, this.chassis(),
                ChipRules.forServer(server.getServer()), za.co.neroland.nerocompanion.chip.ChipKinds::byId);
        this.activeChips = split.kept();
        this.chipSlots = null;
        if (split.rejected().isEmpty()) {
            return;
        }
        ServerPlayer owner = this.resolveOwner();
        if (owner == null) {
            return;
        }
        List<ChipStack> kept = new ArrayList<>(split.kept());
        for (ChipStack chip : split.rejected()) {
            net.minecraft.world.item.Item item = za.co.neroland.nerocompanion.chip.ChipKinds.byId(chip.chip())
                    .map(kind -> ModItems.chip(kind, chip.tier())).orElse(null);
            if (item == null) {
                // An unknown kind (a newer build's chip) cannot be handed back: keep it stored, inactive.
                kept.add(chip);
            } else {
                ItemStack stack = new ItemStack(item);
                if (!owner.getInventory().add(stack)) {
                    //? if >=26.3 {
                    /*owner.getInventory().placeItemBackInInventory(stack, net.minecraft.util.Prediction.SERVER_ONLY);
                    *///?} else {
                    owner.getInventory().placeItemBackInInventory(stack);
                    //?}
                }
            }
        }
        this.chips = kept;
    }

    public ModeController modeController() {
        if (this.modeController == null) {
            this.modeController = new ModeController(this, this.goalSelector, this.targetSelector);
        }
        return this.modeController;
    }

    /**
     * Skips the AI step on throttled ticks (QUARTER: 3 ticks in 4) and while asleep, without touching the goal
     * set. {@code Mob.serverAiStep} is final, so the skip goes through {@link #isImmobile()}, which vanilla reads
     * only in {@code LivingEntity.aiStep} (then it zeroes {@code xxa/zza}, clears jumping and skips
     * {@code serverAiStep}). The vertical input {@code yya} is zeroed here as well, so a throttled flyer coasts
     * instead of keeping its last climb or dive.
     */
    @Override
    public void aiStep() {
        this.aiSkipped = this.level() instanceof ServerLevel
                && (this.galleryDisplay
                        || !CompanionScheduler.stepsAi(this.throttle.state(), this.tickCount, this.getId()));
        if (this.aiSkipped) {
            this.yya = 0.0F;
        }
        super.aiStep();
    }

    @Override
    protected boolean isImmobile() {
        return super.isImmobile() || this.aiSkipped;
    }

    // =================================================================================================
    // Perching: a world entity pinned to the owner's shoulder, no riding
    // =================================================================================================

    /** Server: perch on the owner's shoulder (called by {@code PerchGoal}). */
    public void startPerch(ServerPlayer owner) {
        this.setFlag(FLAG_PERCHED, true);
        this.entityData.set(DATA_OWNER_ENTITY_ID, owner.getId());
        this.noPhysics = true;
        this.setNoGravity(true);
        this.getNavigation().stop();
        this.pinToShoulder();
    }

    /** Server: leave the shoulder (called by {@code PerchGoal}, and on any unperch condition). */
    public void stopPerch() {
        if (!this.isPerched()) {
            return;
        }
        this.setFlag(FLAG_PERCHED, false);
        this.entityData.set(DATA_OWNER_ENTITY_ID, -1);
        this.noPhysics = false;
        // Flyers hover whether awake or asleep (they never sink into water or lava); ground chassis fall.
        this.setNoGravity(this.locomotion() == Locomotion.FLYING);
        this.perchCooldown = PERCH_COOLDOWN;
        // The shoulder point may be inside a wall: step off next to the owner (or where it is, if the owner left).
        ServerPlayer owner = this.owner();
        if (owner != null && owner.level() == this.level()) {
            if (!this.relocateNear(owner.blockPosition(), 1)) {
                this.snapTo(owner.getX(), owner.getY(), owner.getZ(), this.getYRot(), this.getXRot());
            }
        } else if (!this.isSafeSpot(this.blockPosition())) {
            this.relocateNear(this.blockPosition(), 0);
        }
    }

    /**
     * Moves to the nearest safe spot around {@code center} (rings {@code minRing}..3, one block up or down).
     *
     * @return false if none was found (the companion stays put; its environmental immunity covers it)
     */
    public boolean relocateNear(BlockPos center, int minRing) {
        Optional<BlockPos> spot = SafeSpot.find(center, minRing, 3, 1, this::isSafeSpot);
        spot.ifPresent(pos -> {
            this.snapTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, this.getYRot(), this.getXRot());
            this.getNavigation().stop();
            this.setDeltaMovement(Vec3.ZERO);
        });
        return spot.isPresent();
    }

    /**
     * A block the companion can stand (or hover) in: inside the world border and above the bottom of the world,
     * no fluid, no fire, nothing hot below, solid ground below for ground chassis, and room for the hitbox.
     */
    public boolean isSafeSpot(BlockPos pos) {
        Level level = this.level();
        if (!level.getWorldBorder().isWithinBounds(pos) || pos.getY() <= level.getMinY()) {
            return false;
        }
        BlockState at = level.getBlockState(pos);
        if (!at.getFluidState().isEmpty() || at.is(BlockTags.FIRE)) {
            return false;
        }
        BlockPos belowPos = pos.below();
        BlockState below = level.getBlockState(belowPos);
        if (below.getFluidState().is(FluidTags.LAVA) || below.is(Blocks.MAGMA_BLOCK) || below.is(BlockTags.FIRE)
                || below.is(BlockTags.CAMPFIRES)) {
            return false;
        }
        if (this.locomotion() == Locomotion.GROUND && !below.isFaceSturdy(level, belowPos, Direction.UP)) {
            return false;
        }
        AABB box = this.getBoundingBox().move(pos.getX() + 0.5D - this.getX(), pos.getY() - this.getY(),
                pos.getZ() + 0.5D - this.getZ());
        return level.noCollision(this, box);
    }

    // =================================================================================================
    // Damage, leads, vehicles, pushing
    // =================================================================================================

    /** Classifies a damage source for {@link CompanionSafety}. */
    private static CompanionSafety.Hazard hazardOf(DamageSource source) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return CompanionSafety.Hazard.OTHER;
        }
        if (source.is(DamageTypeTags.IS_DROWNING)) {
            return CompanionSafety.Hazard.DROWNING;
        }
        if (source.is(DamageTypes.IN_WALL)) {
            return CompanionSafety.Hazard.IN_WALL;
        }
        if (source.is(DamageTypeTags.IS_FALL)) {
            return CompanionSafety.Hazard.FALL;
        }
        if (source.is(DamageTypeTags.IS_FIRE)) {
            return CompanionSafety.Hazard.FIRE;
        }
        if (source.is(DamageTypes.CACTUS) || source.is(DamageTypes.SWEET_BERRY_BUSH)
                || source.is(DamageTypes.STALAGMITE)) {
            return CompanionSafety.Hazard.CONTACT;
        }
        return CompanionSafety.Hazard.OTHER;
    }

    private boolean ignoresDamage(DamageSource source) {
        if (this.galleryDisplay) {
            // A display companion takes no damage at all; gallery clear removes it.
            return true;
        }
        boolean sleeping = this.throttle.sleeping() || this.mode == CompanionMode.SLEEP || this.brokenRecord != null;
        return CompanionSafety.isImmune(hazardOf(source), sleeping);
    }

    //? if >=1.21.2 {
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (this.ignoresDamage(source)) {
            return false;
        }
        return super.hurtServer(level, source, amount);
    }
    //?} else {
    /*@Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.ignoresDamage(source)) {
            return false;
        }
        return super.hurt(source, amount);
    }
    *///?}

    /** Nobody can put a lead on a companion: a stranger could otherwise walk it away. */
    @Override
    public boolean canBeLeashed() {
        return false;
    }

    /** Companions never board boats or minecarts (a stranger could scoop one up and carry it off). */
    @Override
    protected boolean canRide(Entity vehicle) {
        return false;
    }

    /** Not pushed by players or mobs, so a stranger cannot shove a sleeping companion into lava or a portal. */
    @Override
    public boolean isPushable() {
        return false;
    }

    /** Whether the owner's state forbids perching right now (sneaking, hurt, gliding, elsewhere). */
    public static boolean ownerRefusesPerch(@Nullable LivingEntity owner, Level level) {
        return owner == null || owner.isRemoved() || owner.level() != level || owner.isShiftKeyDown()
                || owner.hurtTime > 0 || owner.isFallFlying() || owner.isPassenger() || owner.isSleeping();
    }

    private void pinToShoulder() {
        ServerPlayer owner = this.owner();
        if (owner == null || ownerRefusesPerch(owner, this.level())) {
            this.stopPerch();
            return;
        }
        Vec3 point = shoulderPoint(owner.getX(), owner.getY(), owner.getZ(), owner.yBodyRot, owner.getBbHeight());
        this.setPos(point.x, point.y, point.z);
        this.setDeltaMovement(Vec3.ZERO);
        this.setYRot(owner.yBodyRot);
        this.yBodyRot = owner.yBodyRot;
        this.yHeadRot = owner.yHeadRot;
    }

    /** Right-shoulder point for an owner at {@code (x, y, z)} with body yaw {@code bodyYaw} (degrees). */
    public static Vec3 shoulderPoint(double x, double y, double z, float bodyYaw, float ownerHeight) {
        float radians = bodyYaw * Mth.DEG_TO_RAD;
        double side = 0.42D;
        // The owner's right: forward is (-sin, cos), so right is (-cos, -sin).
        return new Vec3(x - Mth.cos(radians) * side, y + ownerHeight - 0.42D, z - Mth.sin(radians) * side);
    }

    /**
     * Client: while perched, place this entity at the owner's shoulder using the owner's own previous and
     * current positions, so the renderer's interpolation follows the owner's interpolation (no 20 Hz jitter).
     */
    private void clientPerchTick() {
        if (!this.isPerched()) {
            return;
        }
        int ownerEntityId = this.entityData.get(DATA_OWNER_ENTITY_ID);
        Entity entity = ownerEntityId < 0 ? null : this.level().getEntity(ownerEntityId);
        if (!(entity instanceof LivingEntity owner)) {
            return;
        }
        Vec3 now = shoulderPoint(owner.getX(), owner.getY(), owner.getZ(), owner.yBodyRot, owner.getBbHeight());
        Vec3 before = shoulderPoint(owner.xOld, owner.yOld, owner.zOld, owner.yBodyRotO, owner.getBbHeight());
        this.setPos(now.x, now.y, now.z);
        this.xo = before.x;
        this.yo = before.y;
        this.zo = before.z;
        this.xOld = before.x;
        this.yOld = before.y;
        this.zOld = before.z;
        this.setYRot(owner.yBodyRot);
        this.yRotO = owner.yBodyRotO;
        this.yBodyRot = owner.yBodyRot;
        this.yBodyRotO = owner.yBodyRotO;
    }

    // =================================================================================================
    // Interaction and lifecycle
    // =================================================================================================

    /**
     * Owner, sneaking with an empty hand, or holding an empty Capsule: recall. Anyone else's click falls through.
     * Ownership is only known on the server; the client always passes and the server decides.
     */
    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!(this.level() instanceof ServerLevel)) {
            return InteractionResult.PASS;
        }
        if (!this.isOwnedBy(player) || this.brokenRecord != null || this.galleryDisplay) {
            return InteractionResult.PASS;
        }
        if (hand == InteractionHand.MAIN_HAND && Salvage.returnTo(this, player)) {
            // Salvaged items come back before anything else the click would do.
            return InteractionResult.SUCCESS_SERVER;
        }
        InteractionResult custom = this.onInteractOwner(player, hand);
        if (custom != InteractionResult.PASS) {
            return custom;
        }
        ItemStack held = player.getItemInHand(hand);
        if (held.getItem() instanceof RemoteItem) {
            // Sneak + use with the Remote recalls; a plain use falls through to the Remote (screen / radial).
            if (player.isSecondaryUseActive()) {
                this.recall(player, null);
                return InteractionResult.SUCCESS_SERVER;
            }
            return InteractionResult.PASS;
        }
        if (held.getItem() instanceof ChipItem chip && player instanceof ServerPlayer serverPlayer) {
            Component chipName = held.getHoverName();
            ChipValidation.Result result = this.insertChip(chip, -1);
            if (result.ok()) {
                held.shrink(1);
                serverPlayer.sendSystemMessage(Component.translatable("gui.nerocompanion.chip.installed", chipName),
                        true);
            } else {
                serverPlayer.sendSystemMessage(za.co.neroland.nerocompanion.network.ServerActions.reason(this, result),
                        true);
            }
            return InteractionResult.SUCCESS_SERVER;
        }
        boolean emptyCapsule = held.is(ModItems.CAPSULE.get()) && !held.has(ModDataComponents.COMPANION.get())
                && !held.has(ModDataComponents.BLUEPRINT.get());
        boolean sneakEmpty = player.isSecondaryUseActive() && held.isEmpty();
        if (held.isEmpty() && !player.isSecondaryUseActive() && hand == InteractionHand.MAIN_HAND) {
            // Petting: an empty main hand, not sneaking.
            InteractionResult petted = PersonalityHooks.get().onPetted(this, player);
            if (petted != InteractionResult.PASS) {
                return petted;
            }
        }
        if (!emptyCapsule && !sneakEmpty) {
            return InteractionResult.PASS;
        }
        this.recall(player, emptyCapsule ? held : null);
        return InteractionResult.SUCCESS_SERVER;
    }

    /** Client: the nameplate carries the bond 3-4 decoration (server-side names, messages and logs stay plain). */
    @Override
    public Component getDisplayName() {
        Component name = super.getDisplayName();
        if (this.level().isClientSide()) {
            return za.co.neroland.nerocompanion.bond.BondNameplate.decorate(name, this.syncedBondLevel());
        }
        return name;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public boolean requiresCustomPersistence() {
        return true;
    }

    /** Cargo drops where the companion fell (salvage of the owner's items is Stage 4; this is the bot's own load). */
    @Override
    public void die(DamageSource source) {
        if (this.level() instanceof ServerLevel && !this.isRemoved() && !this.dead) {
            for (ItemStack stack : this.takeAllCargo()) {
                this.dropAtSelf(stack);
            }
            // Killed: LOST at once, reclaimable (without cargo, which just dropped here). Displays never are.
            if (!this.galleryDisplay) {
                Recovery.died(this);
            }
        }
        super.die(source);
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        if (this.level() instanceof ServerLevel && !this.isRemoved()) {
            this.onLeaveWorld(reason);
            // Vanilla unloads chunks and changes dimension through setRemoved(..), not remove(..): neither reaches
            // here, and the ledger's last-known position is the 10-second refresh's. Only an outright discard by
            // something other than recall/park/death (another mod, a command) does: the companion becomes reclaimable.
            if (!this.leavingQuietly && !this.galleryDisplay && this.brokenRecord == null
                    && reason == Entity.RemovalReason.DISCARDED) {
                Recovery.died(this);
            }
        }
        if (reason.shouldDestroy() && this.galleryDisplay) {
            GalleryDisplays.remove(this.getUUID());
        }
        if (reason.shouldDestroy() && this.companionId != null && this.level() instanceof ServerLevel server) {
            // Killed or discarded: no longer LIVE. (Unloading keeps the entry - the companion still exists.)
            CompanionIndex.forServer(server.getServer()).remove(this.companionId, this.getUUID());
            HudBoard.forServer(server.getServer()).remove(this.companionId);
        } else if (this.companionId != null && this.level() instanceof ServerLevel server) {
            // Reached only by mods that remove companions with a non-destroying reason through remove(..).
            HudBoard.forServer(server.getServer()).unloaded(this.companionId);
        }
        super.remove(reason);
    }

    // =================================================================================================
    // Persistence: everything through CompanionRecord
    // =================================================================================================

    //? if >=1.21.6 {
    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
    //?} else {
    /*@Override
    public void addAdditionalSaveData(net.minecraft.nbt.CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        ValueOutput output = ValueOutput.of(nbt, this.registryAccess());
    *///?}
        if (this.brokenRecord != null) {
            // Written back exactly as read, so a later build that understands it loses nothing.
            output.store(RECORD_KEY, CompoundTag.CODEC, this.brokenRecord);
        } else {
            output.store(RECORD_KEY, CompanionRecord.CODEC, this.toRecord());
        }
        if (this.galleryDisplay) {
            output.store(GALLERY_KEY, com.mojang.serialization.Codec.BOOL, true);
        }

    }

    //? if >=1.21.6 {
    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
    //?} else {
    /*@Override
    public void readAdditionalSaveData(net.minecraft.nbt.CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        ValueInput input = ValueInput.of(nbt, this.registryAccess());
    *///?}
        input.read(RECORD_KEY, CompoundTag.CODEC).ifPresent(raw -> {
            Optional<CompanionRecord> record = CompanionRecord.tryDecode(raw, reason -> {
                if (BROKEN_WARNED.compareAndSet(false, true)) {
                    NeroCompanionCommon.LOGGER.warn("[NeroCompanion] a companion's saved data could not be read ({}); "
                            + "it is kept unchanged and the companion stays asleep", reason);
                }
            });
            if (record.isPresent()) {
                this.brokenRecord = null;
                this.applyRecord(record.get());
            } else {
                this.brokenRecord = raw.copy();
            }
        });
        this.galleryDisplay = input.read(GALLERY_KEY, com.mojang.serialization.Codec.BOOL).orElse(false);

    }
}
