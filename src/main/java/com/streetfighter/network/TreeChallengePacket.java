package com.streetfighter.network;

import com.streetfighter.server.TreeFights;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.network.CustomPayloadEvent;

/** Client -> server: "I held E on this oak log, let's fight the tree!" */
public record TreeChallengePacket(BlockPos pos) {
    public static void encode(TreeChallengePacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
    }

    public static TreeChallengePacket decode(FriendlyByteBuf buf) {
        return new TreeChallengePacket(buf.readBlockPos());
    }

    public static void handle(TreeChallengePacket msg, CustomPayloadEvent.Context ctx) {
        ServerPlayer player = ctx.getSender();
        if (player != null) TreeFights.challenge(player, msg.pos);
        ctx.setPacketHandled(true);
    }
}
