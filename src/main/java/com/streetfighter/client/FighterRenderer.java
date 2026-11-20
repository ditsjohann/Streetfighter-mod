package com.streetfighter.client;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.joml.Quaternionf;

/** Renders a real Minecraft entity model side-on inside the 2D fight screen. */
final class FighterRenderer {
    private FighterRenderer() {
    }

    /** How the entity should be posed for one frame. */
    static final class Pose {
        float tilt;          // degrees, positive = lean toward facing direction
        float squashY = 1f;  // crouch squash
        float offsetX;       // screen px toward facing direction
        float offsetY;       // screen px upward
        float attackAnim = -1f; // arm swing 0..1, -1 = leave alone
        boolean aggressive;  // skeleton bow pose
        float spin;          // extra yaw spin in degrees
        float flip;          // roll around the body's centre (belly-up knockdowns for four-legged mobs)
    }

    private static final java.util.Map<net.minecraft.world.entity.EntityType<?>, Boolean> UPRIGHT = new java.util.HashMap<>();

    /** True for two-legged bodies (players, skeletons, villagers...), false for pigs, spiders, etc. */
    static boolean isUpright(LivingEntity entity) {
        return UPRIGHT.computeIfAbsent(entity.getType(), t -> {
            var renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity);
            if (renderer instanceof net.minecraft.client.renderer.entity.LivingEntityRenderer<?, ?> ler) {
                var model = ler.getModel();
                return model instanceof net.minecraft.client.model.HumanoidModel<?>
                        || model instanceof net.minecraft.client.model.VillagerModel<?>
                        || model instanceof net.minecraft.client.model.IllagerModel<?>;
            }
            return entity.getBbHeight() > entity.getBbWidth() * 1.5f;
        });
    }

    /**
     * @param x          screen x of the feet
     * @param y          screen y of the feet
     * @param pxPerBlock pixels per Minecraft block
     */
    static void render(GuiGraphics g, LivingEntity entity, float x, float y, float pxPerBlock, boolean faceRight, Pose pose) {
        if (entity == null) return;
        int dir = faceRight ? 1 : -1;
        float yaw = faceRight ? 90f + 28f : 270f - 28f;
        yaw += pose.spin;

        // save entity state we temporarily override
        float bodyRot = entity.yBodyRot, bodyRotO = entity.yBodyRotO;
        float headRot = entity.yHeadRot, headRotO = entity.yHeadRotO;
        float yRot = entity.getYRot(), yRotO = entity.yRotO;
        float xRot = entity.getXRot(), xRotO = entity.xRotO;
        float attack = entity.attackAnim, attackO = entity.oAttackAnim;
        Component customName = entity.getCustomName();
        boolean wasAggressive = entity instanceof Mob mob && mob.isAggressive();

        entity.yBodyRot = entity.yBodyRotO = yaw;
        entity.yHeadRot = entity.yHeadRotO = yaw;
        entity.setYRot(yaw);
        entity.yRotO = yaw;
        entity.setXRot(0);
        entity.xRotO = 0;
        if (pose.attackAnim >= 0) {
            entity.attackAnim = pose.attackAnim;
            entity.oAttackAnim = pose.attackAnim;
        }
        if (customName != null) entity.setCustomName(null);
        if (entity instanceof Mob mob && pose.aggressive != wasAggressive) mob.setAggressive(pose.aggressive);

        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate(x + pose.offsetX * dir, y - pose.offsetY, 100);
        if (pose.tilt != 0) ps.mulPose(Axis.ZP.rotationDegrees(pose.tilt * dir));
        if (pose.flip != 0) {
            float half = entity.getBbHeight() * pxPerBlock * pose.squashY / 2f;
            ps.translate(0, -half, 0);
            ps.mulPose(Axis.ZP.rotationDegrees(pose.flip * dir));
            ps.translate(0, half, 0);
        }
        ps.scale(pxPerBlock, pxPerBlock * pose.squashY, -pxPerBlock);
        ps.mulPose(new Quaternionf().rotateZ((float) Math.PI));
        Lighting.setupForEntityInInventory();
        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        dispatcher.setRenderShadow(false);
        try {
            RenderSystem.runAsFancy(() -> dispatcher.render(entity, 0, 0, 0, 0f, 1f, ps, g.bufferSource(), 0xF000F0));
            g.flush();
        } finally {
            dispatcher.setRenderShadow(true);
            ps.popPose();
            Lighting.setupFor3DItems();

            entity.yBodyRot = bodyRot;
            entity.yBodyRotO = bodyRotO;
            entity.yHeadRot = headRot;
            entity.yHeadRotO = headRotO;
            entity.setYRot(yRot);
            entity.yRotO = yRotO;
            entity.setXRot(xRot);
            entity.xRotO = xRotO;
            entity.attackAnim = attack;
            entity.oAttackAnim = attackO;
            if (customName != null) entity.setCustomName(customName);
            if (entity instanceof Mob mob && mob.isAggressive() != wasAggressive) mob.setAggressive(wasAggressive);
        }
    }
}
