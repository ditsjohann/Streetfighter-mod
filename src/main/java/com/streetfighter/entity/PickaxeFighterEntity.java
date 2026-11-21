package com.streetfighter.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/** The stone pickaxe you tried to craft, alive and unwilling. Wanders off in a puff of dust unless beaten. */
public class PickaxeFighterEntity extends BlockBodyEntity {
    public PickaxeFighterEntity(EntityType<? extends PickaxeFighterEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void restoreEffects(ServerLevel sl) {
        sl.sendParticles(ParticleTypes.POOF, getX(), getY() + 0.8, getZ(), 20, 0.3, 0.5, 0.3, 0.05);
        sl.playSound(null, blockPosition(), SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 1f, 0.8f);
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.STONE_HIT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ITEM_BREAK;
    }
}
