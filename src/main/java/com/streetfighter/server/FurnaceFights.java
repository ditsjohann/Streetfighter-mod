package com.streetfighter.server;

import com.streetfighter.SFConfig;
import com.streetfighter.entity.BlockBodyEntity;
import com.streetfighter.entity.FurnaceGolemEntity;
import com.streetfighter.entity.ModEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Furnaces (and smokers / blast furnaces) can be challenged: the block hops off its spot and fights you, carrying
 * whatever it was smelting. Lose or back out and it climbs back in, still smelting; win and its contents spill out
 * with the loot.
 */
public final class FurnaceFights {
    private FurnaceFights() {
    }

    public static boolean isFurnace(BlockState s) {
        return s.getBlock() instanceof AbstractFurnaceBlock;
    }

    public static void challenge(ServerPlayer player, BlockPos pos) {
        if (player.isSpectator() || FightManager.isFighting(player)) return;
        ServerLevel level = player.serverLevel();
        double range = SFConfig.CHALLENGE_RANGE.get() + 3.0;
        BlockState state = level.getBlockState(pos);
        if (pos.distToCenterSqr(player.position()) > range * range || !isFurnace(state)) return;
        var event = new net.minecraftforge.event.level.BlockEvent.BreakEvent(level, pos, state, player);
        if (net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(event)) {
            player.displayClientMessage(Component.literal("This furnace is protected.").withStyle(ChatFormatting.RED), true);
            return;
        }
        FurnaceGolemEntity golem = ModEntities.FURNACE_GOLEM.get().create(level);
        if (golem == null) return;
        // take its contents along (and empty the block first so breaking it doesn't spill them)
        BlockEntity be = level.getBlockEntity(pos);
        CompoundTag data = be != null ? be.saveWithoutMetadata(level.registryAccess()) : new CompoundTag();
        if (be instanceof Container c) c.clearContent();
        golem.setFurnaceData(data);
        golem.setBlocks(pos, List.of(new BlockBodyEntity.TreeBlock(BlockPos.ZERO, state)));
        level.sendParticles(ParticleTypes.LARGE_SMOKE, pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, 20, 0.3, 0.3, 0.3, 0.02);
        level.sendParticles(ParticleTypes.FLAME, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 20, 0.3, 0.3, 0.3, 0.05);
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2 | 16);
        Vec3 spot = OreFights.standingSpot(level, player, Vec3.atCenterOf(pos));
        OreFights.place(golem, player, spot);
        level.addFreshEntity(golem);
        level.playSound(null, pos, SoundEvents.FURNACE_FIRE_CRACKLE, SoundSource.BLOCKS, 1.5f, 0.6f);
        level.playSound(null, pos, SoundEvents.BLAZE_SHOOT, SoundSource.BLOCKS, 0.8f, 0.6f);
        level.playSound(null, pos, SoundEvents.STONE_BREAK, SoundSource.BLOCKS, 1.2f, 0.6f);
        player.displayClientMessage(Component.literal("The furnace hops off its spot, fired up for a fight!")
                .withStyle(ChatFormatting.GOLD), true);
        TreeFights.queue(player, golem);
    }
}
