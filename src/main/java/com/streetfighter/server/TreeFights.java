package com.streetfighter.server;

import com.streetfighter.SFConfig;
import com.streetfighter.entity.ModEntities;
import com.streetfighter.entity.TreantEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Oak trees can be challenged: the whole tree (its logs and natural leaves) pulls itself out of the ground and
 * becomes a {@link TreantEntity}. Beat it and it's felled for a pile of logs; lose or back out and it re-plants.
 */
public final class TreeFights {
    private static final int MAX_LOGS = 96, MAX_LEAVES = 400;

    private record Pending(UUID player, UUID treant, ServerLevel level, int ticks) {
    }

    /** Opens the fight a few ticks from now, once the client has received the freshly spawned opponent. */
    public static void queue(ServerPlayer player, net.minecraft.world.entity.Mob opponent) {
        PENDING.add(new Pending(player.getUUID(), opponent.getUUID(), player.serverLevel(), 4));
    }

    private static final List<Pending> PENDING = new ArrayList<>();

    private TreeFights() {
    }

    public static boolean isOakLog(BlockState s) {
        return s.is(Blocks.OAK_LOG) || s.is(Blocks.OAK_WOOD);
    }

    public static void challenge(ServerPlayer player, BlockPos clicked) {
        if (player.isSpectator() || FightManager.isFighting(player)) return;
        ServerLevel level = player.serverLevel();
        double range = SFConfig.CHALLENGE_RANGE.get() + 3.0;
        if (clicked.distToCenterSqr(player.position()) > range * range || !level.isLoaded(clicked)) return;
        if (!isOakLog(level.getBlockState(clicked))) return;

        // 1. the trunk and branches: connected oak logs (including diagonals)
        Set<BlockPos> logs = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(clicked.immutable());
        logs.add(clicked.immutable());
        while (!queue.isEmpty() && logs.size() < MAX_LOGS) {
            BlockPos p = queue.poll();
            for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
                BlockPos n = p.offset(dx, dy, dz);
                if (!logs.contains(n) && isOakLog(level.getBlockState(n))) {
                    logs.add(n.immutable());
                    queue.add(n.immutable());
                }
            }
        }
        // 2. its crown: natural (non-player-placed) oak leaves near the logs
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        BlockPos base = clicked;
        for (BlockPos p : logs) {
            minX = Math.min(minX, p.getX()); maxX = Math.max(maxX, p.getX());
            minY = Math.min(minY, p.getY()); maxY = Math.max(maxY, p.getY());
            minZ = Math.min(minZ, p.getZ()); maxZ = Math.max(maxZ, p.getZ());
            if (p.getY() < base.getY()) base = p;
        }
        List<BlockPos> leaves = new ArrayList<>();
        for (BlockPos p : BlockPos.betweenClosed(minX - 3, minY, minZ - 3, maxX + 3, maxY + 3, maxZ + 3)) {
            BlockState st = level.getBlockState(p);
            if (st.is(Blocks.OAK_LEAVES) && !st.getValue(LeavesBlock.PERSISTENT)) {
                leaves.add(p.immutable());
                if (leaves.size() >= MAX_LEAVES) break;
            }
        }
        if (leaves.size() < 4 || logs.size() >= MAX_LOGS) {
            player.displayClientMessage(Component.literal("That's no living oak tree.").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        // respect protection / claim mods
        var event = new net.minecraftforge.event.level.BlockEvent.BreakEvent(level, base, level.getBlockState(base), player);
        if (net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(event)) {
            player.displayClientMessage(Component.literal("This tree is protected.").withStyle(ChatFormatting.RED), true);
            return;
        }

        // 3. uproot: remember every block, then lift the tree out of the world
        List<TreantEntity.TreeBlock> blocks = new ArrayList<>();
        for (BlockPos p : leaves) blocks.add(new TreantEntity.TreeBlock(p.subtract(base), level.getBlockState(p)));
        for (BlockPos p : logs) blocks.add(new TreantEntity.TreeBlock(p.subtract(base), level.getBlockState(p)));
        TreantEntity treant = ModEntities.OAK_TREANT.get().create(level);
        if (treant == null) return;
        for (BlockPos p : leaves) level.setBlock(p, Blocks.AIR.defaultBlockState(), 2 | 16);
        for (BlockPos p : logs) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(p)),
                    p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.05);
            level.setBlock(p, Blocks.AIR.defaultBlockState(), 2 | 16);
        }
        Vec3 to = player.position().subtract(Vec3.atBottomCenterOf(base));
        float yaw = (float) (Math.toDegrees(Math.atan2(-to.x, to.z)));
        treant.moveTo(base.getX() + 0.5, base.getY(), base.getZ() + 0.5, yaw, 0);
        treant.setYHeadRot(yaw);
        treant.setYBodyRot(yaw);
        treant.setTree(base, blocks);
        level.addFreshEntity(treant);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.OAK_LEAVES.defaultBlockState()),
                base.getX() + 0.5, base.getY() + 3, base.getZ() + 0.5, 80, 1.5, 1.5, 1.5, 0.1);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.ROOTED_DIRT.defaultBlockState()),
                base.getX() + 0.5, base.getY() + 0.2, base.getZ() + 0.5, 40, 0.6, 0.1, 0.6, 0.1);
        level.playSound(null, base, SoundEvents.ROOTED_DIRT_BREAK, SoundSource.BLOCKS, 1.5f, 0.6f);
        level.playSound(null, base, SoundEvents.WOOD_BREAK, SoundSource.BLOCKS, 1.2f, 0.5f);
        level.playSound(null, base, SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 0.5f, 1.8f);
        player.displayClientMessage(Component.literal("The oak tree uproots itself!").withStyle(ChatFormatting.GOLD), true);
        // the client needs the new entity before the fight can open on it
        PENDING.add(new Pending(player.getUUID(), treant.getUUID(), level, 4));
    }

    public static void tick() {
        if (PENDING.isEmpty()) return;
        List<Pending> next = new ArrayList<>();
        Iterator<Pending> it = PENDING.iterator();
        while (it.hasNext()) {
            Pending p = it.next();
            it.remove();
            if (p.ticks > 0) {
                next.add(new Pending(p.player, p.treant, p.level, p.ticks - 1));
                continue;
            }
            ServerPlayer player = p.level.getServer().getPlayerList().getPlayer(p.player);
            var e = p.level.getEntity(p.treant);
            if (player != null && e instanceof net.minecraft.world.entity.Mob t && t.isAlive()) FightManager.startFight(player, t);
        }
        PENDING.addAll(next);
    }
}
