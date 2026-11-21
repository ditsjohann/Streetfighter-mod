package com.streetfighter.server;

import com.streetfighter.SFConfig;
import com.streetfighter.entity.BlockBodyEntity;
import com.streetfighter.entity.ModEntities;
import com.streetfighter.entity.OreGolemEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Iron ore can be challenged: the whole vein tears out of the rock as an ore golem. */
public final class OreFights {
    private static final int MAX_VEIN = 24;

    private OreFights() {
    }

    public static boolean isIronOre(BlockState s) {
        return s.is(Blocks.IRON_ORE) || s.is(Blocks.DEEPSLATE_IRON_ORE);
    }

    public static void challenge(ServerPlayer player, BlockPos clicked) {
        if (player.isSpectator() || FightManager.isFighting(player)) return;
        ServerLevel level = player.serverLevel();
        double range = SFConfig.CHALLENGE_RANGE.get() + 3.0;
        if (clicked.distToCenterSqr(player.position()) > range * range || !isIronOre(level.getBlockState(clicked))) return;

        // the whole vein (connected iron ore, diagonals included)
        Set<BlockPos> vein = new LinkedHashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        vein.add(clicked.immutable());
        queue.add(clicked.immutable());
        while (!queue.isEmpty() && vein.size() < MAX_VEIN) {
            BlockPos p = queue.poll();
            for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
                BlockPos n = p.offset(dx, dy, dz);
                if (!vein.contains(n) && isIronOre(level.getBlockState(n))) {
                    vein.add(n.immutable());
                    queue.add(n.immutable());
                }
            }
        }
        var event = new net.minecraftforge.event.level.BlockEvent.BreakEvent(level, clicked, level.getBlockState(clicked), player);
        if (net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(event)) {
            player.displayClientMessage(Component.literal("This ore is protected.").withStyle(ChatFormatting.RED), true);
            return;
        }
        OreGolemEntity golem = ModEntities.IRON_ORE_GOLEM.get().create(level);
        if (golem == null) return;
        List<BlockBodyEntity.TreeBlock> blocks = new ArrayList<>();
        for (BlockPos p : vein) {
            BlockState st = level.getBlockState(p);
            blocks.add(new BlockBodyEntity.TreeBlock(p.subtract(clicked), st));
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, st), p.getX() + 0.5, p.getY() + 0.5,
                    p.getZ() + 0.5, 12, 0.3, 0.3, 0.3, 0.1);
            level.setBlock(p, Blocks.AIR.defaultBlockState(), 2 | 16);
        }
        golem.setBlocks(clicked, blocks);
        Vec3 spot = standingSpot(level, player, Vec3.atCenterOf(clicked));
        place(golem, player, spot);
        level.addFreshEntity(golem);
        level.playSound(null, clicked, SoundEvents.STONE_BREAK, SoundSource.BLOCKS, 1.5f, 0.5f);
        level.playSound(null, clicked, SoundEvents.IRON_GOLEM_REPAIR, SoundSource.BLOCKS, 1f, 0.6f);
        level.playSound(null, clicked, SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 0.4f, 0.6f);
        player.displayClientMessage(Component.literal("The iron vein tears itself out of the rock!").withStyle(ChatFormatting.GOLD), true);
        TreeFights.queue(player, golem);
    }

    /** A spot on the player's floor between the player and {@code target}, with room to stand. */
    static Vec3 standingSpot(ServerLevel level, ServerPlayer player, Vec3 target) {
        Vec3 flat = new Vec3(target.x - player.getX(), 0, target.z - player.getZ());
        Vec3 dir = flat.lengthSqr() < 0.01 ? Vec3.directionFromRotation(0, player.getYRot()) : flat.normalize();
        double maxD = Math.max(1.5, Math.min(3.0, flat.length()));
        for (double d = maxD; d >= 1.2; d -= 0.4) {
            Vec3 p = player.position().add(dir.scale(d));
            BlockPos feet = BlockPos.containing(p);
            if (level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                    && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()) {
                return new Vec3(p.x, player.getY(), p.z);
            }
        }
        return player.position().add(dir.scale(1.5));
    }

    static void place(Mob mob, ServerPlayer player, Vec3 at) {
        Vec3 to = player.position().subtract(at);
        float yaw = (float) Math.toDegrees(Math.atan2(-to.x, to.z));
        mob.moveTo(at.x, at.y, at.z, yaw, 0);
        mob.setYHeadRot(yaw);
        mob.setYBodyRot(yaw);
    }
}
