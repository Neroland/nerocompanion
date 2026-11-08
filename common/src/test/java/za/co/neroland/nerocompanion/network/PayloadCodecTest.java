package za.co.neroland.nerocompanion.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import io.netty.buffer.Unpooled;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerocompanion.entity.CompanionMode;
import za.co.neroland.nerocompanion.network.payload.Payloads;

/** Every nerocompanion:main payload survives its stream codec unchanged; malformed lists are refused. */
class PayloadCodecTest {

    private static final UUID ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final Identifier TYPE = Identifier.fromNamespaceAndPath("nerocompanion", "scout_drone");

    private static <T> T roundTrip(StreamCodec<FriendlyByteBuf, T> codec, T value) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        codec.encode(buf, value);
        T back = codec.decode(buf);
        assertEquals(0, buf.readableBytes(), "trailing bytes");
        return back;
    }

    private static <T> void same(StreamCodec<FriendlyByteBuf, T> codec, T value) {
        assertEquals(value, roundTrip(codec, value));
    }

    @Test
    void clientToServerIntents() {
        for (CompanionMode mode : CompanionMode.values()) {
            same(Payloads.SetMode.STREAM_CODEC, new Payloads.SetMode(ID, mode, mode == CompanionMode.FOLLOW));
        }
        same(Payloads.Rename.STREAM_CODEC, new Payloads.Rename(ID, "Pip the Second"));
        same(Payloads.Rename.STREAM_CODEC, new Payloads.Rename(ID, ""));
        same(Payloads.ChipEdit.STREAM_CODEC, new Payloads.ChipEdit(ID, -1, true));
        same(Payloads.ChipEdit.STREAM_CODEC, new Payloads.ChipEdit(ID, 2, false));
        same(Payloads.Recall.STREAM_CODEC, new Payloads.Recall(ID));
        same(Payloads.ParkUnpark.STREAM_CODEC, new Payloads.ParkUnpark(new BlockPos(-5, 70, 1_000_000), ID, true));
        same(Payloads.ApplySkin.STREAM_CODEC, new Payloads.ApplySkin(ID, false,
                Optional.of(Identifier.fromNamespaceAndPath("nerocompanion", "scout_drone/nightwatch"))));
        same(Payloads.ApplySkin.STREAM_CODEC, new Payloads.ApplySkin(ID, true, Optional.empty()));
        same(Payloads.Reclaim.STREAM_CODEC, new Payloads.Reclaim(BlockPos.ZERO, ID));
        same(Payloads.OpenCompanion.STREAM_CODEC, new Payloads.OpenCompanion(ID));
    }

    @Test
    void hudSync() {
        Payloads.HudEntry a = new Payloads.HudEntry(ID, Optional.of(TYPE), "Pip", CompanionMode.SCOUT, 73, 3,
                Payloads.HudEntry.LOADED | Payloads.HudEntry.WORKING, 1234,
                Payloads.HudEntry.modeBits(true, false, CompanionMode.FOLLOW)
                        | Payloads.HudEntry.modeBits(false, true, CompanionMode.GUARD));
        Payloads.HudEntry b = new Payloads.HudEntry(UUID.randomUUID(), Optional.empty(), "", CompanionMode.SLEEP, 0, 0,
                Payloads.HudEntry.SLEEPING | Payloads.HudEntry.LOW_BATTERY, -1, 0);
        Payloads.HudSync back = roundTrip(Payloads.HudSync.STREAM_CODEC, new Payloads.HudSync(List.of(a, b)));
        assertEquals(List.of(a, b), back.entries());
        assertEquals(true, back.entries().get(0).supports(CompanionMode.FOLLOW));
        assertEquals(true, back.entries().get(0).needsChip(CompanionMode.GUARD));
        same(Payloads.HudSync.STREAM_CODEC, new Payloads.HudSync(List.of()));
    }

    @Test
    void companionSnapshot() {
        List<Payloads.ModeState> modes = new ArrayList<>();
        for (CompanionMode mode : CompanionMode.values()) {
            modes.add(new Payloads.ModeState(mode, mode == CompanionMode.GUARD ? Payloads.ModeState.NEEDS_CHIP
                    : Payloads.ModeState.SUPPORTED, mode == CompanionMode.GUARD
                            ? Optional.of(Identifier.fromNamespaceAndPath("nerocompanion", "guard_protocol_chip"))
                            : Optional.empty()));
        }
        Payloads.CompanionSnapshot snapshot = new Payloads.CompanionSnapshot(ID, Optional.of(TYPE), "Pip", 4,
                CompanionMode.FOLLOW, modes, 18_400L, 24_000L, 10L, 40L, 400L, 3, 512, 2, 7,
                List.of(new Payloads.ChipView(Identifier.fromNamespaceAndPath("nerocompanion", "speed"), 2)),
                Optional.empty(), Optional.of(Identifier.fromNamespaceAndPath("nerocompanion", "tophat")),
                List.of("nerocompanion:scout_drone/default"), List.of(), Payloads.CompanionSnapshot.LOADED
                        | Payloads.CompanionSnapshot.CHARGING,
                Payloads.Feedback.ok("gui.nerocompanion.rename.ok", "Pip"));
        same(Payloads.CompanionSnapshot.STREAM_CODEC, snapshot);
        Payloads.CompanionSnapshot denied = new Payloads.CompanionSnapshot(ID, Optional.empty(), "", 0,
                CompanionMode.SLEEP, List.of(), 0L, 100L, 0L, 0L, 0L, 0, -1, 0, 0, List.of(), Optional.empty(),
                Optional.empty(), List.of(), List.of(), Payloads.CompanionSnapshot.AWAY,
                Payloads.Feedback.deny("gui.nerocompanion.reason.exclusive", "@chip.nerocompanion.kind.lamp"));
        same(Payloads.CompanionSnapshot.STREAM_CODEC, denied);
    }

    @Test
    void baySnapshot() {
        Payloads.BaySnapshot snapshot = new Payloads.BaySnapshot(new BlockPos(10, 64, -3), 120_000L, 200_000L,
                List.of(new Payloads.BaySlotView(Payloads.BaySlotView.MINE, ID, Optional.of(TYPE), "Pip", 55,
                                Payloads.BaySlotView.STATE_CHARGING),
                        new Payloads.BaySlotView(Payloads.BaySlotView.FOREIGN, Payloads.BaySlotView.NONE,
                                Optional.of(TYPE), "", 0, Payloads.BaySlotView.STATE_PARKED),
                        Payloads.BaySlotView.empty(), Payloads.BaySlotView.empty()),
                List.of(new Payloads.BayListEntry(UUID.randomUUID(), Optional.of(TYPE), "Rusty", 80, 0L, "")),
                List.of(new Payloads.BayListEntry(UUID.randomUUID(), Optional.of(TYPE), "Bolt", 0, 125L, "50 credits")),
                2, 4, Payloads.Feedback.NONE);
        same(Payloads.BaySnapshot.STREAM_CODEC, snapshot);
    }

    @Test
    void chatterAndScoutMarks() {
        same(Payloads.Chatter.STREAM_CODEC, new Payloads.Chatter(77, "chatter.nerocompanion.curious.3", 0));
        List<Payloads.Mark> marks = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            marks.add(new Payloads.Mark(new BlockPos(i, -i, i * 2), i % 2));
        }
        Payloads.ScoutMarks capped = new Payloads.ScoutMarks(marks, 40);
        assertEquals(Payloads.ScoutMarks.MAX_MARKS, capped.marks().size());
        same(Payloads.ScoutMarks.STREAM_CODEC, capped);
    }

    @Test
    void anOversizedListIsRefusedNotTrusted() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeVarInt(Integer.MAX_VALUE);
        assertThrows(IllegalArgumentException.class, () -> Payloads.HudSync.STREAM_CODEC.decode(buf));
    }
}
