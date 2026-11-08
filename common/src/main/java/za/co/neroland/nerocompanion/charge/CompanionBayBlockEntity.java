package za.co.neroland.nerocompanion.charge;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import java.util.concurrent.atomic.AtomicBoolean;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.energy.EnergyConversions;
import za.co.neroland.nerolandcore.machine.AbstractMachineBlockEntity;
import za.co.neroland.nerolandcore.sideconfig.Channel;
import za.co.neroland.nerolandcore.sideconfig.SideConfig;
import za.co.neroland.nerolandcore.sideconfig.SidePreset;

import za.co.neroland.nerocompanion.NeroCompanionCommon;
import za.co.neroland.nerocompanion.chip.ChipEffects;
import za.co.neroland.nerocompanion.config.NeroCompanionConfig;
import za.co.neroland.nerocompanion.entity.CompanionEntity;
import za.co.neroland.nerocompanion.entity.CompanionRecord;
import za.co.neroland.nerocompanion.entity.HomePos;
import za.co.neroland.nerocompanion.entity.type.CompanionType;
import za.co.neroland.nerocompanion.entity.type.CompanionTypes;
import za.co.neroland.nerocompanion.hud.HudBoard;
import za.co.neroland.nerocompanion.item.CapsuleItem;
import za.co.neroland.nerocompanion.item.CompanionDeployer;
import za.co.neroland.nerocompanion.network.payload.Payloads;
import za.co.neroland.nerocompanion.platform.Services;
import za.co.neroland.nerocompanion.registry.ModBlockEntities;
import za.co.neroland.nerocompanion.registry.ModItems;
import za.co.neroland.nerocompanion.recovery.Recovery;
import za.co.neroland.nerocompanion.recovery.RecoveryBook;
import za.co.neroland.nerocompanion.recovery.ReclaimRecords;

/**
 * The Bay's four parking slots, buffer and charging loop. Parked companions are full {@link CompanionRecord}s in
 * the block entity's own save data (server side only). Clients receive only a display list (type, charge,
 * state per slot) for the parked-model renderer - no owner, no name. Energy arrives through Core's capability on
 * every face ({@link SidePreset#ALL_INPUT}) and is paid out in FE at the server's ratio.
 */
public class CompanionBayBlockEntity extends AbstractMachineBlockEntity {

