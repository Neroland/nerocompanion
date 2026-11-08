package za.co.neroland.nerocompanion.network.payload;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import za.co.neroland.nerocompanion.entity.CompanionMode;

/**
 * Every {@code nerocompanion:main} payload. Client to server payloads are <b>intents</b>: the server re-validates
 * ownership, state, range and rate for each one. Server to client payloads go only to the owner they describe and
 * carry no owner UUID, no position (except a Bay's own position, which the viewer is standing at, and the scout
 * marks the owner's own drone found) and no other player's data.
 */
public final class Payloads {

    private Payloads() {
    }

    /** A footer / action-bar result: a lang key plus string arguments ({@code @key} = translate that key). */
    public record Feedback(boolean ok, String key, List<String> args) {
        public static final Feedback NONE = new Feedback(true, "", List.of());

        public Feedback {
            args = List.copyOf(args);
        }

        public static Feedback ok(String key, String... args) {
            return new Feedback(true, key, List.of(args));
        }

        public static Feedback deny(String key, String... args) {
            return new Feedback(false, key, List.of(args));
        }

        static void write(FriendlyByteBuf buf, Feedback f) {
            buf.writeBoolean(f.ok);
            Wire.string(buf, f.key);
            Wire.list(buf, f.args, Wire::string);
        }

        static Feedback read(FriendlyByteBuf buf) {
            return new Feedback(buf.readBoolean(), Wire.string(buf), Wire.list(buf, Wire::string));
        }
    }

    // =========================================================================================== C2S

    /** Mode button, radial wedge, or the radial hub ({@code FOLLOW} + {@code comeHere}). */
    public record SetMode(UUID companionId, CompanionMode mode, boolean comeHere) implements CustomPacketPayload {
        public static final Type<SetMode> TYPE = Wire.type("set_mode");
        public static final StreamCodec<FriendlyByteBuf, SetMode> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> { buf.writeUUID(p.companionId); Wire.mode(buf, p.mode); buf.writeBoolean(p.comeHere); },
                buf -> new SetMode(buf.readUUID(), Wire.mode(buf), buf.readBoolean()));

