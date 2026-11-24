package com.streetfighter.entity;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

/** An ore vein (iron or coal) that pulled itself out of the rock as a stone golem; sinks back in unless mined (beaten). */
public class OreGolemEntity extends BlockBodyEntity {
    public OreGolemEntity(EntityType<? extends OreGolemEntity> type, Level level) {
        super(type, level);
    }

    /** True if the vein came out of deepslate (drawn darker). */
    public boolean deepslate() {
        for (TreeBlock b : blocks) {
            if (b.state().is(Blocks.DEEPSLATE_IRON_ORE) || b.state().is(Blocks.DEEPSLATE_COAL_ORE)) return true;
        }
        return false;
    }

    /** True for a coal golem. */
    public boolean coal() {
        return getType() == ModEntities.COAL_ORE_GOLEM.get();
    }

    @Override
    protected void restoreEffects(ServerLevel sl) {
        sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK,
                        (coal() ? Blocks.COAL_ORE : Blocks.IRON_ORE).defaultBlockState()),
                getX(), getY() + 1, getZ(), 50, 0.6, 0.8, 0.6, 0.1);
        sl.playSound(null, origin, SoundEvents.STONE_PLACE, SoundSource.BLOCKS, 1.4f, 0.6f);
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.STONE_HIT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.STONE_BREAK;
    }
}
