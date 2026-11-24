package com.streetfighter.client.world;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.streetfighter.entity.PickaxeFighterEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import static com.streetfighter.client.world.AnimClips.*;

/**
 * A giant living stone pickaxe standing on its handle, with googly eyes. Punch and kick clips swing the whole
 * pickaxe around its grip (a mining chop); hops, flips and leans come from the shared body transforms.
 */
public class PickaxeRenderer extends EntityRenderer<PickaxeFighterEntity> {
    private static final ItemStack PICK = new ItemStack(Items.STONE_PICKAXE);
    private static final ItemStack SWORD = new ItemStack(Items.IRON_SWORD);
    private static final BlockState WHITE = Blocks.WHITE_CONCRETE.defaultBlockState();
    private static final BlockState PUPIL = Blocks.BLACK_CONCRETE.defaultBlockState();
    private static final float[] REST = humanoidGuard();

    public PickaxeRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        shadowRadius = 0.4f;
    }

    @Override
    public ResourceLocation getTextureLocation(PickaxeFighterEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }

    @Override
    public void render(PickaxeFighterEntity e, float yaw, float partial, PoseStack ps, MultiBufferSource buf, int light) {
        if (FinisherCinematic.current != null && FinisherCinematic.current.hides(e)) return;
        boolean animated = !FightAnimator.suspended;
        if (animated) FightAnimator.pre(e, null, ps);
        FightAnimator.State st = animated ? FightAnimator.stateIfPresent(e) : null;
        float[] c = st != null ? st.cur : REST;
        float time = (e.tickCount + partial) / 20f;

        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(-Mth.rotLerp(partial, e.yBodyRotO, e.yBodyRot)));
        if (e.deathTime > 0) {
            float f = Math.min(1f, (e.deathTime + partial) / 14f);
            ps.mulPose(Axis.XP.rotationDegrees(90f * f));
        }
        int overlay = LivingEntityRenderer.getOverlayCoords(e, 0);
        // mining chop: punches swing the head forward around the grip, kicks sweep the handle
        float chop = -(c[RAX] + 1.25f) * 0.9f - (c[RLX] - 0.3f) * 0.6f;
        if (st == null) chop = Mth.sin(time * 2f) * 0.08f;
        ps.translate(0, 0.12, 0);
        ps.mulPose(Axis.XP.rotation(chop));

        // the pickaxe itself: standing upright, head on top, sprite facing the side camera
        ps.pushPose();
        ps.translate(0, 0.86, 0);
        ps.mulPose(Axis.YP.rotationDegrees(90));
        ps.mulPose(Axis.ZP.rotationDegrees(45));
        ps.scale(2.4f, 2.4f, 2.4f);
        boolean sword = e.getType() == com.streetfighter.entity.ModEntities.IRON_SWORD.get();
        Minecraft.getInstance().getItemRenderer().renderStatic(sword ? SWORD : PICK, ItemDisplayContext.NONE, light, overlay,
                ps, buf, e.level(), e.getId());
        ps.popPose();

        // googly eyes just under the head (on the sword: just above the crossguard)
        if (sword) ps.translate(0, -0.55, 0);
        BlockRenderDispatcher br = Minecraft.getInstance().getBlockRenderer();
        for (int i = 0; i < 2; i++) {
            float z = i == 0 ? -0.18f : 0.06f;
            TreantRenderer.cube(ps, br, buf, light, overlay, WHITE, -0.07f, 1.26f, z, 0.14f, 0.15f, 0.14f);
            float look = 0.04f;
            TreantRenderer.cube(ps, br, buf, light, overlay, PUPIL, -0.08f, 1.29f, z + look, 0.16f, 0.07f, 0.07f);
        }
        ps.popPose();
        if (animated) FightAnimator.post(e, null, ps);
        super.render(e, yaw, partial, ps, buf, light);
    }
}
