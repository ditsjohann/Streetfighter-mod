package com.streetfighter.client;

import com.streetfighter.network.OpenFightPacket;

/** Client-only packet handling, kept separate so dedicated servers never load client classes. */
public final class ClientPacketHandler {
    private ClientPacketHandler() {
    }

    public static void openFight(OpenFightPacket msg) {
        FightSession.start(msg);
    }

    public static void promptDifficulty() {
        ClientEvents.pendingDifficultyPrompt = true;
    }
}
