package za.co.neroland.nerocompanion.forge;

//? if >=1.20.5 {
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
//?} else {
/*import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
//? if >=1.20.2 {
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.SimpleChannel;
//?} else {
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
//?}

import za.co.neroland.nerocompanion.NeroCompanionCommon;
import za.co.neroland.nerocompanion.network.CompanionNetwork;
import za.co.neroland.nerolandcore.network.legacy.LegacyPayload;
import za.co.neroland.nerocompanion.platform.NetworkPlatform;

public final class ForgeCompanionNetwork implements NetworkPlatform {
    private static final int PROTOCOL_VERSION = 1;
    private static SimpleChannel channel;

    public static void register() {
        //? if >=1.20.2 {
        channel = ChannelBuilder.named(new ResourceLocation(NeroCompanionCommon.MOD_ID, "main"))
                .networkProtocolVersion(PROTOCOL_VERSION).optional().simpleChannel();
        //?} else {
        channel = NetworkRegistry.newSimpleChannel(new ResourceLocation(NeroCompanionCommon.MOD_ID, "main"),
                () -> Integer.toString(PROTOCOL_VERSION), ForgeCompanionNetwork::acceptVersion, ForgeCompanionNetwork::acceptVersion);
        //?}
        int id = 0;
        for (CompanionNetwork.Clientbound<?> packet : CompanionNetwork.clientbound()) {
            registerClientbound(packet, id++);
        }
        for (CompanionNetwork.Serverbound<?> packet : CompanionNetwork.serverbound()) {
            registerServerbound(packet, id++);
        }
    }

    //? if <1.20.2 {
    private static boolean acceptVersion(String version) {
        return Integer.toString(PROTOCOL_VERSION).equals(version) || NetworkRegistry.ABSENT.version().equals(version)
                || NetworkRegistry.ACCEPTVANILLA.equals(version);
    }
    //?}

    private static <T extends LegacyPayload> void registerClientbound(CompanionNetwork.Clientbound<T> packet, int id) {
        channel.messageBuilder(packet.type().payloadClass(), id, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((payload, buffer) -> packet.codec().encode(buffer, payload))
                .decoder(packet.codec()::decode)
                .consumerMainThread((payload, context) -> packet.handler().accept(payload))
                .add();
    }

    private static <T extends LegacyPayload> void registerServerbound(CompanionNetwork.Serverbound<T> packet, int id) {
        channel.messageBuilder(packet.type().payloadClass(), id, NetworkDirection.PLAY_TO_SERVER)
                .encoder((payload, buffer) -> packet.codec().encode(buffer, payload))
                .decoder(packet.codec()::decode)
                .consumerMainThread((payload, context) -> {
                    //? if >=1.20.2 {
                    ServerPlayer player = context.getSender();
                    //?} else {
                    ServerPlayer player = context.get().getSender();
                    //?}
                    if (player != null) {
                        packet.handler().accept(payload, player);
                    }
                })
                .add();
    }

    @Override
    public boolean sendToPlayer(ServerPlayer player, LegacyPayload payload) {
        //? if >=1.20.2 {
        var connection = player.connection.getConnection();
        //?} else {
        var connection = player.connection.connection;
        //?}
        if (channel != null && channel.isRemotePresent(connection)) {
            //? if >=1.20.2 {
            channel.send(payload, connection);
            //?} else {
            channel.send(PacketDistributor.PLAYER.with(() -> player), payload);
            //?}
            return true;
        }
        return false;
    }

    @Override
    public void sendToServer(LegacyPayload payload) {
        var connection = Minecraft.getInstance().getConnection();
        if (channel != null && connection != null && channel.isRemotePresent(connection.getConnection())) {
            //? if >=1.20.2 {
            channel.send(payload, connection.getConnection());
            //?} else {
            channel.sendToServer(payload);
            //?}
        }
    }
}
*///?}
