package com.streetfighter.network;

import com.streetfighter.server.FightManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.network.CustomPayloadEvent;

import java.util.ArrayList;
import java.util.List;

/** Client -> server: a fighter was smashed through these soft wall blocks. The server re-validates everything. */
public record WallBreakPacket(List<BlockPos> positions) {
    public static void encode(WallBreakPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(Math.min(8, msg.positions.size()));
        for (int i = 0; i < Math.min(8, msg.positions.size()); i++) buf.writeBlockPos(msg.positions.get(i));
    }

    public static WallBreakPacket decode(FriendlyByteBuf buf) {
        int n = Math.min(8, buf.readVarInt());
        List<BlockPos> list = new ArrayList<>();
        for (int i = 0; i < n; i++) list.add(buf.readBlockPos());
        return new WallBreakPacket(list);
    }

    public static void handle(WallBreakPacket msg, CustomPayloadEvent.Context ctx) {
        ServerPlayer player = ctx.getSender();
        if (player != null) FightManager.breakWall(player, msg.positions);
        ctx.setPacketHandled(true);
    }
}