    /** What a client may know about a slot. */
    public record DisplaySlot(String type, int energyPct, int state) {
        static final Codec<DisplaySlot> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("t").forGetter(DisplaySlot::type),
                Codec.INT.fieldOf("e").forGetter(DisplaySlot::energyPct),
                Codec.INT.fieldOf("s").forGetter(DisplaySlot::state)
        ).apply(i, DisplaySlot::new));
        static final DisplaySlot EMPTY = new DisplaySlot("", 0, 0);

        public boolean occupied() {
            return !this.type.isEmpty();
        }
    }

    public static final double REACH = 8.0D;
    private static final String PARKED_KEY = "Parked";
    private static final String DISPLAY_KEY = "Display";

    private BaySlots slots = new BaySlots();
    /**
     * The saved parked list when it could not be read (a newer schema after a downgrade, a corrupt entry): kept and
     * written back unchanged, and the Bay refuses to park, so a later build that understands it loses nothing.
     */
    @Nullable
    private Dynamic<?> unreadableParked;
    private static final AtomicBoolean UNREADABLE_WARNED = new AtomicBoolean();
    private final int[] states = new int[BaySlots.SIZE];
    private List<DisplaySlot> display = List.of(DisplaySlot.EMPTY, DisplaySlot.EMPTY, DisplaySlot.EMPTY,
            DisplaySlot.EMPTY);
    private int phase;
    /** This instance reconciled its records with the ledger since it loaded. */
    private boolean ledgerChecked;

    @SuppressWarnings("this-escape")
    public CompanionBayBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.COMPANION_BAY.get(), pos, state, bufferNe(), Math.max(1, bufferNe() / 20), 1,
                stack -> null);
        this.installSideConfig(SideConfig.builder().channel(Channel.ENERGY).defaultPreset(SidePreset.ALL_INPUT).build());
    }

    private static int bufferNe() {
        return ChargeMath.bufferNe(ChargeMath.BAY_BUFFER_FE, EnergyConversions.neroToForgeRatio());
    }

    public BaySlots slots() {
        return this.slots;
    }

    /** Client: the display list for the renderer. */
    public List<DisplaySlot> display() {
        return this.display;
    }

    public long storedFe() {
        return ChargeMath.feAvailable(this.energyBuffer().getRaw(), EnergyConversions.neroToForgeRatio());
    }

    public long capacityFe() {
        return ChargeMath.feAvailable(this.energyBuffer().getCapacity(), EnergyConversions.neroToForgeRatio());
    }

    /** A parked record's battery capacity (chassis capacity with its chips). */
    public static long capacityOf(MinecraftServer server, CompanionRecord record) {
        long base = CompanionTypes.get(server, record.type()).map(CompanionType::energy)
                .map(CompanionType.Energy::capacity).orElse(24_000L);
        return ChipEffects.capacity(base, record.chips());
    }

    public static int percent(long energy, long capacity) {
        return capacity <= 0 ? 0 : (int) Math.max(0L, Math.min(100L, energy * 100L / capacity));
    }

    // =================================================================================================
    // Ticking
    // =================================================================================================

    @Override
    protected void serverTick(Level level, BlockPos pos, BlockState state) {
        if (++this.phase < ChargingPadBlockEntity.PERIOD || !(level instanceof ServerLevel server)) {
            return;
        }
        this.phase = 0;
        if (!this.ledgerChecked) {
            this.ledgerChecked = true;
            this.reconcile(server);
        }
        int period = ChargingPadBlockEntity.PERIOD;
        // Scrap Eggs incubate in a Bay with or without power.
        za.co.neroland.nerocompanion.incubation.Incubators.tick(server, pos, period);
        double ratio = EnergyConversions.neroToForgeRatio();
        long[] missing = new long[BaySlots.SIZE];
        long[] capacity = new long[BaySlots.SIZE];
        for (int i = 0; i < BaySlots.SIZE; i++) {
            Optional<CompanionRecord> record = this.slots.get(i);
            if (record.isPresent()) {
                capacity[i] = capacityOf(server.getServer(), record.get());
                missing[i] = Math.max(0L, capacity[i] - record.get().energyFe());
            }
        }
        long[] perTick = new long[BaySlots.SIZE];
        for (int i = 0; i < BaySlots.SIZE; i++) {
            perTick[i] = (missing[i] + period - 1) / period;
        }
        long[] plan = ChargeMath.bayPlan(ChargeMath.feAvailable(this.energyBuffer().getRaw(), ratio) / period,
                perTick);
        long spent = 0;
        boolean changed = false;
        for (int i = 0; i < BaySlots.SIZE; i++) {
            Optional<CompanionRecord> record = this.slots.get(i);
            int stateNow;
            if (record.isEmpty()) {
                stateNow = Payloads.BaySlotView.STATE_PARKED;
            } else {
                long give = Math.min(missing[i], plan[i] * period);
                if (give > 0) {
                    this.slots.set(i, BaySlots.withEnergy(record.get(), record.get().energyFe() + give));
                    spent += give;
                    changed = true;
                }
                long after = record.get().energyFe() + give;
                stateNow = after >= capacity[i] ? Payloads.BaySlotView.STATE_FULL
                        : give > 0 ? Payloads.BaySlotView.STATE_CHARGING : Payloads.BaySlotView.STATE_PARKED;
            }
            if (this.states[i] != stateNow) {
                this.states[i] = stateNow;
                changed = true;
            }
        }
        if (spent > 0) {
            long cost = Math.min(this.energyBuffer().getRaw(), ChargeMath.neCost(spent, ratio));
            this.energyBuffer().setRaw((int) (this.energyBuffer().getRaw() - cost));
        }
        if (changed) {
            this.setChanged();
            if (server.getGameTime() % 40L < period) {
                this.syncToClients();
            }
        }
    }

    // =================================================================================================
    // Park / unpark / hand out
    // =================================================================================================

    /** Server: parks {@code companion} for {@code player} (owner only, within 8 blocks of the Bay). */
    public Payloads.Feedback park(ServerPlayer player, CompanionEntity companion) {
        if (!companion.isOwnedBy(player) || companion.isInert()) {
            return Payloads.Feedback.deny("gui.nerocompanion.reason.not_owner");
        }
        if (companion.distanceToSqr(this.worldPosition.getX() + 0.5D, this.worldPosition.getY() + 0.5D,
                this.worldPosition.getZ() + 0.5D) > REACH * REACH) {
            return Payloads.Feedback.deny("gui.nerocompanion.reason.bay_far");
        }
        if (this.unreadableParked != null) {
            return Payloads.Feedback.deny("gui.nerocompanion.reason.bay_unreadable");
        }
        if (this.slots.isFull()) {
            return Payloads.Feedback.deny("gui.nerocompanion.reason.bay_full");
        }
        String name = displayName(companion.toRecord());
        CompanionRecord record = companion.removeForParking(HomePos.of(this.level, this.worldPosition));
        if (record == null) {
            return Payloads.Feedback.deny(RecoveryBook.STALE_RECORD);
        }
        if (this.slots.park(record) < 0) {
            // Cannot happen (checked above); never lose the companion: hand it back as a Capsule.
            this.handOut(record);
            return Payloads.Feedback.deny("gui.nerocompanion.reason.bay_full");
        }
        this.setChanged();
        this.syncToClients();
        // Say "charging" only when the Bay has power to give; an empty buffer charges nothing.
        return Payloads.Feedback.ok(this.storedFe() > 0 ? "gui.nerocompanion.bay.parked.ok"
                : "gui.nerocompanion.bay.parked.no_power", name);
    }

    /** Whether a NeroLink recall or reclaim could park one more companion here (readable and not full). */
    public boolean acceptsFromLink() {
        return this.unreadableParked == null && !this.slots.isFull();
    }

    /**
     * Server, NeroLink {@code recall}: parks the owner's live {@code companion} here without the player being near
     * (the caller checked ownership against the ledger, and that this Bay is the companion's home). Cargo stays in the
     * parked record, exactly as an in-game park: nothing is put into any inventory.
     *
     * @return null when parked, else the refusal's lang key
     */
    @Nullable
    public String parkFromLink(CompanionEntity companion) {
        if (companion.isInert()) {
            return "gui.nerocompanion.reason.not_live";
        }
        if (!this.acceptsFromLink()) {
            return "gui.nerocompanion.reason.bay_full";
        }
        CompanionRecord record = companion.removeForParking(HomePos.of(this.level, this.worldPosition));
        if (record == null) {
            return RecoveryBook.STALE_RECORD;
        }
        if (this.slots.park(record) < 0) {
            this.handOut(record);
            return "gui.nerocompanion.reason.bay_full";
        }
        this.setChanged();
        this.syncToClients();
        return null;
    }

    /**
     * Server, NeroLink {@code reclaim}: a LOST companion's ledger record, restored parked here. The caller checked
     * ownership and the LOST state and moves the ledger entry to PARKED.
     */
    public boolean parkRecordFromLink(CompanionRecord record) {
        if (!this.acceptsFromLink() || this.slots.park(record) < 0) {
            return false;
        }
        this.setChanged();
        this.syncToClients();
        return true;
    }

    /** Server: lets {@code companionId} out next to the Bay, if it is {@code player}'s and the caps allow. */
    public Payloads.Feedback unpark(ServerPlayer player, UUID companionId) {
        int index = this.slots.indexOf(companionId);
        if (index < 0) {
            return Payloads.Feedback.deny("gui.nerocompanion.reason.slot_empty");
        }
        CompanionRecord record = this.slots.get(index).orElseThrow();
        if (!BaySlots.mayUnpark(record, player.getUUID())) {
            return Payloads.Feedback.deny("gui.nerocompanion.reason.bay_foreign");
        }
        if (!(this.level instanceof ServerLevel server)) {
            return Payloads.Feedback.deny("gui.nerocompanion.reason.not_live");
        }
        BlockPos front = this.worldPosition.relative(this.getBlockState().getValue(CompanionBayBlock.FACING));
        BlockPos spawn = server.getBlockState(front).getCollisionShape(server, front).isEmpty()
                ? front : this.worldPosition.above();
        HomePos here = HomePos.of(server, this.worldPosition);
        if (Recovery.check(server.getServer(), new Recovery.Source.Parked(here), companionId) != null) {
            // The ledger says this companion is somewhere else now: this record is a stale copy. Clear it (or, when its
            // owner was erased, hand it out as an ownerless Capsule).
            this.slots.unpark(companionId);
            if (Recovery.book(server.getServer()).isTombstoned(companionId)
                    && Recovery.book(server.getServer()).get(companionId).isEmpty()) {
                this.dropUnbound(record);
            }
            this.setChanged();
            this.syncToClients();
            return Payloads.Feedback.deny(RecoveryBook.STALE_RECORD);
        }
        CompanionDeployer.Result deployed = CompanionDeployer.tryDeploy(server, player, spawn, record, false,
                new Recovery.Source.Parked(here));
        if (!deployed.ok()) {
            return deployed.arg().isEmpty() ? Payloads.Feedback.deny(deployed.reasonKey())
                    : Payloads.Feedback.deny(deployed.reasonKey(), deployed.arg());
        }
        this.slots.unpark(companionId);
        this.setChanged();
        this.syncToClients();
        return Payloads.Feedback.ok("gui.nerocompanion.bay.unparked.ok", displayName(record),
                "@mode.nerocompanion." + record.mode().id());
    }

    /** The Bay is going away: every parked companion becomes a Capsule dropped at the Bay. */
    public void handOutAll() {
        if (!(this.level instanceof ServerLevel)) {
            return;
        }
        if (this.unreadableParked != null) {
            NeroCompanionCommon.LOGGER.warn("[NeroCompanion] a Companion Bay with unreadable parked data was removed; "
                    + "its saved data could not be turned into Capsules");
        }
        List<CompanionRecord> records = this.slots.drain();
        for (CompanionRecord record : records) {
            try {
                this.handOut(record);
            } catch (RuntimeException failure) {
                // One failure must not swallow the other companions' Capsules.
                NeroCompanionCommon.LOGGER.warn("[NeroCompanion] a parked companion could not be handed out ({})",
                        failure.getClass().getName());
            }
        }
        if (!records.isEmpty()) {
            this.setChanged();
        }
    }

    private void handOut(CompanionRecord record) {
        if (!(this.level instanceof ServerLevel server)) {
            return;
        }
        RecoveryBook.HandOutResult result = Recovery.handOut(server.getServer(), record,
                HomePos.of(server, this.worldPosition));
        if (result.action() == RecoveryBook.HandOut.UNBIND) {
            // Its owner was erased (a tombstone): an ownerless Capsule, never the old owner's data.
            this.dropUnbound(record);
            return;
        }
        if (result.action() == RecoveryBook.HandOut.REFUSE) {
            // A stale copy (the ledger has this companion elsewhere): never mint a Capsule for it.
            warnStale();
            return;
        }
        long ticket = result.ticket();
        ItemStack capsule = new ItemStack(ModItems.CAPSULE.get());
        CapsuleItem.store(server.getServer(), capsule, record,
                percent(record.energyFe(), capacityOf(server.getServer(), record)), ticket);
        Containers.dropItemStack(server, this.worldPosition.getX() + 0.5D, this.worldPosition.getY() + 0.5D,
                this.worldPosition.getZ() + 0.5D, capsule);
    }

    /**
     * First tick after load: reconcile the parked records with the ledger. A record with no entry is adopted as PARKED
     * here, unless an erasure tombstone says its owner was erased (then it leaves as an ownerless Capsule). A record the
     * ledger has PARKED at another position whose Bay no longer holds it (the Bay was moved) is adopted here. Any other
     * record the ledger has elsewhere is a stale copy and is cleared.
     */
    private void reconcile(ServerLevel server) {
        MinecraftServer mc = server.getServer();
        RecoveryBook book = Recovery.book(mc);
        HomePos here = HomePos.of(server, this.worldPosition);
        boolean changed = false;
        for (int i = 0; i < BaySlots.SIZE; i++) {
            Optional<CompanionRecord> slot = this.slots.get(i);
            if (slot.isEmpty()) {
                continue;
            }
            CompanionRecord record = slot.get();
            Optional<za.co.neroland.nerocompanion.recovery.LedgerEntry> entry = book.get(record.companionId());
            if (entry.isEmpty()) {
                if (book.isTombstoned(record.companionId())) {
                    this.slots.unpark(record.companionId());
                    this.dropUnbound(record);
                } else {
                    Recovery.adoptParked(mc, record, here);
                }
                changed = true;
            } else if (book.checkParked(record.companionId(), here) != null) {
                za.co.neroland.nerocompanion.recovery.LedgerEntry known = entry.get();
                boolean movedHere = known.is(za.co.neroland.nerocompanion.recovery.LedgerEntry.State.PARKED)
                        && known.bay().isPresent() && known.owner().equals(record.ownerId())
                        && Recovery.bays(mc).probe(known.bay().get(), record.companionId())
                                == RecoveryBook.BayState.MISSING;
                if (movedHere) {
                    Recovery.adoptParked(mc, record, here);
                } else {
                    this.slots.unpark(record.companionId());
                    warnStale();
                }
                changed = true;
            }
        }
        if (changed) {
            this.setChanged();
            this.syncToClients();
        }
    }

    /** Erasure: {@code owner}'s parked companions leave as ownerless Capsules at the Bay. */
    public int unbindOwner(java.util.UUID owner) {
        int count = 0;
        for (int i = 0; i < BaySlots.SIZE; i++) {
            Optional<CompanionRecord> slot = this.slots.get(i);
            if (slot.isPresent() && slot.get().ownerId().equals(owner)) {
                this.slots.unpark(slot.get().companionId());
                this.dropUnbound(slot.get());
                count++;
            }
        }
        if (count > 0) {
            this.setChanged();
            this.syncToClients();
        }
        return count;
    }

    private void dropUnbound(CompanionRecord record) {
        if (!(this.level instanceof ServerLevel server)) {
            return;
        }
        CompanionRecord erased = ReclaimRecords.erased(record);
        ItemStack capsule = new ItemStack(ModItems.CAPSULE.get());
        CapsuleItem.store(server.getServer(), capsule, erased,
                percent(erased.energyFe(), capacityOf(server.getServer(), erased)), 0L);
        Containers.dropItemStack(server, this.worldPosition.getX() + 0.5D, this.worldPosition.getY() + 1.0D,
                this.worldPosition.getZ() + 0.5D, capsule);
    }

    private static final AtomicBoolean STALE_WARNED = new AtomicBoolean();

    /** Once per run, no data. */
    private static void warnStale() {
        if (STALE_WARNED.compareAndSet(false, true)) {
            NeroCompanionCommon.LOGGER.warn("[NeroCompanion] a Companion Bay held an out-of-date copy of a companion "
                    + "that is elsewhere now; the copy was cleared");
        }
    }

    //? if >=1.21.5 {
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        this.handOutAll();
        if (this.level instanceof ServerLevel server) {
            za.co.neroland.nerocompanion.incubation.Incubators.incubatorRemoved(server, pos);
        }
        super.preRemoveSideEffects(pos, state);
    }
    //?}

    private static String displayName(CompanionRecord record) {
        return record.name().orElse("@entity." + record.type().getNamespace() + "." + record.type().getPath());
    }

    // =================================================================================================
    // The Bay screen's snapshot (built per viewer; their own data only)
    // =================================================================================================

    public Payloads.BaySnapshot snapshotFor(ServerPlayer viewer, Payloads.Feedback feedback) {
        MinecraftServer server = viewer.level().getServer();
        List<Payloads.BaySlotView> views = new ArrayList<>();
        for (int i = 0; i < BaySlots.SIZE; i++) {
            Optional<CompanionRecord> record = this.slots.get(i);
            if (record.isEmpty()) {
                views.add(Payloads.BaySlotView.empty());
            } else if (record.get().ownerId().equals(viewer.getUUID())) {
                CompanionRecord r = record.get();
                views.add(new Payloads.BaySlotView(Payloads.BaySlotView.MINE, r.companionId(), Optional.of(r.type()),
                        r.name().orElse(""), percent(r.energyFe(), capacityOf(server, r)), this.states[i]));
            } else {
                views.add(new Payloads.BaySlotView(Payloads.BaySlotView.FOREIGN, Payloads.BaySlotView.NONE,
                        Optional.of(record.get().type()), "", 0, Payloads.BaySlotView.STATE_PARKED));
            }
        }
        List<Payloads.BayListEntry> nearby = new ArrayList<>();
        if (this.level != null) {
            AABB box = new AABB(this.worldPosition).inflate(REACH);
            for (CompanionEntity companion : this.level.getEntitiesOfClass(CompanionEntity.class, box,
                    c -> c.isAlive() && !c.isInert() && c.isOwnedBy(viewer))) {
                if (companion.companionId() != null) {
                    nearby.add(new Payloads.BayListEntry(companion.companionId(), Optional.of(companion.typeId()),
                            companion.companionName().orElse(""), companion.energy().percent(), 0L, ""));
                }
            }
        }
        // The viewer's lost companions (type, name, how long ago, cost - never where).
        List<Payloads.BayListEntry> lost = Recovery.lostRows(viewer);
        int live = Recovery.book(server).liveFor(viewer.getUUID(), Payloads.BaySlotView.NONE);
        return new Payloads.BaySnapshot(this.worldPosition, this.storedFe(), this.capacityFe(), views, nearby, lost,
                live, NeroCompanionConfig.limits().maxCompanionsPerPlayer(), feedback);
    }

    public void sendSnapshot(ServerPlayer viewer, Payloads.Feedback feedback) {
        Services.NETWORK.sendToPlayer(viewer, this.snapshotFor(viewer, feedback));
        HudBoard.forServer(viewer.level().getServer()).send(viewer);
    }

    // =================================================================================================
    // Persistence and client sync
    // =================================================================================================

    private List<DisplaySlot> buildDisplay() {
        List<DisplaySlot> out = new ArrayList<>(BaySlots.SIZE);
        for (int i = 0; i < BaySlots.SIZE; i++) {
            Optional<CompanionRecord> record = this.slots.get(i);
            if (record.isEmpty()) {
                out.add(DisplaySlot.EMPTY);
            } else {
                // Clients get the LED state only - not the charge of someone else's companion.
                out.add(new DisplaySlot(record.get().type().toString(), 0, this.states[i]));
            }
        }
        return out;
    }

    private void syncToClients() {
        if (this.level != null && !this.level.isClientSide()) {
            BlockState state = this.getBlockState();
            this.level.sendBlockUpdated(this.worldPosition, state, state, 3);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        DisplaySlot.CODEC.listOf().encodeStart(NbtOps.INSTANCE, this.buildDisplay()).result()
                .ifPresent(list -> tag.put(DISPLAY_KEY, list));
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    //? if >=1.21.6 {
    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
    //?} else {
    /*@Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ValueOutput output = ValueOutput.of(tag, registries);
    *///?}
        if (this.unreadableParked != null) {
            // Written back exactly as read.
            output.store(PARKED_KEY, Codec.PASSTHROUGH, this.unreadableParked);
        } else {
            output.store(PARKED_KEY, BaySlots.CODEC, this.slots);
        }

    }

    //? if >=1.21.6 {
    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
    //?} else {
    /*@Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ValueInput input = ValueInput.of(tag, registries);
    *///?}
        input.read(PARKED_KEY, Codec.PASSTHROUGH).ifPresent(raw -> {
            DataResult<BaySlots> read = BaySlots.CODEC.parse(raw);
            if (read.result().isPresent()) {
                this.slots = read.result().get();
                this.unreadableParked = null;
            } else {
                this.slots = new BaySlots();
                this.unreadableParked = raw;
                warnUnreadable();
            }
        });
        input.read(DISPLAY_KEY, DisplaySlot.CODEC.listOf()).ifPresent(list -> {
            List<DisplaySlot> padded = new ArrayList<>(list);
            while (padded.size() < BaySlots.SIZE) {
                padded.add(DisplaySlot.EMPTY);
            }
            this.display = List.copyOf(padded.subList(0, BaySlots.SIZE));
        });
    }

    /** Once per run, and never the data itself (it holds owner UUIDs). */
    private static void warnUnreadable() {
        if (UNREADABLE_WARNED.compareAndSet(false, true)) {
            NeroCompanionCommon.LOGGER.warn("[NeroCompanion] a Companion Bay's parked data could not be read; it is "
                    + "kept unchanged and that Bay refuses new companions");
        }
    }

    /** Whether this Bay's saved parked data could not be read (kept unchanged, parking refused). */
    public boolean hasUnreadableData() {
        return this.unreadableParked != null;
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(NeroCompanionCommon.MOD_ID, path);
    }
}
