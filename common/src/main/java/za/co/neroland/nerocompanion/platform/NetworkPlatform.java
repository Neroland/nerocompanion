package za.co.neroland.nerocompanion.platform;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

public interface NetworkPlatform {
    boolean sendToPlayer(ServerPlayer player, CustomPacketPayload payload);
    void sendToServer(CustomPacketPayload payload);
}
