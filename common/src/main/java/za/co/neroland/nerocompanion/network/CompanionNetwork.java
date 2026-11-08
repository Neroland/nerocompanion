package za.co.neroland.nerocompanion.network;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.concurrent.atomic.AtomicBoolean;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

import za.co.neroland.nerocompanion.ClientBridge;
import za.co.neroland.nerocompanion.network.payload.Payloads;

/**
 * NeroCompanion's own payload channel, {@code nerocompanion:main}. Every payload is declared in
 * {@link #init()} (common init step 6), before each loader builds its channel; Core's network is never
 * reused. Serverbound handlers receive the sending {@link ServerPlayer} and must check ownership
 * themselves: the client only ever sends intents.
 */
public final class CompanionNetwork {
    public record Clientbound<T extends CustomPacketPayload>(CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec, Consumer<T> handler) { }
    public record Serverbound<T extends CustomPacketPayload>(CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec, BiConsumer<T, ServerPlayer> handler) { }

    public static final String CHANNEL = "main";
    public static final String PROTOCOL_VERSION = "1";
    private static final List<Clientbound<?>> CLIENTBOUND = new ArrayList<>();
    private static final List<Serverbound<?>> SERVERBOUND = new ArrayList<>();
    private static final AtomicBoolean DECLARED = new AtomicBoolean();

    private CompanionNetwork() { }

    public static <T extends CustomPacketPayload> void clientbound(CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec, Consumer<T> handler) {
        CLIENTBOUND.add(new Clientbound<>(type, codec, handler));
    }

    public static <T extends CustomPacketPayload> void serverbound(CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec, BiConsumer<T, ServerPlayer> handler) {
        SERVERBOUND.add(new Serverbound<>(type, codec, handler));
    }

    public static List<Clientbound<?>> clientbound() { return Collections.unmodifiableList(CLIENTBOUND); }
    public static List<Serverbound<?>> serverbound() { return Collections.unmodifiableList(SERVERBOUND); }
    public static boolean hasPayloads() { return !CLIENTBOUND.isEmpty() || !SERVERBOUND.isEmpty(); }

    public static void init() {
        if (DECLARED.compareAndSet(false, true)) {
            // Client to server: intents, each re-validated by ServerActions.
            serverbound(Payloads.SetMode.TYPE, Payloads.SetMode.STREAM_CODEC, ServerActions::setMode);
            serverbound(Payloads.Rename.TYPE, Payloads.Rename.STREAM_CODEC, ServerActions::rename);
            serverbound(Payloads.ChipEdit.TYPE, Payloads.ChipEdit.STREAM_CODEC, ServerActions::chipEdit);
            serverbound(Payloads.Recall.TYPE, Payloads.Recall.STREAM_CODEC, ServerActions::recall);
            serverbound(Payloads.ParkUnpark.TYPE, Payloads.ParkUnpark.STREAM_CODEC, ServerActions::parkUnpark);
            serverbound(Payloads.ApplySkin.TYPE, Payloads.ApplySkin.STREAM_CODEC, ServerActions::applySkin);
            serverbound(Payloads.Reclaim.TYPE, Payloads.Reclaim.STREAM_CODEC, ServerActions::reclaim);
            serverbound(Payloads.OpenCompanion.TYPE, Payloads.OpenCompanion.STREAM_CODEC, ServerActions::openCompanion);
            // Server to client: owner-only views, handed to the client through ClientBridge.
            clientbound(Payloads.HudSync.TYPE, Payloads.HudSync.STREAM_CODEC, p -> ClientBridge.get().onHudSync(p));
            clientbound(Payloads.CompanionSnapshot.TYPE, Payloads.CompanionSnapshot.STREAM_CODEC,
                    p -> ClientBridge.get().onCompanionSnapshot(p));
            clientbound(Payloads.BaySnapshot.TYPE, Payloads.BaySnapshot.STREAM_CODEC,
                    p -> ClientBridge.get().onBaySnapshot(p));
            clientbound(Payloads.Chatter.TYPE, Payloads.Chatter.STREAM_CODEC, p -> ClientBridge.get().onChatter(p));
            clientbound(Payloads.ScoutMarks.TYPE, Payloads.ScoutMarks.STREAM_CODEC,
                    p -> ClientBridge.get().onScoutMarks(p));
            clientbound(Payloads.LookCatalogue.TYPE, Payloads.LookCatalogue.STREAM_CODEC,
                    p -> ClientBridge.get().onLookCatalogue(p));
        }
    }
}
