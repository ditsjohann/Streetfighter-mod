package com.streetfighter.server;

import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.Tags;

/** Which blocks a fighter can be smashed through (shared by client arena scan and server validation). */
public final class BlockRules {
    private BlockRules() {
    }

    public static boolean isBreakable(BlockState st) {
        return st.is(BlockTags.LEAVES) || st.is(Tags.Blocks.GLASS_BLOCKS) || st.is(Tags.Blocks.GLASS_PANES)
                || st.is(BlockTags.FLOWERS) || st.is(Blocks.HAY_BLOCK);
    }
}
