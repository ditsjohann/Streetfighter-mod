package com.streetfighter.network;

import com.streetfighter.server.FightManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.network.CustomPayloadEvent;

/** Client -> server: where the opponent currently stands on the fight line (sent ~10x per second). */
public record FightSyncPacket(double x, double y, double z, float yaw) {
    public static void encode(FightSyncPacket msg, FriendlyByteBuf buf) {
        buf.writeDouble(msg.x);
        buf.writeDouble(msg.y);
        buf.writeDouble(msg.z);
        buf.writeFloat(msg.yaw);
    }

    public static FightSyncPacket decode(FriendlyByteBuf buf) {
        return new FightSyncPacket(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat());
    }

    public static void handle(FightSyncPacket msg, CustomPayloadEvent.Context ctx) {
        ServerPlayer player = ctx.getSender();
        if (player != null && Double.isFinite(msg.x) && Double.isFinite(msg.y) && Double.isFinite(msg.z)) {
            FightManager.syncMob(player, msg.x, msg.y, msg.z, msg.yaw);
        }
        ctx.setPacketHandled(true);
    }
}
