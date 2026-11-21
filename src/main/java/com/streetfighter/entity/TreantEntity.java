package com.streetfighter.entity;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

import java.util.List;

/** A real oak tree that pulled itself out of the ground to fight; re-plants itself unless felled. */
public class TreantEntity extends BlockBodyEntity {
    /** Kept for compatibility with older code paths. */
    public record TreeBlock(net.minecraft.core.BlockPos offset, net.minecraft.world.level.block.state.BlockState state) {
    }

    public TreantEntity(EntityType<? extends TreantEntity> type, Level level) {
        super(type, level);
    }

    public void setTree(net.minecraft.core.BlockPos origin, List<TreeBlock> list) {
        setBlocks(origin, list.stream().map(b -> new BlockBodyEntity.TreeBlock(b.offset(), b.state())).toList());
    }

    public int logCount() {
        int n = 0;
        for (BlockBodyEntity.TreeBlock b : blocks) if (!b.state().is(BlockTags.LEAVES)) n++;
        return n;
    }

    public void markFelled() {
        markDefeated();
    }

    @Override
    protected void restoreEffects(ServerLevel sl) {
        sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.OAK_LEAVES.defaultBlockState()),
                getX(), getY() + 2, getZ(), 60, 1.2, 1.2, 1.2, 0.1);
        sl.playSound(null, origin, SoundEvents.ROOTED_DIRT_PLACE, SoundSource.BLOCKS, 1.2f, 0.7f);
        sl.playSound(null, origin, SoundEvents.AZALEA_LEAVES_PLACE, SoundSource.BLOCKS, 1f, 0.8f);
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.WOOD_HIT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.WOOD_BREAK;
    }
}
