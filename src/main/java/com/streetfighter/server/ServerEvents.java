package com.streetfighter.server;

import com.streetfighter.SFConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Forge-bus game events: peaceful mobs, fight invulnerability, persistence, commands. */
public final class ServerEvents {
    private ServerEvents() {
    }

    /** Mobs never pick a player as their target. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        if (!SFConfig.PEACEFUL_MOBS.get()) return;
        if (event.getEntity() instanceof Mob && event.getNewTarget() instanceof Player) {
            event.setCanceled(true);
        }
    }

    /** Mobs can't hurt players (arrows, explosions, slimes...) and fighters can't be hurt mid-fight. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onAttack(LivingAttackEvent event) {
        if (event.getEntity().level().isClientSide) return;
        if (FightManager.isFighting(event.getEntity())) {
            event.setCanceled(true);
            return;
        }
        if (!SFConfig.PEACEFUL_MOBS.get() || !(event.getEntity() instanceof Player)) return;
        DamageSource source = event.getSource();
        Entity cause = source.getEntity();
        Entity direct = source.getDirectEntity();
        if (cause instanceof Mob || direct instanceof Mob) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent.Post event) {
        FightManager.tick(event.getServer());
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) FightManager.promptDifficultyIfNeeded(player);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        FightManager.abandon(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        PlayerFightData.copy(event.getOriginal(), event.getEntity());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        FightManager.clear();
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        SFCommands.register(event.getDispatcher());
    }
}
