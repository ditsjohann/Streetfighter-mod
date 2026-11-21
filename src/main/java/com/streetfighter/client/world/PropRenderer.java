package com.streetfighter.client.world;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.streetfighter.client.FightSession;
import com.streetfighter.fight.FieldProp;
import com.streetfighter.fight.FightEngine;
import com.streetfighter.fight.Fighter;
import com.streetfighter.fight.Prop;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Draws villager-thrown props: lying in the arena, flying in, held and swung by fighters, and the villager. */
public final class PropRenderer {
    private static final BlockState SEAT = Blocks.OAK_PLANKS.defaultBlockState();
    private static final BlockState LEG = Blocks.DARK_OAK_PLANKS.defaultBlockState();

    private PropRenderer() {
    }

    public static ItemStack stackOf(Prop p) {
        return switch (p) {
            case TNT -> new ItemStack(Items.TNT);
            case ANVIL -> new ItemStack(Items.ANVIL);
            case SLIME -> new ItemStack(Items.SLIME_BLOCK);
            case COBWEB -> new ItemStack(Items.COBWEB);
            case DIAMOND_SWORD -> new ItemStack(Items.DIAMOND_SWORD);
            case FISHING_ROD -> new ItemStack(Items.FISHING_ROD);
            case TRIDENT -> new ItemStack(Items.TRIDENT);
            case ENDER_PEARL -> new ItemStack(Items.ENDER_PEARL);
            case CHAIR -> new ItemStack(Items.OAK_STAIRS);
            case BOTTLE -> new ItemStack(Items.HONEY_BOTTLE);
        };
    }

    /** Draws a prop centred on the pose origin, about one block big. */
    public static void draw(Prop p, PoseStack ps, MultiBufferSource buf, int light, int seed) {
        Minecraft mc = Minecraft.getInstance();
        if (p == Prop.CHAIR) {
            // a real little wooden chair: four legs, a seat and a backrest
            BlockRenderDispatcher br = mc.getBlockRenderer();
            ps.pushPose();
            ps.translate(0, -0.5, 0);
            int o = OverlayTexture.NO_OVERLAY;
            for (int i = 0; i < 4; i++) {
                float lx = (i % 2 == 0) ? -0.35f : 0.25f, lz = (i < 2) ? -0.35f : 0.25f;
                TreantRenderer.cube(ps, br, buf, light, o, LEG, lx, 0f, lz, 0.1f, 0.45f, 0.1f);
            }
            TreantRenderer.cube(ps, br, buf, light, o, SEAT, -0.4f, 0.45f, -0.4f, 0.8f, 0.1f, 0.8f);
            TreantRenderer.cube(ps, br, buf, light, o, SEAT, -0.4f, 0.55f, -0.4f, 0.8f, 0.55f, 0.1f);
            TreantRenderer.cube(ps, br, buf, light, o, LEG, -0.4f, 1.05f, -0.42f, 0.8f, 0.08f, 0.14f);
            ps.popPose();
            return;
        }
        float sc = p.block ? 0.75f : 1.0f;
        ps.pushPose();
        ps.scale(sc, sc, sc);
        mc.getItemRenderer().renderStatic(stackOf(p), ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, ps, buf,
                mc.level, seed);
        ps.popPose();
    }

