package com.streetfighter.server;

import com.streetfighter.SFConfig;
import com.streetfighter.fight.AIDifficulty;
import com.streetfighter.fight.FighterType;
import com.streetfighter.network.DifficultyPromptPacket;
import com.streetfighter.network.FightResultPacket;
import com.streetfighter.network.ModNetwork;
import com.streetfighter.network.OpenFightPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** Server-side bookkeeping of running fights. */
public final class FightManager {
    private static final long TIMEOUT_TICKS = 20L * 60 * 15;

    private record Session(UUID playerId, ServerLevel level, int mobId, UUID mobUuid, FighterType type,
                           String mobName, boolean hostile, boolean prevNoAi, long startTick) {
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private FightManager() {
    }

    // ------------------------------------------------------------------ queries

    public static boolean isFighting(Entity entity) {
        if (SESSIONS.isEmpty()) return false;
        if (SESSIONS.containsKey(entity.getUUID())) return true;
        for (Session s : SESSIONS.values()) {
            if (s.mobUuid.equals(entity.getUUID())) return true;
        }
        return false;
    }

    public static boolean canChooseDifficulty(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        return player.hasPermissions(2) || (server != null && server.isSingleplayerOwner(player.getGameProfile()));
    }

    // ------------------------------------------------------------------ lifecycle

    public static void requestFight(ServerPlayer player, int entityId) {
        if (SESSIONS.containsKey(player.getUUID())) return;
        ServerLevel level = player.serverLevel();
        Entity entity = level.getEntity(entityId);
        if (!(entity instanceof Mob mob) || !mob.isAlive()) return;
        double range = SFConfig.CHALLENGE_RANGE.get() + 2.0;
        if (mob.distanceToSqr(player) > range * range) return;
        if (isFighting(mob)) {
            player.displayClientMessage(Component.literal("That mob is already in a fight!").withStyle(ChatFormatting.RED), true);
            return;
        }
        if (player.isSpectator()) return;

        WorldFightData data = WorldFightData.get(player.server);
        AIDifficulty difficulty = data.difficultyOrDefault();
        FighterType type = FighterType.fromEntity(mob);

        float hpScale = 1f, dmgScale = 1f;
        if (type == FighterType.BRAWLER) {
            hpScale = clamp(mob.getMaxHealth() / 20f, 0.6f, 2.2f);
            AttributeInstance attack = mob.getAttribute(Attributes.ATTACK_DAMAGE);
            float atk = attack != null ? (float) attack.getValue() : 2f;
            dmgScale = clamp(0.75f + atk / 12f, 0.75f, 1.6f);
        }

        boolean prevNoAi = mob.isNoAi();
        mob.setNoAi(true);
        mob.getNavigation().stop();
        mob.setDeltaMovement(Vec3.ZERO);
        mob.setTarget(null);

        String name = mob.getDisplayName().getString();
        boolean hostile = mob instanceof Enemy;
        SESSIONS.put(player.getUUID(), new Session(player.getUUID(), level, mob.getId(), mob.getUUID(), type, name,
                hostile, prevNoAi, level.getGameTime()));

        level.playSound(null, mob.blockPosition(), SoundEvents.RAID_HORN.value(), SoundSource.PLAYERS, 0.6f, 1.6f);
        ModNetwork.sendToPlayer(player, new OpenFightPacket(mob.getId(), type.id, name, hpScale, dmgScale,
                difficulty.id, PlayerFightData.unlockedStyles(player)));
    }

    public static void finishFight(ServerPlayer player, FightResultPacket result) {
        Session s = SESSIONS.remove(player.getUUID());
        if (s == null) return;
        Entity entity = s.level.getEntity(s.mobUuid);
        Mob mob = entity instanceof Mob m && m.isAlive() ? m : null;
        if (mob != null) {
            mob.setNoAi(s.prevNoAi);
            mob.fallDistance = 0;
        }
        player.fallDistance = 0;

        if (result.cancelled()) {
            player.displayClientMessage(Component.literal("You backed out of the fight.").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        if (result.won()) {
            onPlayerWon(player, s, mob, result);
        } else {
            onPlayerLost(player, s, mob);
        }
    }

    private static void onPlayerWon(ServerPlayer player, Session s, Mob mob, FightResultPacket result) {
        PlayerFightData.recordWin(player, s.type == FighterType.BRAWLER ? "brawler" : s.type.id);
        player.sendSystemMessage(Component.literal("K.O.! ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                .append(Component.literal("You defeated " + s.mobName + " (" + result.roundsWon() + "-" + result.roundsLost() + ")"
                        + (result.perfect() ? " - PERFECT!" : "!")).withStyle(ChatFormatting.YELLOW)));
        if (PlayerFightData.unlock(player, s.type)) {
            player.sendSystemMessage(Component.literal("You learned the " + s.type.displayName + " style: " + s.type.title
                    + "! Pick it at the start of your next fight.").withStyle(ChatFormatting.AQUA));
            player.level().playSound(null, player.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.8f, 1f);
        } else {
            player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8f, 1.2f);
        }
        player.giveExperiencePoints(result.perfect() ? 15 : 8);

        if (mob == null) return;
        boolean dies = s.hostile ? SFConfig.DEFEATED_HOSTILES_DIE.get() : SFConfig.DEFEATED_PASSIVES_DIE.get();
        if (dies) {
            mob.setLastHurtByPlayer(player);
            mob.hurt(player.damageSources().playerAttack(player), Math.max(1000f, mob.getMaxHealth() * 10f));
        } else {
            s.level.sendParticles(ParticleTypes.HAPPY_VILLAGER, mob.getX(), mob.getY() + mob.getBbHeight() + 0.3, mob.getZ(),
                    10, 0.4, 0.3, 0.4, 0.05);
            Vec3 push = mob.position().subtract(player.position()).normalize().scale(0.6);
            mob.push(push.x, 0.3, push.z);
            mob.hurtMarked = true;
        }
    }

    private static void onPlayerLost(ServerPlayer player, Session s, Mob mob) {
        player.sendSystemMessage(Component.literal("DEFEAT... ").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)
                .append(Component.literal(s.mobName + " beat you. Train up and try again!").withStyle(ChatFormatting.RED)));
        if (SFConfig.LOSER_PENALTY.get()) {
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 20 * 30, 0));
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20 * 30, 0));
        }
        if (mob != null) {
            s.level.sendParticles(ParticleTypes.ANGRY_VILLAGER, mob.getX(), mob.getY() + mob.getBbHeight() + 0.3, mob.getZ(),
                    5, 0.3, 0.2, 0.3, 0.05);
            Vec3 push = player.position().subtract(mob.position()).normalize().scale(0.8);
            player.push(push.x, 0.35, push.z);
            player.hurtMarked = true;
        }
    }

    /** The client moved the mob along the fight line; mirror it on the server so everyone sees it. */
    public static void syncMob(ServerPlayer player, double x, double y, double z, float yaw) {
        Session s = SESSIONS.get(player.getUUID());
        if (s == null) return;
        Entity entity = s.level.getEntity(s.mobUuid);
        if (!(entity instanceof Mob mob) || !mob.isAlive()) return;
        // only accept positions near the fight (the arena is at most ~22 blocks long)
        if (mob.distanceToSqr(x, y, z) > 20 * 20 || player.distanceToSqr(x, y, z) > 24 * 24) return;
        mob.moveTo(x, y, z, yaw, mob.getXRot());
        mob.setYHeadRot(yaw);
        mob.setYBodyRot(yaw);
        mob.setDeltaMovement(Vec3.ZERO);
    }

    /** Ends a fight without a result (player left, mob vanished...). */
    public static void abandon(UUID playerId) {
        Session s = SESSIONS.remove(playerId);
        if (s == null) return;
        Entity entity = s.level.getEntity(s.mobUuid);
        if (entity instanceof Mob mob) mob.setNoAi(s.prevNoAi);
    }

    public static void tick(MinecraftServer server) {
        if (SESSIONS.isEmpty()) return;
        Iterator<Map.Entry<UUID, Session>> it = SESSIONS.entrySet().iterator();
        while (it.hasNext()) {
            Session s = it.next().getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(s.playerId);
            boolean timedOut = s.level.getGameTime() - s.startTick > TIMEOUT_TICKS;
            if (player == null || timedOut) {
                it.remove();
                Entity entity = s.level.getEntity(s.mobUuid);
                if (entity instanceof Mob mob) mob.setNoAi(s.prevNoAi);
                continue;
            }
            // keep the opponent frozen and looking at the player
            Entity entity = s.level.getEntity(s.mobUuid);
            player.clearFire();
            player.fallDistance = 0;
            if (entity instanceof Mob mob && mob.isAlive()) {
                mob.clearFire(); // no burning in daylight mid-fight
                mob.fallDistance = 0;
            }
        }
    }

    public static void clear() {
        SESSIONS.clear();
    }

    // ------------------------------------------------------------------ difficulty

    public static void promptDifficultyIfNeeded(ServerPlayer player) {
        WorldFightData data = WorldFightData.get(player.server);
        if (data.difficulty() == null && canChooseDifficulty(player)) {
            ModNetwork.sendToPlayer(player, new DifficultyPromptPacket());
        }
    }

    public static void chooseInitialDifficulty(ServerPlayer player, AIDifficulty difficulty) {
        WorldFightData data = WorldFightData.get(player.server);
        if (data.difficulty() != null && !player.hasPermissions(2)) return;
        if (!canChooseDifficulty(player)) return;
        data.setDifficulty(player.server, difficulty);
        player.server.getPlayerList().broadcastSystemMessage(
                Component.literal("[Street Fighter] Mob AI difficulty set to ").withStyle(ChatFormatting.GOLD)
                        .append(Component.literal(difficulty.displayName.toUpperCase()).withStyle(
                                difficulty == AIDifficulty.HARD ? ChatFormatting.RED : ChatFormatting.GREEN, ChatFormatting.BOLD)),
                false);
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    /** True if the attacker counts as a mob attacking a player (used for the peaceful-mobs rule). */
    public static boolean isMobAggression(Entity attacker, LivingEntity target) {
        return attacker instanceof Mob && target instanceof net.minecraft.world.entity.player.Player;
    }
}
