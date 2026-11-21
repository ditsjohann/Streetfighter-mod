package com.streetfighter.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.network.CustomPayloadEvent;
import net.minecraftforge.fml.loading.FMLEnvironment;

import java.util.ArrayList;
import java.util.List;

/** Server -> client: the challenge was accepted, open the fight screen. */
public record OpenFightPacket(int entityId, String enemyType, String enemyName, float hpScale, float damageScale,
                              String difficulty, List<String> unlockedStyles, boolean training) {
    public static void encode(OpenFightPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.entityId);
        buf.writeUtf(msg.enemyType, 32);
        buf.writeUtf(msg.enemyName, 128);
        buf.writeFloat(msg.hpScale);
        buf.writeFloat(msg.damageScale);
        buf.writeUtf(msg.difficulty, 32);
        buf.writeVarInt(msg.unlockedStyles.size());
        for (String s : msg.unlockedStyles) buf.writeUtf(s, 32);
        buf.writeBoolean(msg.training);
    }

    public static OpenFightPacket decode(FriendlyByteBuf buf) {
        int id = buf.readVarInt();
        String type = buf.readUtf(32);
        String name = buf.readUtf(128);
        float hp = buf.readFloat();
        float dmg = buf.readFloat();
        String diff = buf.readUtf(32);
        int n = Math.min(buf.readVarInt(), 64);
        List<String> styles = new ArrayList<>();
        for (int i = 0; i < n; i++) styles.add(buf.readUtf(32));
        boolean training = buf.readBoolean();
        return new OpenFightPacket(id, type, name, hp, dmg, diff, styles, training);
    }

    public static void handle(OpenFightPacket msg, CustomPayloadEvent.Context ctx) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            com.streetfighter.client.ClientPacketHandler.openFight(msg);
        }
        ctx.setPacketHandled(true);
    }
}