        @Override
        public Type<SetMode> type() {
            return TYPE;
        }
    }

    /** The name box. The server filters, strips formatting and caps at 24 characters. */
    public record Rename(UUID companionId, String name) implements CustomPacketPayload {
        public static final Type<Rename> TYPE = Wire.type("rename");
        public static final StreamCodec<FriendlyByteBuf, Rename> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> { buf.writeUUID(p.companionId); buf.writeUtf(p.name, 64); },
                buf -> new Rename(buf.readUUID(), buf.readUtf(64)));

        @Override
        public Type<Rename> type() {
            return TYPE;
        }
    }

    /** Hold a chip and click a slot ({@code insert}), or click a filled slot. Slot {@code -1} = first that fits. */
    public record ChipEdit(UUID companionId, int slot, boolean insert) implements CustomPacketPayload {
        public static final Type<ChipEdit> TYPE = Wire.type("chip_edit");
        public static final StreamCodec<FriendlyByteBuf, ChipEdit> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> { buf.writeUUID(p.companionId); buf.writeVarInt(p.slot + 1); buf.writeBoolean(p.insert); },
                buf -> new ChipEdit(buf.readUUID(), buf.readVarInt() - 1, buf.readBoolean()));

        @Override
        public Type<ChipEdit> type() {
            return TYPE;
        }
    }

    /** Recall into a Capsule (screen button, radial 0). */
    public record Recall(UUID companionId) implements CustomPacketPayload {
        public static final Type<Recall> TYPE = Wire.type("recall");
        public static final StreamCodec<FriendlyByteBuf, Recall> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> buf.writeUUID(p.companionId), buf -> new Recall(buf.readUUID()));

        @Override
        public Type<Recall> type() {
            return TYPE;
        }
    }

    /** Bay Park / Unpark. */
    public record ParkUnpark(BlockPos bay, UUID companionId, boolean park) implements CustomPacketPayload {
        public static final Type<ParkUnpark> TYPE = Wire.type("park_unpark");
        public static final StreamCodec<FriendlyByteBuf, ParkUnpark> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> { buf.writeBlockPos(p.bay); buf.writeUUID(p.companionId); buf.writeBoolean(p.park); },
                buf -> new ParkUnpark(buf.readBlockPos(), buf.readUUID(), buf.readBoolean()));

        @Override
        public Type<ParkUnpark> type() {
            return TYPE;
        }
    }

    /** Skin Studio Apply: a skin, or a hat ({@code id} empty = remove the hat). */
    public record ApplySkin(UUID companionId, boolean hat, Optional<Identifier> id) implements CustomPacketPayload {
        public static final Type<ApplySkin> TYPE = Wire.type("apply_skin");
        public static final StreamCodec<FriendlyByteBuf, ApplySkin> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> { buf.writeUUID(p.companionId); buf.writeBoolean(p.hat); Wire.id(buf, p.id); },
                buf -> new ApplySkin(buf.readUUID(), buf.readBoolean(), Wire.id(buf)));

        @Override
        public Type<ApplySkin> type() {
            return TYPE;
        }
    }

    /** Bay Reclaim on a lost companion. */
    public record Reclaim(BlockPos bay, UUID companionId) implements CustomPacketPayload {
        public static final Type<Reclaim> TYPE = Wire.type("reclaim");
        public static final StreamCodec<FriendlyByteBuf, Reclaim> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> { buf.writeBlockPos(p.bay); buf.writeUUID(p.companionId); },
                buf -> new Reclaim(buf.readBlockPos(), buf.readUUID()));

        @Override
        public Type<Reclaim> type() {
            return TYPE;
        }
    }

    /** Open the Companion screen (Remote tap on a companion, the open-companion key). */
    public record OpenCompanion(UUID companionId) implements CustomPacketPayload {
        public static final Type<OpenCompanion> TYPE = Wire.type("open_companion");
        public static final StreamCodec<FriendlyByteBuf, OpenCompanion> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> buf.writeUUID(p.companionId), buf -> new OpenCompanion(buf.readUUID()));

        @Override
        public Type<OpenCompanion> type() {
            return TYPE;
        }
    }

    // =========================================================================================== S2C

    /** One of the owner's LIVE companions, for the HUD strip and the Remote's target list. */
    public record HudEntry(UUID companionId, Optional<Identifier> type, String name, CompanionMode mode,
            int energyPct, int bondLevel, int flags, int entityId, int modes) {
        public static final int LOADED = 1;
        public static final int SLEEPING = 2;
        public static final int LOW_BATTERY = 4;
        public static final int WORKING = 8;

        public boolean has(int flag) {
            return (this.flags & flag) != 0;
        }

        /** Mode availability for the radial: low 8 bits supported, next 8 bits "a chip would unlock it". */
        public static int modeBits(boolean supported, boolean needsChip, CompanionMode mode) {
            return (supported ? 1 << mode.ordinal() : 0) | (needsChip ? 1 << (8 + mode.ordinal()) : 0);
        }

        public boolean supports(CompanionMode mode) {
            return (this.modes & (1 << mode.ordinal())) != 0;
        }

        public boolean needsChip(CompanionMode mode) {
            return (this.modes & (1 << (8 + mode.ordinal()))) != 0;
        }

        static void write(FriendlyByteBuf buf, HudEntry e) {
            buf.writeUUID(e.companionId);
            Wire.id(buf, e.type);
            Wire.string(buf, e.name);
            Wire.mode(buf, e.mode);
            buf.writeByte(e.energyPct);
            buf.writeByte(e.bondLevel);
            buf.writeByte(e.flags);
            buf.writeVarInt(e.entityId + 1);
            buf.writeVarInt(e.modes);
        }

        static HudEntry read(FriendlyByteBuf buf) {
            return new HudEntry(buf.readUUID(), Wire.id(buf), Wire.string(buf), Wire.mode(buf),
                    Math.max(0, Math.min(100, buf.readByte())), Math.max(0, Math.min(4, buf.readByte())),
                    buf.readByte() & 0xFF, buf.readVarInt() - 1, buf.readVarInt());
        }
    }

    /** The owner's companions (LIVE only, deploy order). No positions, no dimension. */
    public record HudSync(List<HudEntry> entries) implements CustomPacketPayload {
        public static final Type<HudSync> TYPE = Wire.type("hud_sync");
        public static final StreamCodec<FriendlyByteBuf, HudSync> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> Wire.list(buf, p.entries, HudEntry::write),
                buf -> new HudSync(Wire.list(buf, HudEntry::read)));

        public HudSync {
            entries = List.copyOf(entries);
        }

        @Override
        public Type<HudSync> type() {
            return TYPE;
        }
    }

    /** One installed chip as the screen sees it. */
    public record ChipView(Identifier chip, int tier) {
        static void write(FriendlyByteBuf buf, ChipView c) {
            Wire.string(buf, c.chip.toString());
            buf.writeByte(c.tier);
        }

        static ChipView read(FriendlyByteBuf buf) {
            Identifier id = Identifier.tryParse(Wire.string(buf));
            return new ChipView(id == null ? Identifier.fromNamespaceAndPath("nerocompanion", "unknown") : id,
                    buf.readByte());
        }
    }

    /** Mode availability on the screen: supported, the chassis can't, or a chip would unlock it. */
    public record ModeState(CompanionMode mode, int state, Optional<Identifier> chip) {
        public static final int SUPPORTED = 0;
        public static final int TYPE_CANT = 1;
        public static final int NEEDS_CHIP = 2;

        static void write(FriendlyByteBuf buf, ModeState m) {
            Wire.mode(buf, m.mode);
            buf.writeByte(m.state);
            Wire.id(buf, m.chip);
        }

        static ModeState read(FriendlyByteBuf buf) {
            return new ModeState(Wire.mode(buf), buf.readByte(), Wire.id(buf));
        }
    }

    /** Everything the Companion screen and the Skin Studio show, sent only to the owner. */
    public record CompanionSnapshot(UUID companionId, Optional<Identifier> chassis, String name, int trait,
            CompanionMode mode, List<ModeState> modes, long energyFe, long capacityFe, long drainIdleFePerSec,
            long drainActiveFePerSec, long chargingFePerTick, int bondLevel, int bondPointsToNext, int chipSlots,
            int chipCategoryMask, List<ChipView> chips, Optional<Identifier> skin, Optional<Identifier> hat,
            List<String> unlockedSkins, List<String> unlockedHats, int flags, Feedback feedback)
            implements CustomPacketPayload {
        public static final int LOADED = 1;
        public static final int LOW_BATTERY = 2;
        public static final int SLEEPING = 4;
        public static final int AWAY = 8;
        public static final int CHARGING = 16;

        public static final Type<CompanionSnapshot> TYPE = Wire.type("companion_snapshot");
        public static final StreamCodec<FriendlyByteBuf, CompanionSnapshot> STREAM_CODEC = StreamCodec.of(
                Payloads::writeSnapshot, Payloads::readSnapshot);

        public CompanionSnapshot {
            modes = List.copyOf(modes);
            chips = List.copyOf(chips);
            unlockedSkins = List.copyOf(unlockedSkins);
            unlockedHats = List.copyOf(unlockedHats);
        }

        public boolean has(int flag) {
            return (this.flags & flag) != 0;
        }

        public ModeState modeState(CompanionMode mode) {
            for (ModeState state : this.modes) {
                if (state.mode() == mode) {
                    return state;
                }
            }
            return new ModeState(mode, ModeState.TYPE_CANT, Optional.empty());
        }

        @Override
        public Type<CompanionSnapshot> type() {
            return TYPE;
        }
    }

    private static void writeSnapshot(FriendlyByteBuf buf, CompanionSnapshot s) {
        buf.writeUUID(s.companionId());
        Wire.id(buf, s.chassis());
        Wire.string(buf, s.name());
        buf.writeByte(s.trait());
        Wire.mode(buf, s.mode());
        Wire.list(buf, s.modes(), ModeState::write);
        buf.writeVarLong(s.energyFe());
        buf.writeVarLong(s.capacityFe());
        buf.writeVarLong(s.drainIdleFePerSec());
        buf.writeVarLong(s.drainActiveFePerSec());
        buf.writeVarLong(s.chargingFePerTick());
        buf.writeByte(s.bondLevel());
        buf.writeVarInt(s.bondPointsToNext() + 1);
        buf.writeByte(s.chipSlots());
        buf.writeByte(s.chipCategoryMask());
        Wire.list(buf, s.chips(), ChipView::write);
        Wire.id(buf, s.skin());
        Wire.id(buf, s.hat());
        Wire.list(buf, s.unlockedSkins(), Wire::string);
        Wire.list(buf, s.unlockedHats(), Wire::string);
        buf.writeByte(s.flags());
        Feedback.write(buf, s.feedback());
    }

    private static CompanionSnapshot readSnapshot(FriendlyByteBuf buf) {
        return new CompanionSnapshot(buf.readUUID(), Wire.id(buf), Wire.string(buf), buf.readByte(), Wire.mode(buf),
                Wire.list(buf, ModeState::read), buf.readVarLong(), buf.readVarLong(), buf.readVarLong(),
                buf.readVarLong(), buf.readVarLong(), buf.readByte(), buf.readVarInt() - 1, buf.readByte(),
                buf.readByte(), Wire.list(buf, ChipView::read), Wire.id(buf), Wire.id(buf),
                Wire.list(buf, Wire::string), Wire.list(buf, Wire::string), buf.readByte() & 0xFF,
                Feedback.read(buf));
    }

    /** One Bay slot as the viewer may see it: empty, theirs (with numbers), or someone else's (type only). */
    public record BaySlotView(int kind, UUID companionId, Optional<Identifier> type, String name, int energyPct,
            int state) {
        public static final int EMPTY = 0;
        public static final int MINE = 1;
        public static final int FOREIGN = 2;
        public static final int STATE_PARKED = 0;
        public static final int STATE_CHARGING = 1;
        public static final int STATE_FULL = 2;
        public static final UUID NONE = new UUID(0L, 0L);

        public static BaySlotView empty() {
            return new BaySlotView(EMPTY, NONE, Optional.empty(), "", 0, STATE_PARKED);
        }

        static void write(FriendlyByteBuf buf, BaySlotView v) {
            buf.writeByte(v.kind);
            buf.writeUUID(v.companionId);
            Wire.id(buf, v.type);
            Wire.string(buf, v.name);
            buf.writeByte(v.energyPct);
            buf.writeByte(v.state);
        }

        static BaySlotView read(FriendlyByteBuf buf) {
            return new BaySlotView(buf.readByte(), buf.readUUID(), Wire.id(buf), Wire.string(buf), buf.readByte(),
                    buf.readByte());
        }
    }

    /** One of the viewer's companions near the Bay, or one of their lost companions. */
    public record BayListEntry(UUID companionId, Optional<Identifier> type, String name, int energyPct,
            long minutesAgo, String cost) {
        static void write(FriendlyByteBuf buf, BayListEntry e) {
            buf.writeUUID(e.companionId);
            Wire.id(buf, e.type);
            Wire.string(buf, e.name);
            buf.writeByte(e.energyPct);
            buf.writeVarLong(e.minutesAgo);
            Wire.string(buf, e.cost);
        }

        static BayListEntry read(FriendlyByteBuf buf) {
            return new BayListEntry(buf.readUUID(), Wire.id(buf), Wire.string(buf), buf.readByte(),
                    buf.readVarLong(), Wire.string(buf));
        }
    }

    /** The Bay screen's view, built for one viewer. */
    public record BaySnapshot(BlockPos bay, long bufferFe, long bufferCapacityFe, List<BaySlotView> slots,
            List<BayListEntry> nearby, List<BayListEntry> lost, int liveCount, int liveCap, Feedback feedback)
            implements CustomPacketPayload {
        public static final Type<BaySnapshot> TYPE = Wire.type("bay_snapshot");
        public static final StreamCodec<FriendlyByteBuf, BaySnapshot> STREAM_CODEC = StreamCodec.of(
                (buf, s) -> {
                    buf.writeBlockPos(s.bay);
                    buf.writeVarLong(s.bufferFe);
                    buf.writeVarLong(s.bufferCapacityFe);
                    Wire.list(buf, s.slots, BaySlotView::write);
                    Wire.list(buf, s.nearby, BayListEntry::write);
                    Wire.list(buf, s.lost, BayListEntry::write);
                    buf.writeVarInt(s.liveCount);
                    buf.writeVarInt(s.liveCap);
                    Feedback.write(buf, s.feedback);
                },
                buf -> new BaySnapshot(buf.readBlockPos(), buf.readVarLong(), buf.readVarLong(),
                        Wire.list(buf, BaySlotView::read), Wire.list(buf, BayListEntry::read),
                        Wire.list(buf, BayListEntry::read), buf.readVarInt(), buf.readVarInt(), Feedback.read(buf)));

        public BaySnapshot {
            slots = List.copyOf(slots);
            nearby = List.copyOf(nearby);
            lost = List.copyOf(lost);
        }

        @Override
        public Type<BaySnapshot> type() {
            return TYPE;
        }
    }

    /** An emote bubble: a lang key from the trait's line pool, never free text, never player data. */
    public record Chatter(int entityId, String key, int trait) implements CustomPacketPayload {
        public static final Type<Chatter> TYPE = Wire.type("chatter");
        public static final StreamCodec<FriendlyByteBuf, Chatter> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> { buf.writeVarInt(p.entityId); Wire.string(buf, p.key); buf.writeByte(p.trait); },
                buf -> new Chatter(buf.readVarInt(), Wire.string(buf), buf.readByte()));

        @Override
        public Type<Chatter> type() {
            return TYPE;
        }
    }

    /** One scout mark (owner only): an ore block or a hostile's block position, with what it is. */
    public record Mark(BlockPos pos, int kind) {
        public static final int ORE = 0;
        public static final int HOSTILE = 1;

        static void write(FriendlyByteBuf buf, Mark m) {
            buf.writeBlockPos(m.pos);
            buf.writeByte(m.kind);
        }

        static Mark read(FriendlyByteBuf buf) {
            return new Mark(buf.readBlockPos(), buf.readByte());
        }
    }

    /** The owner's scout marks (max 32, 2-second TTL). Wired fully in Stage 3. */
    public record ScoutMarks(List<Mark> marks, int ttlTicks) implements CustomPacketPayload {
        public static final int MAX_MARKS = 32;
        public static final Type<ScoutMarks> TYPE = Wire.type("scout_marks");
        public static final StreamCodec<FriendlyByteBuf, ScoutMarks> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> { Wire.list(buf, p.marks, Mark::write); buf.writeVarInt(p.ttlTicks); },
                buf -> new ScoutMarks(Wire.list(buf, Mark::read), buf.readVarInt()));

        public ScoutMarks {
            marks = List.copyOf(marks.size() > MAX_MARKS ? marks.subList(0, MAX_MARKS) : marks);
        }

        @Override
        public Type<ScoutMarks> type() {
            return TYPE;
        }
    }

    /**
     * The skin and hat catalogue (every client: it draws other players' companions). Definitions only - textures,
     * colours, rules - never who has unlocked what (that is in the owner's {@link CompanionSnapshot}).
     */
    public record LookCatalogue(List<za.co.neroland.nerocompanion.look.Look> looks) implements CustomPacketPayload {
        public static final Type<LookCatalogue> TYPE = Wire.type("look_catalogue");
        public static final StreamCodec<FriendlyByteBuf, LookCatalogue> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> Wire.list(buf, p.looks, za.co.neroland.nerocompanion.look.Look::write),
                buf -> new LookCatalogue(Wire.list(buf, za.co.neroland.nerocompanion.look.Look::read)));

        public LookCatalogue {
            looks = List.copyOf(looks.size() > Wire.MAX_LIST ? looks.subList(0, Wire.MAX_LIST) : looks);
        }

        @Override
        public Type<LookCatalogue> type() {
            return TYPE;
        }
    }
}
