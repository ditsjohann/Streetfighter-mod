package com.streetfighter.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * A furnace (or smoker / blast furnace) that hopped off its spot to fight. It carries its own contents: if it
 * isn't beaten it climbs back into place with everything still smelting; beaten, it spills them as loot.
 */
public class FurnaceGolemEntity extends BlockBodyEntity {
    private static final net.minecraft.network.syncher.EntityDataAccessor<Integer> KIND =
            net.minecraft.network.syncher.SynchedEntityData.defineId(FurnaceGolemEntity.class,
                    net.minecraft.network.syncher.EntityDataSerializers.INT);
    private CompoundTag furnaceData = new CompoundTag();

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(KIND, 0);
    }

    /** 0 furnace, 1 smoker, 2 blast furnace (synced so the client draws the right block). */
    public int kind() {
        return entityData.get(KIND);
    }

    @Override
    public void setBlocks(BlockPos origin, List<TreeBlock> list) {
        super.setBlocks(origin, list);
        BlockState st = furnaceState();
        entityData.set(KIND, st.is(Blocks.SMOKER) ? 1 : st.is(Blocks.BLAST_FURNACE) ? 2 : 0);
    }

    public FurnaceGolemEntity(EntityType<? extends FurnaceGolemEntity> type, Level level) {
        super(type, level);
    }

    public void setFurnaceData(CompoundTag tag) {
        furnaceData = tag == null ? new CompoundTag() : tag;
    }

    /** The furnace block it was (drawn as its body). */
    public BlockState furnaceState() {
        return blocks.isEmpty() ? Blocks.FURNACE.defaultBlockState() : blocks.get(0).state();
    }

    /** What was inside (input, fuel, output). */
    public List<ItemStack> contents() {
        NonNullList<ItemStack> items = NonNullList.withSize(3, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(furnaceData, items, level().registryAccess());
        List<ItemStack> out = new ArrayList<>();
        for (ItemStack s : items) if (!s.isEmpty()) out.add(s);
        return out;
    }

    @Override
    protected void afterRestore(ServerLevel sl) {
        BlockEntity be = sl.getBlockEntity(origin);
        if (be != null && !furnaceData.isEmpty()) {
            be.loadWithComponents(furnaceData, sl.registryAccess());
            be.setChanged();
        }
    }

    @Override
    protected void restoreEffects(ServerLevel sl) {
        sl.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1, getZ(), 20, 0.4, 0.5, 0.4, 0.02);
        sl.playSound(null, origin, SoundEvents.STONE_PLACE, SoundSource.BLOCKS, 1.4f, 0.8f);
        sl.playSound(null, origin, SoundEvents.FURNACE_FIRE_CRACKLE, SoundSource.BLOCKS, 1f, 1f);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.put("FurnaceData", furnaceData);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        furnaceData = tag.getCompound("FurnaceData");
        BlockState st = furnaceState();
        entityData.set(KIND, st.is(Blocks.SMOKER) ? 1 : st.is(Blocks.BLAST_FURNACE) ? 2 : 0);
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.STONE_HIT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.STONE_BREAK;
    }

    public BlockPos home() {
        return origin;
    }
}