    public static void render(FightSession s, PoseStack ps, Vec3 cam, MultiBufferSource buf, EntityRenderDispatcher dispatcher) {
        FightEngine e = s.engine;
        Minecraft mc = Minecraft.getInstance();
        if (e == null || mc.level == null) return;
        Arena arena = s.arena;
        Vec3 toCam = arena.normal.scale(s.camera.side());
        float a = s.alpha();
        float t = s.time;

        // ---- props in the arena
        for (FieldProp p : e.props) {
            float rx = p.prevX + (p.x - p.prevX) * a, ry = p.prevY + (p.y - p.prevY) * a;
            if (p.landed && p.groundLeft < 120 && ((int) (t * 8)) % 2 == 0) continue; // blink before vanishing
            Vec3 w = arena.toWorld(rx, ry, 0.3).add(0, p.landed ? 0.35 + Math.sin(t * 3 + p.id) * 0.06 : 0.3, 0);
            int light = light(mc, w);
            ps.pushPose();
            ps.translate(w.x - cam.x, w.y - cam.y, w.z - cam.z);
            if (p.landed) ps.mulPose(Axis.YP.rotationDegrees(t * 90 + p.id * 40));
            else {
                face(ps, w, cam);
                ps.mulPose(Axis.ZP.rotationDegrees(-p.age * 15f * Math.signum(p.vx == 0 ? 1 : p.vx)));
            }
            draw(p.kind, ps, buf, light, p.id);
            ps.popPose();
        }

        // ---- held props (and the one being thrown/used this move)
        for (Fighter f : new Fighter[]{e.player, e.enemy}) {
            Prop held = f.heldProp;
            Prop using = f.state == Fighter.State.ATTACK ? Prop.of(f.move) : null;
            if (using != null && f.move.projectile != null && f.curFrame >= f.move.projectileFrame) using = null;
            if (using != null && f.move.randomBrew && f.curFrame > f.move.startup) using = null;
            Prop show = using != null ? using : held;
            if (show == null) continue;
            Vec3 face = arena.axis.scale(f.facingRight ? 1 : -1);
            double hand = f.hurtHeight() * 0.52 / Arena.UNITS_PER_BLOCK;
            Vec3 w = s.worldOf(f).add(0, hand, 0).add(face.scale(0.42)).add(toCam.scale(0.35));
            float swing = 0;
            if (using != null) {
                var m = f.move;
                float fr = Math.max(0, f.curFrame) + a;
                float end = m.startup + Math.max(1, m.active);
                if (fr < m.startup) swing = 80f * (fr / Math.max(1, m.startup)); // wind up behind
                else if (fr < end) swing = Mth.lerp((fr - m.startup) / Math.max(1f, m.active), 80f, -70f);
                else swing = -70f + 70f * Math.min(1f, (fr - end) / Math.max(1f, m.recovery));
                if (m.teleport || m.randomBrew) swing = 30f * Mth.sin(fr * 0.5f);
            } else {
                swing = 10f + Mth.sin(t * 4 + f.index) * 6f;
                if (f.heldTimer < 90 && ((int) (t * 10)) % 2 == 0) continue; // about to run out
            }
            int light = light(mc, w);
            ps.pushPose();
            ps.translate(w.x - cam.x, w.y - cam.y, w.z - cam.z);
            face(ps, w, cam);
            boolean screenRight = screenRight(face, w, cam);
            if (!screenRight) ps.mulPose(Axis.YP.rotationDegrees(180));
            ps.mulPose(Axis.ZP.rotationDegrees(swing));
            float k = show == Prop.TRIDENT || show == Prop.DIAMOND_SWORD || show == Prop.FISHING_ROD ? 1.15f : 0.8f;
            ps.scale(k, k, k);
            draw(show, ps, buf, light, f.index);
            ps.popPose();
        }

        // ---- the funny villager
        if (e.villagerTimer > 0 && s.propVillager != null) {
            LivingEntity v = s.propVillager;
            int vt = e.villagerTimer;
            float pop = Math.min(1f, (110 - vt) / 8f) * Math.min(1f, vt / 10f);
            float hop = vt > 72 && vt < 84 ? (float) Math.sin((84 - vt) / 12f * Math.PI) * 0.35f : (float) Math.abs(Math.sin(t * 5)) * 0.05f;
            Vec3 w = arena.toWorld(e.villagerX, 0, 0.3).add(toCam.scale(-1.2)).add(0, hop, 0);
            Vec3 face = arena.axis.scale(e.villagerFacingRight ? 1 : -1).scale(0.8).add(toCam.scale(0.6));
            float yaw = (float) (Mth.atan2(face.z, face.x) * Mth.RAD_TO_DEG) - 90f;
            v.setPos(w.x, w.y, w.z);
            v.setYRot(yaw);
            v.yRotO = yaw;
            v.yBodyRot = v.yBodyRotO = yaw;
            v.yHeadRot = v.yHeadRotO = yaw + (vt > 70 && vt < 80 ? 0 : (float) Math.sin(t * 3) * 25f);
            v.setXRot(vt > 74 && vt < 80 ? -20 : 0);
            v.xRotO = v.getXRot();
            ps.pushPose();
            ps.translate(w.x - cam.x, w.y - cam.y, w.z - cam.z);
            ps.scale(pop, pop, pop);
            dispatcher.render(v, 0, 0, 0, yaw, 1f, ps, buf, light(mc, w.add(0, 1, 0)));
            ps.popPose();
        }
    }

    /** Turns the pose so local +Z points at the camera (billboard around the vertical axis). */
    private static void face(PoseStack ps, Vec3 w, Vec3 cam) {
        double dx = cam.x - w.x, dz = cam.z - w.z;
        ps.mulPose(Axis.YP.rotation((float) Math.atan2(dx, dz)));
    }

    /** Whether a world direction points to the right side of the screen. */
    private static boolean screenRight(Vec3 dir, Vec3 w, Vec3 cam) {
        double dx = cam.x - w.x, dz = cam.z - w.z;
        double yaw = Math.atan2(dx, dz);
        double rx = Math.cos(yaw), rz = -Math.sin(yaw); // local +X after facing the camera
        return dir.x * rx + dir.z * rz >= 0;
    }

    private static int light(Minecraft mc, Vec3 w) {
        return LevelRenderer.getLightColor(mc.level, BlockPos.containing(w));
    }
}
