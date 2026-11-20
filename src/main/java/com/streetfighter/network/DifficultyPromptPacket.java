package com.streetfighter.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.network.CustomPayloadEvent;
import net.minecraftforge.fml.loading.FMLEnvironment;

/** Server -> client: this world has no AI difficulty yet, please pick one. */
public record DifficultyPromptPacket() {
    public static void encode(DifficultyPromptPacket msg, FriendlyByteBuf buf) {
    }

    public static DifficultyPromptPacket decode(FriendlyByteBuf buf) {
        return new DifficultyPromptPacket();
    }

    public static void handle(DifficultyPromptPacket msg, CustomPayloadEvent.Context ctx) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            com.streetfighter.client.ClientPacketHandler.promptDifficulty();
        }
        ctx.setPacketHandled(true);
    }
}
