package com.streetfighter.server;

import com.streetfighter.SFConfig;
import com.streetfighter.entity.ModEntities;
import com.streetfighter.entity.PickaxeFighterEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * You can't just craft a stone pickaxe: taking it out of the crafting table makes it come alive and fight you.
 * The table closes (the ingredients go back to your inventory) and the pickaxe hops out in front of you.
 */
public final class PickaxeFights {
    private static final Map<UUID, Long> COOLDOWN = new HashMap<>();

    private PickaxeFights() {
    }

    /** Called from the result-slot mixin on both sides; true = the pickaxe can't be taken (yet). */
    public static boolean blocksPickup(Player player, boolean sword) {
        if (!(sword ? SFConfig.IRON_SWORD_CHALLENGE : SFConfig.PICKAXE_CHALLENGE).get() || player.isCreative()
                || player.isSpectator()) return false;
        if (player instanceof ServerPlayer sp) {
            long now = sp.serverLevel().getGameTime();
            Long last = COOLDOWN.get(sp.getUUID());
            if (last == null || now - last > 40) {
                COOLDOWN.put(sp.getUUID(), now);
                sp.server.execute(() -> challenge(sp, sword)); // after the click has been processed
            }
        }
        return true;
    }

    private static void challenge(ServerPlayer player, boolean sword) {
        if (FightManager.isFighting(player) || !player.isAlive()) return;
        player.closeContainer(); // returns the grid ingredients to the player
        ServerLevel level = player.serverLevel();
        PickaxeFighterEntity pick = (sword ? ModEntities.IRON_SWORD : ModEntities.STONE_PICKAXE).get().create(level);
        if (pick == null) return;
        Vec3 ahead = player.position().add(Vec3.directionFromRotation(0, player.getYRot()).scale(3));
        Vec3 spot = OreFights.standingSpot(level, player, ahead);
        OreFights.place(pick, player, spot);
        pick.setBlocks(net.minecraft.core.BlockPos.containing(spot), java.util.List.of());
        level.addFreshEntity(pick);
        level.sendParticles(ParticleTypes.POOF, spot.x, spot.y + 0.8, spot.z, 20, 0.3, 0.5, 0.3, 0.05);
        level.playSound(null, pick.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.6f, 1.8f);
        level.playSound(null, pick.blockPosition(), sword ? SoundEvents.PLAYER_ATTACK_SWEEP : SoundEvents.ITEM_BREAK,
                SoundSource.PLAYERS, 1f, 0.6f);
        player.displayClientMessage(Component.literal(sword ? "The iron sword draws itself! Beat it to claim it."
                        : "The stone pickaxe refuses to be crafted! Beat it first.")
                .withStyle(ChatFormatting.GOLD), true);
        TreeFights.queue(player, pick);
    }
}
