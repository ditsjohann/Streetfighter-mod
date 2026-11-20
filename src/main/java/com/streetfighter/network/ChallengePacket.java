package com.streetfighter.network;

import com.streetfighter.server.FightManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.network.CustomPayloadEvent;

/** Client -> server: "I held E on this mob, let's fight!" */
public record ChallengePacket(int entityId) {
    public static void encode(ChallengePacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.entityId);
    }

    public static ChallengePacket decode(FriendlyByteBuf buf) {
        return new ChallengePacket(buf.readVarInt());
    }

    public static void handle(ChallengePacket msg, CustomPayloadEvent.Context ctx) {
        ServerPlayer player = ctx.getSender();
        if (player != null) FightManager.requestFight(player, msg.entityId);
        ctx.setPacketHandled(true);
    }
}
