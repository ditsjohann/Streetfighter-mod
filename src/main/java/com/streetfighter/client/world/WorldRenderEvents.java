package com.streetfighter.client.world;

import com.mojang.blaze3d.vertex.PoseStack;
import com.streetfighter.client.FightSession;
import com.streetfighter.fight.FightEngine;
import com.streetfighter.fight.Fighter;
import com.streetfighter.fight.Projectile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.RenderNameTagEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Quaternionf;

/** Forge-bus hooks that make in-world fights happen. */
public final class WorldRenderEvents {
    private WorldRenderEvents() {
    }

    private static boolean isFighter(Entity e) {
        FightSession s = FightSession.current();
        if (s == null) return false;
        Minecraft mc = Minecraft.getInstance();
        return e == s.enemyEntity || e == mc.player;
    }

    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent.Pre event) {
        FightSession s = FightSession.current();
        if (s != null) s.frame();
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent.Pre event) {
        FightSession s = FightSession.current();
        if (s != null) s.tickPre();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderLivingPre(RenderLivingEvent.Pre<?, ?> event) {
        if (FightAnimator.suspended || !isFighter(event.getEntity())) return;
        FightAnimator.pre(event.getEntity(), event.getRenderer().getModel(), event.getPoseStack());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRenderLivingPost(RenderLivingEvent.Post<?, ?> event) {
        if (FightAnimator.suspended || !isFighter(event.getEntity())) return;
        FightAnimator.post(event.getEntity(), event.getRenderer().getModel(), event.getPoseStack());
    }

    @SubscribeEvent
    public static void onNameTag(RenderNameTagEvent event) {
        if (FightCamera.active && (isFighter(event.getEntity()) || FightAnimator.suspended)) event.setResult(Event.Result.DENY);
    }

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        if (FightCamera.active) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (FightCamera.active) event.setRoll(FightCamera.roll);
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        FightSession.abortAll();
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        FightSession s = FightSession.current();
        if (s == null || s.engine == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Vec3 cam = event.getCamera().getPosition();
        PoseStack ps = new PoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
        Arena arena = s.arena;
        FightEngine engine = s.engine;
        float time = s.time;

        for (Projectile p : engine.projectiles) {
            if (p.delay > 0) continue;
            Vec3 w = p.bottomAnchored() ? arena.toWorld(p.x, p.y, 0.5) : arena.toWorldFlat(p.x, p.y);
            int light = LevelRenderer.getLightColor(mc.level, BlockPos.containing(w.x, w.y + 0.6, w.z));
            switch (p.spec.visual) {
                case ARROW -> {
                    if (s.arrowDummy == null) break;
                    Vec3 v = arena.axis.scale(p.vx).add(0, p.vy, 0);
                    float yRot = (float) (Mth.atan2(v.x, v.z) * Mth.RAD_TO_DEG);
                    float xRot = (float) (Mth.atan2(v.y, Math.sqrt(v.x * v.x + v.z * v.z)) * Mth.RAD_TO_DEG);
                    s.arrowDummy.setPos(w.x, w.y, w.z);
                    s.arrowDummy.setYRot(yRot);
                    s.arrowDummy.yRotO = yRot;
                    s.arrowDummy.setXRot(xRot);
                    s.arrowDummy.xRotO = xRot;
                    ps.pushPose();
                    ps.translate(w.x - cam.x, w.y - cam.y, w.z - cam.z);
                    ps.scale(1.6f, 1.6f, 1.6f);
                    dispatcher.render(s.arrowDummy, 0, 0, 0, yRot, 1f, ps, buffers, light);
                    ps.popPose();
                }
                case GOLEM, PIG -> {
                    LivingEntity dummy = p.spec.visual == com.streetfighter.fight.ProjectileVisual.GOLEM ? s.golemDummy : s.pigDummy;
                    if (dummy == null) break;
                    Fighter target = engine.fighter(1 - p.owner);
                    boolean faceRight = p.spec.visual == com.streetfighter.fight.ProjectileVisual.PIG ? p.vx >= 0 : target.x >= p.x;
                    Vec3 face = arena.axis.scale(faceRight ? 1 : -1);
                    float yaw = (float) (Mth.atan2(face.z, face.x) * Mth.RAD_TO_DEG) - 90f;
                    dummy.setPos(w.x, w.y, w.z);
                    dummy.setYRot(yaw);
                    dummy.yRotO = yaw;
                    dummy.yBodyRot = dummy.yBodyRotO = yaw;
                    dummy.yHeadRot = dummy.yHeadRotO = yaw;
                    float k = p.spec.height / (dummy.getBbHeight() * Arena.UNITS_PER_BLOCK);
                    ps.pushPose();
                    ps.translate(w.x - cam.x, w.y - cam.y, w.z - cam.z);
                    ps.scale(k, k, k);
                    dispatcher.render(dummy, 0, 0, 0, yaw, 1f, ps, buffers, light);
                    ps.popPose();
                }
                default -> {
                    ItemStack stack = switch (p.spec.visual) {
                        case DIRT -> new ItemStack(Items.DIRT);
                        case EMERALD -> new ItemStack(Items.EMERALD);
                        case BONE -> new ItemStack(Items.BONE);
                        default -> new ItemStack(Items.SNOWBALL);
                    };
                    float spin = p.age * 14f * (p.vx >= 0 ? 1 : -1);
                    Vec3 f = arena.axis;
                    ps.pushPose();
                    ps.translate(w.x - cam.x, w.y - cam.y, w.z - cam.z);
                    ps.mulPose(new Quaternionf().rotateAxis(-spin * Mth.DEG_TO_RAD, (float) f.z, 0, (float) -f.x));
                    float sc = p.spec.visual == com.streetfighter.fight.ProjectileVisual.DIRT ? 0.75f : 0.8f;
                    ps.scale(sc, sc, sc);
                    mc.getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, ps,
                            buffers, mc.level, p.id);
                    ps.popPose();
                }
            }
        }
        buffers.endBatch();
    }
}
