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
        if (FinisherCinematic.current != null && FinisherCinematic.current.hides(event.getEntity())) {
            event.setCanceled(true); // replaced by the x-ray body, or it fell apart
            return;
        }
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
    public static void onFog(ViewportEvent.RenderFog event) {
        FinisherCinematic f = FinisherCinematic.current;
        FightSession s = FightSession.current();
        if (f == null || s == null) return;
        float[] fog = f.fog(s.time, event.getFarPlaneDistance());
        if (fog == null) return;
        event.setNearPlaneDistance(fog[0]);
        event.setFarPlaneDistance(fog[1]);
        event.setFogShape(com.mojang.blaze3d.shaders.FogShape.SPHERE);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        FinisherCinematic f = FinisherCinematic.current;
        FightSession s = FightSession.current();
        if (s == null) return;
        if ((f == null || f.fog(s.time, 192f) == null) && s.ki != null && FightCamera.active) {
            float[] t = s.ki.fogTint();
            if (t == null) return;
            event.setRed(Mth.lerp(t[3], event.getRed(), t[0]));
            event.setGreen(Mth.lerp(t[3], event.getGreen(), t[1]));
            event.setBlue(Mth.lerp(t[3], event.getBlue(), t[2]));
            return;
        }
        if (f == null) return;
        float[] fog = f.fog(s.time, 192f);
        if (fog == null) return;
        float k = f.fogColorMix(s.time);
        event.setRed(Mth.lerp(k, event.getRed(), fog[2]));
        event.setGreen(Mth.lerp(k, event.getGreen(), fog[3]));
        event.setBlue(Mth.lerp(k, event.getBlue(), fog[4]));
    }

    @SubscribeEvent
    public static void onFov(ViewportEvent.ComputeFov event) {
        FightSession s = FightSession.current();
        if (s != null && FightCamera.active && s.fovKick > 0) event.setFOV(event.getFOV() * (1 - s.fovKick));
    }

    @SubscribeEvent
    public static void onRenderFx(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        FightSession s = FightSession.current();
        if (s == null) return;
        Minecraft mc = Minecraft.getInstance();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        PoseStack ps = new PoseStack();
        s.fx.quads.render(ps, buffers, event.getCamera(), s.fx.auras());
        s.fx.quads.render(ps, buffers, event.getCamera());
        buffers.endBatch();
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
            float rx = p.prevX + (p.x - p.prevX) * s.alpha(), ry = p.prevY + (p.y - p.prevY) * s.alpha();
            if (p.exploded || p.spec.visual == com.streetfighter.fight.ProjectileVisual.SHOCKWAVE
                    || p.spec.visual == com.streetfighter.fight.ProjectileVisual.CLOUD
                    || p.spec.visual == com.streetfighter.fight.ProjectileVisual.BLAST
                    || p.spec.visual == com.streetfighter.fight.ProjectileVisual.KI_BLAST
                    || p.spec.visual == com.streetfighter.fight.ProjectileVisual.BEAM
                    || p.spec.visual == com.streetfighter.fight.ProjectileVisual.SPIRIT_BALL) continue;
            Vec3 w = p.bottomAnchored() ? arena.toWorld(rx, ry, 0.5) : arena.toWorldFlat(rx, ry);
            int light = LevelRenderer.getLightColor(mc.level, BlockPos.containing(w.x, w.y + 0.6, w.z));
            switch (p.spec.visual) {
                case ARROW, BIG_ARROW -> {
                    if (s.arrowDummy == null) break;
                    float arrowScale = p.spec.visual == com.streetfighter.fight.ProjectileVisual.BIG_ARROW ? 3.4f : 1.6f;
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
                    ps.scale(arrowScale, arrowScale, arrowScale);
                    dispatcher.render(s.arrowDummy, 0, 0, 0, yRot, 1f, ps, buffers, light);
                    ps.popPose();
                }
                case GOLEM, PIG, GOAT, CHICKEN, ZOMBIE, BABY_ZOMBIE, COW, DONKEY, CREEPER, MINI_SLIME, SPIDER -> {
                    LivingEntity dummy = switch (p.spec.visual) {
                        case GOLEM -> s.golemDummy;
                        case GOAT -> s.goatDummy;
                        case CHICKEN -> s.chickenDummy;
                        case ZOMBIE -> s.zombieDummy;
                        case BABY_ZOMBIE -> s.babyZombieDummy;
                        case COW -> s.cowDummy;
                        case DONKEY -> s.donkeyDummy;
                        case CREEPER -> s.creeperDummy;
                        case MINI_SLIME -> s.slimeDummy;
                        case SPIDER -> s.spiderDummy;
                        default -> s.pigDummy;
                    };
                    if (dummy == null) break;
                    Fighter target = engine.fighter(1 - p.owner);
                    boolean faceRight = p.spec.visual != com.streetfighter.fight.ProjectileVisual.GOLEM ? p.vx >= 0 : target.x >= p.x;
                    if (p.spec.visual == com.streetfighter.fight.ProjectileVisual.CHICKEN) {
                        w = w.add(0, -p.spec.height / 2f / Arena.UNITS_PER_BLOCK, 0); // centred -> feet
                    }
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
                        case COBBLE -> new ItemStack(Items.COBBLESTONE);
                        case EMERALD -> new ItemStack(Items.EMERALD);
                        case BONE -> new ItemStack(Items.BONE);
                        case TNT -> new ItemStack(Items.TNT);
                        case SKULL -> new ItemStack(Items.WITHER_SKELETON_SKULL);
                        case BELL -> new ItemStack(Items.BELL);
                        case EGG -> new ItemStack(Items.EGG);
                        case GOLDEN_EGG -> {
                            ItemStack egg = new ItemStack(Items.EGG);
                            egg.set(net.minecraft.core.component.DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
                            yield egg;
                        }
                        case FEATHER -> new ItemStack(Items.FEATHER);
                        case APPLE -> new ItemStack(Items.APPLE);
                        case ROOTS -> new ItemStack(Items.MANGROVE_ROOTS);
                        case SPIKE -> new ItemStack(Items.POINTED_DRIPSTONE);
                        case RAW_IRON -> new ItemStack(Items.RAW_IRON);
                        case ROTTEN_FLESH -> new ItemStack(Items.ROTTEN_FLESH);
                        case MINECART -> new ItemStack(Items.MINECART);
                        case ANVIL -> new ItemStack(Items.ANVIL);
                        case SLIME -> new ItemStack(Items.SLIME_BLOCK);
                        case WEB, COBWEB -> new ItemStack(Items.COBWEB);
                        case TRIDENT -> new ItemStack(Items.TRIDENT);
                        case PEARL -> new ItemStack(Items.ENDER_PEARL);
                        case BOTTLE -> new ItemStack(Items.HONEY_BOTTLE);
                        case MILK -> new ItemStack(Items.MILK_BUCKET);
                        case CHEST -> new ItemStack(Items.CHEST);
                        case GUNPOWDER -> new ItemStack(Items.GUNPOWDER);
                        case SLIMEBALL -> new ItemStack(Items.SLIME_BALL);
                        case LEATHER -> new ItemStack(Items.LEATHER);
                        case LASSO -> new ItemStack(Items.LEAD);
                        case SWORD, GIANT_SWORD -> new ItemStack(Items.IRON_SWORD);
                        case FIRE_CHARGE -> new ItemStack(Items.FIRE_CHARGE);
                        case FLAME_PILLAR, MAGMA -> new ItemStack(Items.MAGMA_BLOCK);
                        case COAL -> new ItemStack(Items.COAL);
                        case POTION -> net.minecraft.world.item.alchemy.PotionContents.createItemStack(Items.SPLASH_POTION,
                                WorldFx.potionFor(p.spec.effect));
                        default -> new ItemStack(Items.SNOWBALL);
                    };
                    boolean roots = p.spec.visual == com.streetfighter.fight.ProjectileVisual.ROOTS
                            || p.spec.visual == com.streetfighter.fight.ProjectileVisual.SPIKE
                            || p.spec.visual == com.streetfighter.fight.ProjectileVisual.FLAME_PILLAR;
                    boolean magma = p.spec.visual == com.streetfighter.fight.ProjectileVisual.MAGMA;
                    boolean giantSword = p.spec.visual == com.streetfighter.fight.ProjectileVisual.GIANT_SWORD;
                    boolean cart = p.spec.visual == com.streetfighter.fight.ProjectileVisual.MINECART;
                    var vis = p.spec.visual;
                    boolean web = vis == com.streetfighter.fight.ProjectileVisual.WEB;
                    if (vis == com.streetfighter.fight.ProjectileVisual.CHAIR) {
                        ps.pushPose();
                        ps.translate(w.x - cam.x, w.y - cam.y, w.z - cam.z);
                        ps.mulPose(com.mojang.math.Axis.YP.rotation((float) Math.atan2(cam.x - w.x, cam.z - w.z)));
                        ps.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-p.age * 12f * (p.vx >= 0 ? 1 : -1)));
                        PropRenderer.draw(com.streetfighter.fight.Prop.CHAIR, ps, buffers, light, p.id);
                        ps.popPose();
                        break;
                    }
                    boolean trident = vis == com.streetfighter.fight.ProjectileVisual.TRIDENT;
                    float spin = roots || cart || web || magma ? 0 : giantSword ? 135 : trident ? (p.vx >= 0 ? -45 : 45)
                            : p.age * 14f * (p.vx >= 0 ? 1 : -1);
                    Vec3 f = arena.axis;
                    ps.pushPose();
                    ps.translate(w.x - cam.x, w.y - cam.y, w.z - cam.z);
                    ps.mulPose(new Quaternionf().rotateAxis(-spin * Mth.DEG_TO_RAD, (float) f.z, 0, (float) -f.x));
                    float sc = switch (p.spec.visual) {
                        case DIRT, COBBLE -> 0.75f;
                        case TNT -> 0.85f + (p.age % 10 < 5 ? 0.06f : 0f); // pulsing fuse
                        case BELL -> 1.6f;
                        case SKULL -> 0.9f;
                        case GOLDEN_EGG -> 0.85f;
                        case EGG, POTION -> 0.7f;
                        default -> 0.8f;
                    };
                    if (p.spec.visual == com.streetfighter.fight.ProjectileVisual.BELL) {
                        ps.translate(0, 0.7, 0);
                    }
                    if (web) {
                        ps.translate(0, 0.35, 0);
                        ps.scale(1.8f, 1.8f, 1.8f);
                    }
                    if (vis == com.streetfighter.fight.ProjectileVisual.ANVIL || trident) ps.scale(1.6f, 1.6f, 1.6f);
                    if (magma) {
                        // a glowing patch of hot coals on the ground
                        ps.translate(0, 0.06, 0);
                        ps.scale(2.4f, 0.22f, 1.3f);
                        light = 0xF000F0;
                    }
                    if (giantSword) ps.scale(5.5f, 5.5f, 5.5f);
                    if (vis == com.streetfighter.fight.ProjectileVisual.SWORD) ps.scale(1.4f, 1.4f, 1.4f);
                    if (cart) {
                        ps.translate(0, 0.45, 0);
                        ps.scale(2.2f, 2.2f, 2.2f);
                    }
                    if (roots) {
                        // roots burst out of the ground, then sink back
                        float life = p.spec.lifetime;
                        float rise = Math.min(1f, p.age / 5f) * Math.min(1f, (life - p.age) / 8f);
                        float h = p.spec.height / Arena.UNITS_PER_BLOCK;
                        ps.translate(0, -h * 0.5 + h * rise * 0.5, 0);
                        ps.scale(1.6f, h * Math.max(0.05f, rise) * 1.1f, 1.6f);
                    }
                    ps.scale(sc, sc, sc);
                    mc.getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, ps,
                            buffers, mc.level, p.id);
                    ps.popPose();
                }
            }
        }
        PropRenderer.render(s, ps, cam, buffers, dispatcher);
        buffers.endBatch();
        if (s.finisher != null) s.finisher.renderWorld(ps, cam, buffers);
        if (s.ki != null) s.ki.renderWorld(ps, cam, buffers);
    }
}
