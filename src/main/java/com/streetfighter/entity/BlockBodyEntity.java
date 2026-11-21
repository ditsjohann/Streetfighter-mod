package com.streetfighter.entity;

import com.streetfighter.server.FightManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * A fighter made out of blocks that were in the world (an oak tree, an iron ore vein) or out of thin air (a
 * crafted pickaxe). It remembers the blocks it came from, saved with the entity, and puts them back if it
 * isn't beaten. Beaten, it's {@link #markDefeated() defeated} for good.
 */
public abstract class BlockBodyEntity extends PathfinderMob {
    public record TreeBlock(BlockPos offset, BlockState state) {
    }

    protected final List<TreeBlock> blocks = new ArrayList<>();
    protected BlockPos origin = BlockPos.ZERO;
    private int idleTicks;
    private boolean defeated;

    protected BlockBodyEntity(EntityType<? extends BlockBodyEntity> type, Level level) {
        super(type, level);
        setNoAi(true);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, 60).add(Attributes.MOVEMENT_SPEED, 0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    public void setBlocks(BlockPos origin, List<TreeBlock> list) {
        this.origin = origin.immutable();
        blocks.clear();
        blocks.addAll(list);
    }

    public int blockCount() {
        return blocks.size();
    }

    /** Called just before the winning blow: it won't go back to being blocks. */
    public void markDefeated() {
        defeated = true;
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return !defeated || super.isInvulnerableTo(source);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide || defeated || !isAlive()) return;
        if (FightManager.isFighting(this)) {
            idleTicks = 0;
        } else if (++idleTicks > 60) {
            restore();
        }
    }

    /** Puts the blocks back (only into empty/replaceable spots) and vanishes. */
    public void restore() {
        if (!(level() instanceof ServerLevel sl)) return;
        for (TreeBlock b : blocks) {
            BlockPos p = origin.offset(b.offset);
            BlockState cur = sl.getBlockState(p);
            if (cur.isAir() || cur.canBeReplaced()) sl.setBlock(p, b.state, 2 | 16);
        }
        restoreEffects(sl);
        blocks.clear();
        discard();
    }

    protected abstract void restoreEffects(ServerLevel level);

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
        for (TreeBlock b : blocks) {
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
        blocks.clear();
        ListTag list = tag.getList("Tree", Tag.TAG_COMPOUND);
        var lookup = level().holderLookup(Registries.BLOCK);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            blocks.add(new TreeBlock(new BlockPos(t.getInt("x"), t.getInt("y"), t.getInt("z")),
                    NbtUtils.readBlockState(lookup, t.getCompound("state"))));
        }
    }
}
