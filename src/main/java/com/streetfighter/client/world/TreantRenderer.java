package com.streetfighter.client.world;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.streetfighter.entity.TreantEntity;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Quaternionf;

import static com.streetfighter.client.world.AnimClips.*;

/**
 * Draws the oak treant out of real block models: root legs, an oak-log trunk with a face, branch arms ending in
 * leaf "fists" and a leafy crown for a head. Limbs read the same animation channels as humanoid models
 * (RAX/LAX arms, RLX/LLX legs, BX/BY trunk, HX/HY crown), so every move clip works on the tree too.
 */
public class TreantRenderer extends EntityRenderer<TreantEntity> {
    private static final BlockState LOG = Blocks.OAK_LOG.defaultBlockState();
    private static final BlockState LOG_X = Blocks.OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z);
    private static final BlockState BARK = Blocks.OAK_WOOD.defaultBlockState();
    private static final BlockState LEAVES = Blocks.OAK_LEAVES.defaultBlockState();
    private static final BlockState EYE = Blocks.BLACK_CONCRETE.defaultBlockState();
    private static final BlockState GLOW = Blocks.OCHRE_FROGLIGHT.defaultBlockState();
    private static final BlockState MOUTH = Blocks.DARK_OAK_LOG.defaultBlockState();
    private static final BlockState ROOT = Blocks.MANGROVE_ROOTS.defaultBlockState();
    private static final float[] REST = humanoidGuard();

    public TreantRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        shadowRadius = 0.7f;
    }

    @Override
    public ResourceLocation getTextureLocation(TreantEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }

    @Override
    public void render(TreantEntity e, float yaw, float partial, PoseStack ps, MultiBufferSource buf, int light) {
        if (FinisherCinematic.current != null && FinisherCinematic.current.hides(e)) return;
        boolean animated = !FightAnimator.suspended;
        if (animated) FightAnimator.pre(e, null, ps);
        FightAnimator.State st = animated ? FightAnimator.stateIfPresent(e) : null;
        float[] c = st != null ? st.cur : REST;
        float time = (e.tickCount + partial) / 20f;

        ps.pushPose();
        float bodyYaw = Mth.rotLerp(partial, e.yBodyRotO, e.yBodyRot);
        ps.mulPose(Axis.YP.rotationDegrees(-bodyYaw));
        if (e.deathTime > 0) { // TIMBER: falls over backwards
            float f = Math.min(1f, (e.deathTime + partial) / 18f);
            ps.mulPose(Axis.XP.rotationDegrees(-90f * f * f));
        }
        int overlay = LivingEntityRenderer.getOverlayCoords(e, 0);
        BlockRenderDispatcher br = Minecraft.getInstance().getBlockRenderer();
        float sway = st == null ? Mth.sin(time * 1.3f) * 0.04f : 0;

        // ---- root legs (pivot at the hip)
        leg(ps, br, buf, light, overlay, 0.22f, c[RLX], c[RLZ]);
        leg(ps, br, buf, light, overlay, -0.22f, c[LLX], c[LLZ]);

        // ---- trunk, face, crown and branches all hang off the hip
        ps.pushPose();
        ps.translate(0, 0.72, 0);
        ps.mulPose(new Quaternionf().rotationZYX(sway, c[BY] * 0.6f, -c[BX]));
        cube(ps, br, buf, light, overlay, BARK, -0.36f, 0f, -0.3f, 0.72f, 1.2f, 0.6f);
        // face: glowing eyes and a knot of a mouth on the front (+Z)
        cube(ps, br, buf, light, overlay, EYE, -0.24f, 0.86f, 0.29f, 0.16f, 0.12f, 0.04f);
        cube(ps, br, buf, light, overlay, EYE, 0.08f, 0.86f, 0.29f, 0.16f, 0.12f, 0.04f);
        cube(ps, br, buf, 0xF000F0, overlay, GLOW, -0.2f, 0.88f, 0.31f, 0.06f, 0.06f, 0.02f);
        cube(ps, br, buf, 0xF000F0, overlay, GLOW, 0.12f, 0.88f, 0.31f, 0.06f, 0.06f, 0.02f);
        cube(ps, br, buf, light, overlay, MOUTH, -0.16f, 0.6f, 0.29f, 0.32f, 0.07f, 0.04f);

        // crown (head)
        ps.pushPose();
        ps.translate(0, 1.18, 0);
        ps.mulPose(new Quaternionf().rotationZYX(sway * 1.5f, c[HY], -c[HX] * 0.7f));
        cube(ps, br, buf, light, overlay, LEAVES, -0.8f, 0f, -0.7f, 1.6f, 0.8f, 1.4f);
        cube(ps, br, buf, light, overlay, LEAVES, -0.55f, 0.78f, -0.5f, 1.1f, 0.45f, 1.0f);
        cube(ps, br, buf, light, overlay, LEAVES, -1.0f, 0.15f, -0.4f, 0.25f, 0.5f, 0.8f);
        cube(ps, br, buf, light, overlay, LEAVES, 0.75f, 0.15f, -0.4f, 0.25f, 0.5f, 0.8f);
        cube(ps, br, buf, light, overlay, LOG, -0.1f, 0.6f, -0.1f, 0.2f, 0.5f, 0.2f);
        ps.popPose();

        // branch arms (the tree's right is -X when facing +Z)
        arm(ps, br, buf, light, overlay, -0.46f, c[RAX], c[RAY], c[RAZ], false);
        arm(ps, br, buf, light, overlay, 0.46f, c[LAX], c[LAY], c[LAZ], true);
        ps.popPose();

        ps.popPose();
        if (animated) FightAnimator.post(e, null, ps);
        super.render(e, yaw, partial, ps, buf, light);
    }

    private static void leg(PoseStack ps, BlockRenderDispatcher br, MultiBufferSource buf, int light, int overlay,
                            float x, float rx, float rz) {
        ps.pushPose();
        ps.translate(x, 0.72, 0);
        ps.mulPose(new Quaternionf().rotationZYX(0, 0, rx));
        cube(ps, br, buf, light, overlay, LOG, -0.14f, -0.72f, -0.14f, 0.28f, 0.72f, 0.28f);
        cube(ps, br, buf, light, overlay, ROOT, -0.2f, -0.72f, -0.18f, 0.4f, 0.2f, 0.55f);
        ps.popPose();
    }

    private static void arm(PoseStack ps, BlockRenderDispatcher br, MultiBufferSource buf, int light, int overlay,
                            float x, float ax, float ay, float az, boolean left) {
        ps.pushPose();
        ps.translate(x, 1.02, 0);
        // humanoid convention: negative X = raise forward, +Z (right arm) / -Z (left arm) = swing outward
        ps.mulPose(new Quaternionf().rotationZYX(-az, left ? -ay : ay, ax));
        cube(ps, br, buf, light, overlay, LOG, -0.12f, -0.82f, -0.12f, 0.24f, 0.82f, 0.24f);
        cube(ps, br, buf, light, overlay, LOG_X, -0.08f, -0.55f, 0.05f, 0.16f, 0.16f, 0.3f); // twig
        cube(ps, br, buf, light, overlay, LEAVES, -0.24f, -1.12f, -0.24f, 0.48f, 0.38f, 0.48f);
        ps.popPose();
    }

    static void cube(PoseStack ps, BlockRenderDispatcher br, MultiBufferSource buf, int light, int overlay,
                             BlockState state, float x, float y, float z, float sx, float sy, float sz) {
        ps.pushPose();
        ps.translate(x, y, z);
        ps.scale(sx, sy, sz);
        br.renderSingleBlock(state, ps, buf, light, overlay);
        ps.popPose();
    }
}
