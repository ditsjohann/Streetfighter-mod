package com.streetfighter.entity;

import com.streetfighter.server.FightManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * A real oak tree that pulled itself out of the ground to fight. It remembers every block of the tree it came
 * from (saved with the entity), so if it isn't felled it plants itself back exactly as it was.
 */
public class TreantEntity extends PathfinderMob {
    public record TreeBlock(BlockPos offset, BlockState state) {
    }

    private final List<TreeBlock> tree = new ArrayList<>();
    private BlockPos origin = BlockPos.ZERO;
    private int idleTicks;
    private boolean felled;

    public TreantEntity(EntityType<? extends TreantEntity> type, Level level) {
        super(type, level);
        setNoAi(true);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, 60).add(Attributes.MOVEMENT_SPEED, 0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    public void setTree(BlockPos origin, List<TreeBlock> blocks) {
        this.origin = origin.immutable();
        tree.clear();
        tree.addAll(blocks);
    }

    public int logCount() {
        int n = 0;
        for (TreeBlock b : tree) if (!b.state.is(net.minecraft.tags.BlockTags.LEAVES)) n++;
        return n;
    }

    /** Called just before the winning blow: the tree is felled for good and won't replant. */
    public void markFelled() {
        felled = true;
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return !felled || super.isInvulnerableTo(source);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide || felled || !isAlive()) return;
        if (FightManager.isFighting(this)) {
            idleTicks = 0;
        } else if (++idleTicks > 60) {
            replant();
        }
    }

    /** Puts the tree back where it stood (only into empty/replaceable spots) and vanishes. */
    public void replant() {
        if (!(level() instanceof ServerLevel sl)) return;
        for (TreeBlock b : tree) {
            BlockPos p = origin.offset(b.offset);
            BlockState cur = sl.getBlockState(p);
            if (cur.isAir() || cur.canBeReplaced()) sl.setBlock(p, b.state, 2 | 16);
        }
        sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.OAK_LEAVES.defaultBlockState()),
                getX(), getY() + 2, getZ(), 60, 1.2, 1.2, 1.2, 0.1);
        sl.playSound(null, origin, SoundEvents.ROOTED_DIRT_PLACE, SoundSource.BLOCKS, 1.2f, 0.7f);
        sl.playSound(null, origin, SoundEvents.AZALEA_LEAVES_PLACE, SoundSource.BLOCKS, 1f, 0.8f);
        tree.clear();
        discard();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.WOOD_HIT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.WOOD_BREAK;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.put("Origin", NbtUtils.writeBlockPos(origin));
        ListTag list = new ListTag();
        for (TreeBlock b : tree) {
            CompoundTag t = new CompoundTag();
            t.putInt("x", b.offset.getX());
            t.putInt("y", b.offset.getY());
            t.putInt("z", b.offset.getZ());
            t.put("state", NbtUtils.writeBlockState(b.state));
            list.add(t);
        }
        tag.put("Tree", list);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        NbtUtils.readBlockPos(tag, "Origin").ifPresent(p -> origin = p);
        tree.clear();
        ListTag list = tag.getList("Tree", Tag.TAG_COMPOUND);
        var blocks = level().holderLookup(Registries.BLOCK);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            tree.add(new TreeBlock(new BlockPos(t.getInt("x"), t.getInt("y"), t.getInt("z")),
                    NbtUtils.readBlockState(blocks, t.getCompound("state"))));
        }
    }
}
