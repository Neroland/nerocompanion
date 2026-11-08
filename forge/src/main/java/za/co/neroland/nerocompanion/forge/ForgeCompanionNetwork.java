package za.co.neroland.nerocompanion.forge;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.Channel;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.payload.PayloadFlow;

import za.co.neroland.nerocompanion.NeroCompanionCommon;
import za.co.neroland.nerocompanion.network.CompanionNetwork;
import za.co.neroland.nerocompanion.platform.NetworkPlatform;

/**
 * Forge side of the cross-loader packet seam, on NeroCompanion' own {@code nerocompanion:main} channel.
 * Registered as the {@link NetworkPlatform} implementation via {@code META-INF/services}.
 *
 * <p>Forge seals a channel at {@code build()}, so every payload must be declared before then — which
 * is why NeroCompanion owns this channel rather than adding to Neroland Core's, and why
 * {@link #register()} must run <b>after</b> {@code NeroCompanionCommon.init()}, where the payloads are
 * declared. {@code optional()} keeps a NeroCompanion-less client connectable. While no payload is
 * declared the channel is not built; attempting to send then raises an error.
 */
public final class ForgeCompanionNetwork implements NetworkPlatform {

    private static Channel<CustomPacketPayload> channel;

    public static void register() {
        if (!CompanionNetwork.hasPayloads()) {
            NeroCompanionCommon.LOGGER.debug("[NeroCompanion] no payloads declared; nerocompanion:main channel not built");
            return;
        }
        PayloadFlow<RegistryFriendlyByteBuf, CustomPacketPayload> play =
                ChannelBuilder.named(Identifier.fromNamespaceAndPath(NeroCompanionCommon.MOD_ID, CompanionNetwork.CHANNEL))
                        .optional()
                        .payloadChannel()
                        .play()
                        .bidirectional();
        for (CompanionNetwork.Clientbound<?> cb : CompanionNetwork.clientbound()) {
            registerClientbound(play, cb);
        }
        for (CompanionNetwork.Serverbound<?> sb : CompanionNetwork.serverbound()) {
            registerServerbound(play, sb);
        }
        channel = play.build();
    }

    private static <T extends CustomPacketPayload> void registerClientbound(
            PayloadFlow<RegistryFriendlyByteBuf, CustomPacketPayload> play,
            CompanionNetwork.Clientbound<T> cb) {
        // One bidirectional flow: a clientbound payload is only ever accepted from the server (no sender). A client
        // (a LAN guest on an integrated server) sending one is ignored.
        play.addMain(cb.type(), registryCodec(cb.codec()), (payload, context) -> {
            if (context.getSender() == null) {
                cb.handler().accept(payload);
            }
        });
    }

    /** Both directions share the one bidirectional flow; the sender is what distinguishes them. */
    private static <T extends CustomPacketPayload> void registerServerbound(
            PayloadFlow<RegistryFriendlyByteBuf, CustomPacketPayload> play,
            CompanionNetwork.Serverbound<T> sb) {
        play.addMain(sb.type(), registryCodec(sb.codec()), (payload, context) -> {
            if (context.getSender() instanceof ServerPlayer serverPlayer) {
                sb.handler().accept(payload, serverPlayer);
            }
        });
    }

    @SuppressWarnings("unchecked")
    private static <T extends CustomPacketPayload> StreamCodec<RegistryFriendlyByteBuf, T> registryCodec(
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
        return (StreamCodec<RegistryFriendlyByteBuf, T>) codec;
    }

    @Override
    public boolean sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        if (channel == null) throw new IllegalStateException("NeroCompanion network channel is not registered");
        if (!channel.isRemotePresent(player.connection.getConnection())) return false;
        channel.send(payload, PacketDistributor.PLAYER.with(player));
        return true;
    }

    @Override
    public void sendToServer(CustomPacketPayload payload) {
        if (channel == null) throw new IllegalStateException("NeroCompanion network channel is not registered");
        channel.send(payload, PacketDistributor.SERVER.noArg());
    }
}
