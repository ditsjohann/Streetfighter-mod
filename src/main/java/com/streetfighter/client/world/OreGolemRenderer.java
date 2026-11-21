package com.streetfighter.client.world;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.streetfighter.entity.OreGolemEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Quaternionf;

import static com.streetfighter.client.world.AnimClips.*;

/** The iron ore golem: an ore-block torso, stone head with glowing eyes, raw-iron fists, cobblestone legs. */
public class OreGolemRenderer extends EntityRenderer<OreGolemEntity> {
    private static final BlockState EYE = Blocks.OCHRE_FROGLIGHT.defaultBlockState();
    private static final BlockState FIST = Blocks.RAW_IRON_BLOCK.defaultBlockState();
    private static final float[] REST = humanoidGuard();

    public OreGolemRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        shadowRadius = 0.7f;
    }

    @Override
    public ResourceLocation getTextureLocation(OreGolemEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }

    @Override
    public void render(OreGolemEntity e, float yaw, float partial, PoseStack ps, MultiBufferSource buf, int light) {
        boolean animated = !FightAnimator.suspended;
        if (animated) FightAnimator.pre(e, null, ps);
        FightAnimator.State st = animated ? FightAnimator.stateIfPresent(e) : null;
        float[] c = st != null ? st.cur : REST;
        boolean deep = e.deepslate();
        BlockState ore = (deep ? Blocks.DEEPSLATE_IRON_ORE : Blocks.IRON_ORE).defaultBlockState();
        BlockState rock = (deep ? Blocks.DEEPSLATE : Blocks.STONE).defaultBlockState();
        BlockState rubble = (deep ? Blocks.COBBLED_DEEPSLATE : Blocks.COBBLESTONE).defaultBlockState();

        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(-Mth.rotLerp(partial, e.yBodyRotO, e.yBodyRot)));
        if (e.deathTime > 0) { // crumbles forward
            float f = Math.min(1f, (e.deathTime + partial) / 16f);
            ps.mulPose(Axis.XP.rotationDegrees(80f * f * f));
        }
        int overlay = LivingEntityRenderer.getOverlayCoords(e, 0);
        BlockRenderDispatcher br = Minecraft.getInstance().getBlockRenderer();

        for (int side = -1; side <= 1; side += 2) {
            float rx = side < 0 ? c[RLX] : c[LLX];
            ps.pushPose();
            ps.translate(side * -0.26f, 0.7, 0);
            ps.mulPose(new Quaternionf().rotationX(rx));
            TreantRenderer.cube(ps, br, buf, light, overlay, rubble, -0.17f, -0.7f, -0.17f, 0.34f, 0.7f, 0.34f);
            ps.popPose();
        }
        ps.pushPose();
        ps.translate(0, 0.7, 0);
        ps.mulPose(new Quaternionf().rotationZYX(0, c[BY] * 0.6f, -c[BX]));
        TreantRenderer.cube(ps, br, buf, light, overlay, ore, -0.5f, 0f, -0.36f, 1.0f, 0.86f, 0.72f);
        TreantRenderer.cube(ps, br, buf, light, overlay, rock, -0.62f, 0.62f, -0.3f, 1.24f, 0.26f, 0.6f); // shoulders
        // head
        ps.pushPose();
        ps.translate(0, 0.86, 0);
        ps.mulPose(new Quaternionf().rotationZYX(0, c[HY], -c[HX] * 0.7f));
        TreantRenderer.cube(ps, br, buf, light, overlay, rock, -0.3f, 0f, -0.3f, 0.6f, 0.52f, 0.6f);
        TreantRenderer.cube(ps, br, buf, 0xF000F0, overlay, EYE, -0.2f, 0.26f, 0.29f, 0.12f, 0.08f, 0.03f);
        TreantRenderer.cube(ps, br, buf, 0xF000F0, overlay, EYE, 0.08f, 0.26f, 0.29f, 0.12f, 0.08f, 0.03f);
        ps.popPose();
        // arms with raw iron fists
        armOf(ps, br, buf, light, overlay, rock, -0.72f, c[RAX], c[RAY], c[RAZ], false);
        armOf(ps, br, buf, light, overlay, rock, 0.72f, c[LAX], c[LAY], c[LAZ], true);
        ps.popPose();

        ps.popPose();
        if (animated) FightAnimator.post(e, null, ps);
        super.render(e, yaw, partial, ps, buf, light);
    }

    private static void armOf(PoseStack ps, BlockRenderDispatcher br, MultiBufferSource buf, int light, int overlay,
                              BlockState rock, float x, float ax, float ay, float az, boolean left) {
        ps.pushPose();
        ps.translate(x, 0.74, 0);
        ps.mulPose(new Quaternionf().rotationZYX(-az, left ? -ay : ay, ax));
        TreantRenderer.cube(ps, br, buf, light, overlay, rock, -0.15f, -0.62f, -0.15f, 0.3f, 0.62f, 0.3f);
        TreantRenderer.cube(ps, br, buf, light, overlay, FIST, -0.21f, -0.98f, -0.21f, 0.42f, 0.4f, 0.42f);
        ps.popPose();
    }
}
