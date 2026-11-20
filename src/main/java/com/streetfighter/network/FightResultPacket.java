package com.streetfighter.network;

import com.streetfighter.server.FightManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.network.CustomPayloadEvent;

/** Client -> server: the match is over (or the player backed out before it started). */
public record FightResultPacket(boolean won, int roundsWon, int roundsLost, boolean perfect, boolean cancelled) {
    public static void encode(FightResultPacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.won);
        buf.writeVarInt(msg.roundsWon);
        buf.writeVarInt(msg.roundsLost);
        buf.writeBoolean(msg.perfect);
        buf.writeBoolean(msg.cancelled);
    }

    public static FightResultPacket decode(FriendlyByteBuf buf) {
        return new FightResultPacket(buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean(),
                buf.readBoolean());
    }

    public static void handle(FightResultPacket msg, CustomPayloadEvent.Context ctx) {
        ServerPlayer player = ctx.getSender();
        if (player != null) FightManager.finishFight(player, msg);
        ctx.setPacketHandled(true);
    }
}
