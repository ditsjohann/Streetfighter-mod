package com.streetfighter.network;

import com.streetfighter.fight.AIDifficulty;
import com.streetfighter.server.FightManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.network.CustomPayloadEvent;

/** Client -> server: the difficulty chosen on the world-start prompt. */
public record SetDifficultyPacket(String difficulty) {
    public static void encode(SetDifficultyPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.difficulty, 32);
    }

    public static SetDifficultyPacket decode(FriendlyByteBuf buf) {
        return new SetDifficultyPacket(buf.readUtf(32));
    }

    public static void handle(SetDifficultyPacket msg, CustomPayloadEvent.Context ctx) {
        ServerPlayer player = ctx.getSender();
        AIDifficulty d = AIDifficulty.byId(msg.difficulty);
        if (player != null && d != null) FightManager.chooseInitialDifficulty(player, d);
        ctx.setPacketHandled(true);
    }
}
