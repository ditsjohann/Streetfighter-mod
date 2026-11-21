package com.streetfighter.network;

import com.streetfighter.StreetFighterMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.SimpleChannel;

public final class ModNetwork {
    public static SimpleChannel CHANNEL;

    private ModNetwork() {
    }

    public static void register() {
        CHANNEL = ChannelBuilder.named(ResourceLocation.fromNamespaceAndPath(StreetFighterMod.MODID, "main"))
                .networkProtocolVersion(1)
                .simpleChannel();

        CHANNEL.messageBuilder(ChallengePacket.class, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ChallengePacket::encode).decoder(ChallengePacket::decode)
                .consumerMainThread(ChallengePacket::handle).add();
        CHANNEL.messageBuilder(FightResultPacket.class, NetworkDirection.PLAY_TO_SERVER)
                .encoder(FightResultPacket::encode).decoder(FightResultPacket::decode)
                .consumerMainThread(FightResultPacket::handle).add();
        CHANNEL.messageBuilder(SetDifficultyPacket.class, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SetDifficultyPacket::encode).decoder(SetDifficultyPacket::decode)
                .consumerMainThread(SetDifficultyPacket::handle).add();
        CHANNEL.messageBuilder(FightSyncPacket.class, NetworkDirection.PLAY_TO_SERVER)
                .encoder(FightSyncPacket::encode).decoder(FightSyncPacket::decode)
                .consumerMainThread(FightSyncPacket::handle).add();
        CHANNEL.messageBuilder(WallBreakPacket.class, NetworkDirection.PLAY_TO_SERVER)
                .encoder(WallBreakPacket::encode).decoder(WallBreakPacket::decode)
                .consumerMainThread(WallBreakPacket::handle).add();
        CHANNEL.messageBuilder(OpenFightPacket.class, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(OpenFightPacket::encode).decoder(OpenFightPacket::decode)
                .consumerMainThread(OpenFightPacket::handle).add();
        CHANNEL.messageBuilder(DifficultyPromptPacket.class, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(DifficultyPromptPacket::encode).decoder(DifficultyPromptPacket::decode)
                .consumerMainThread(DifficultyPromptPacket::handle).add();
        CHANNEL.build();
    }

    public static void sendToServer(Object msg) {
        CHANNEL.send(msg, PacketDistributor.SERVER.noArg());
    }

    public static void sendToPlayer(ServerPlayer player, Object msg) {
        CHANNEL.send(msg, PacketDistributor.PLAYER.with(player));
    }
}
