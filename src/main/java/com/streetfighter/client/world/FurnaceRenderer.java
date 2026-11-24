package com.streetfighter.client.world;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.streetfighter.entity.FurnaceGolemEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Quaternionf;

import static com.streetfighter.client.world.AnimClips.*;

/**
 * A walking furnace: the furnace block itself is the body, lit and facing you (its fiery mouth is the face), on
 * stubby cobblestone legs with stone-brick arms and iron-block fists.
 */
public class FurnaceRenderer extends EntityRenderer<FurnaceGolemEntity> {
    private static final BlockState LIMB = Blocks.COBBLESTONE.defaultBlockState();
    private static final BlockState ARM = Blocks.STONE_BRICKS.defaultBlockState();
    private static final BlockState FIST = Blocks.IRON_BLOCK.defaultBlockState();
    private static final BlockState EMBER = Blocks.MAGMA_BLOCK.defaultBlockState();
    private static final float[] REST = humanoidGuard();

    public FurnaceRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        shadowRadius = 0.7f;
    }

    @Override
    public ResourceLocation getTextureLocation(FurnaceGolemEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }

    @Override
    public void render(FurnaceGolemEntity e, float yaw, float partial, PoseStack ps, MultiBufferSource buf, int light) {
        if (FinisherCinematic.current != null && FinisherCinematic.current.hides(e)) return;
        boolean animated = !FightAnimator.suspended;
        if (animated) FightAnimator.pre(e, null, ps);
        FightAnimator.State st = animated ? FightAnimator.stateIfPresent(e) : null;
        float[] c = st != null ? st.cur : REST;
        BlockState body = (e.kind() == 1 ? Blocks.SMOKER : e.kind() == 2 ? Blocks.BLAST_FURNACE : Blocks.FURNACE)
                .defaultBlockState().setValue(AbstractFurnaceBlock.FACING, Direction.SOUTH)
                .setValue(AbstractFurnaceBlock.LIT, true);

        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(-Mth.rotLerp(partial, e.yBodyRotO, e.yBodyRot)));
        if (e.deathTime > 0) {
            float f = Math.min(1f, (e.deathTime + partial) / 16f);
            ps.mulPose(Axis.XP.rotationDegrees(80f * f * f));
        }
        int overlay = LivingEntityRenderer.getOverlayCoords(e, 0);
        BlockRenderDispatcher br = Minecraft.getInstance().getBlockRenderer();

        for (int side = -1; side <= 1; side += 2) {
            float rx = side < 0 ? c[RLX] : c[LLX];
            ps.pushPose();
            ps.translate(side * -0.25f, 0.55, 0);
            ps.mulPose(new Quaternionf().rotationX(rx));
            TreantRenderer.cube(ps, br, buf, light, overlay, LIMB, -0.16f, -0.55f, -0.16f, 0.32f, 0.55f, 0.32f);
            ps.popPose();
        }
        ps.pushPose();
        ps.translate(0, 0.55, 0);
        ps.mulPose(new Quaternionf().rotationZYX(0, c[BY] * 0.6f, -c[BX] - c[HX] * 0.2f));
        // the furnace body, a bit bigger than a block
        TreantRenderer.cube(ps, br, buf, light, overlay, body, -0.55f, 0f, -0.55f, 1.1f, 1.1f, 1.1f);
        // a glowing ember crown when it's fired up (heavy attacks lean forward)
        TreantRenderer.cube(ps, br, buf, 0xF000F0, overlay, EMBER, -0.3f, 1.1f, -0.3f, 0.6f, 0.08f, 0.6f);
        armOf(ps, br, buf, light, overlay, -0.72f, c[RAX], c[RAY], c[RAZ], false);
        armOf(ps, br, buf, light, overlay, 0.72f, c[LAX], c[LAY], c[LAZ], true);
        ps.popPose();

        ps.popPose();
        if (animated) FightAnimator.post(e, null, ps);
        super.render(e, yaw, partial, ps, buf, light);
    }

    private static void armOf(PoseStack ps, BlockRenderDispatcher br, MultiBufferSource buf, int light, int overlay,
                              float x, float ax, float ay, float az, boolean left) {
        ps.pushPose();
        ps.translate(x, 0.86, 0);
        ps.mulPose(new Quaternionf().rotationZYX(-az, left ? -ay : ay, ax));
        TreantRenderer.cube(ps, br, buf, light, overlay, ARM, -0.13f, -0.55f, -0.13f, 0.26f, 0.55f, 0.26f);
        TreantRenderer.cube(ps, br, buf, light, overlay, FIST, -0.18f, -0.86f, -0.18f, 0.36f, 0.34f, 0.36f);
        ps.popPose();
    }
}
