package za.co.neroland.nerocompanion.fabric;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

import za.co.neroland.nerocompanion.network.CompanionNetwork;
import za.co.neroland.nerocompanion.platform.NetworkPlatform;

/**
 * Fabric side of the networking seam (the {@code nerocompanion:main} payloads). {@link #registerCommon()}
 * (mod init, both sides) registers the payload types and the serverbound receivers;
 * {@link #registerClient()} (client init) registers the clientbound receivers, keeping
 * {@code ClientPlayNetworking} off the dedicated server. Registered as the {@link NetworkPlatform}
 * implementation via {@code META-INF/services}.
 *
 * <p>Receivers hop to the client / server thread before running a handler, which is what makes
 * plain-data mirror caches and saved-data access safe without locking.
 */
public final class FabricCompanionNetwork implements NetworkPlatform {

    /** Mod-init (both sides): clientbound payload types, plus serverbound types and receivers. */
    public static void registerCommon() {
        for (CompanionNetwork.Clientbound<?> cb : CompanionNetwork.clientbound()) {
            registerClientboundType(cb);
        }
        for (CompanionNetwork.Serverbound<?> sb : CompanionNetwork.serverbound()) {
            registerServerbound(sb);
        }
    }

    /** Client-init: clientbound receivers (client-only API). */
    public static void registerClient() {
        for (CompanionNetwork.Clientbound<?> cb : CompanionNetwork.clientbound()) {
            registerClientReceiver(cb);
        }
    }

    private static <T extends CustomPacketPayload> void registerClientboundType(
            CompanionNetwork.Clientbound<T> cb) {
        PayloadTypeRegistry.clientboundPlay().register(cb.type(), cb.codec());
    }

    private static <T extends CustomPacketPayload> void registerClientReceiver(
            CompanionNetwork.Clientbound<T> cb) {
        ClientPlayNetworking.registerGlobalReceiver(cb.type(), (payload, context) ->
                context.client().execute(() -> cb.handler().accept(payload)));
    }

    private static <T extends CustomPacketPayload> void registerServerbound(
            CompanionNetwork.Serverbound<T> sb) {
        PayloadTypeRegistry.serverboundPlay().register(sb.type(), sb.codec());
        ServerPlayNetworking.registerGlobalReceiver(sb.type(), (payload, context) -> {
            ServerPlayer player = context.player();
            player.level().getServer().execute(() -> sb.handler().accept(payload, player));
        });
    }

    @Override
    public boolean sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        if (!ServerPlayNetworking.canSend(player, payload.type())) return false;
        ServerPlayNetworking.send(player, payload);
        return true;
    }

    @Override
    public void sendToServer(CustomPacketPayload payload) {
        ClientPlayNetworking.send(payload);
    }
}
