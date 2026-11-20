package com.streetfighter.client;

import com.streetfighter.network.OpenFightPacket;
import net.minecraft.client.Minecraft;

/** Client-only packet handling, kept separate so dedicated servers never load client classes. */
public final class ClientPacketHandler {
    private ClientPacketHandler() {
    }

    public static void openFight(OpenFightPacket msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.setScreen(new FightScreen(msg));
    }

    public static void promptDifficulty() {
        ClientEvents.pendingDifficultyPrompt = true;
    }
}
