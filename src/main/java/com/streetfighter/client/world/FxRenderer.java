package com.streetfighter.client.world;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.streetfighter.StreetFighterMod;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Additive-blended effect quads drawn in the world: hit sparks, shockwave rings, strike swooshes, glows and
 * speed streaks. Billboards face the camera; swooshes and streaks lie in the fight plane.
 */
public final class FxRenderer {
    public enum Tex {
        SPARK("spark"), RING("ring"), GLOW("glow"), SWOOSH("swoosh"), STREAK("streak");

        final ResourceLocation location;

        Tex(String name) {
            this.location = ResourceLocation.fromNamespaceAndPath(StreetFighterMod.MODID, "textures/fx/" + name + ".png");
        }
    }

    /** One live effect. Sizes in blocks, life in seconds, colour 0xRRGGBB. */
    public static final class Fx {
        final Tex tex;
        Vec3 pos;
        final float size0, size1, life;
        final float rot, rotSpeed;
        final int color;
        final boolean plane;
        final float planeAngle, aspect;
        final boolean mirror;
        float age;

        Fx(Tex tex, Vec3 pos, float size0, float size1, float life, float rot, float rotSpeed, int color,
           boolean plane, float planeAngle, float aspect, boolean mirror) {
            this.tex = tex;
            this.pos = pos;
            this.size0 = size0;
            this.size1 = size1;
            this.life = life;
            this.rot = rot;
            this.rotSpeed = rotSpeed;
            this.color = color;
            this.plane = plane;
            this.planeAngle = planeAngle;
            this.aspect = aspect;
            this.mirror = mirror;
        }
    }

    private final List<Fx> list = new ArrayList<>();
    private final Random random = new Random();
    private Vec3 planeU = new Vec3(1, 0, 0);

    public void setPlane(Vec3 axis) {
        this.planeU = axis;
    }

    public void spark(Vec3 p, float size, int color) {
        list.add(new Fx(Tex.SPARK, p, size * 0.4f, size, 0.16f, random.nextFloat() * 360, 120, color, false, 0, 1, false));
        list.add(new Fx(Tex.GLOW, p, size * 0.6f, size * 0.9f, 0.12f, 0, 0, color, false, 0, 1, false));
    }

    public void ring(Vec3 p, float from, float to, float life, int color) {
        list.add(new Fx(Tex.RING, p, from, to, life, 0, 0, color, false, 0, 1, false));
    }

    public void glow(Vec3 p, float size, float life, int color) {
        list.add(new Fx(Tex.GLOW, p, size, size * 1.2f, life, 0, 0, color, false, 0, 1, false));
    }

    /** A crescent swoosh in the fight plane. {@code angle} rotates it in the plane (0 = horizontal arc). */
    public void swoosh(Vec3 p, float size, float angle, boolean faceRight, int color) {
        list.add(new Fx(Tex.SWOOSH, p, size, size * 1.15f, 0.18f, 0, 0, color, true, angle, 0.5f, !faceRight));
    }

    public void streak(Vec3 p, float length, boolean faceRight, int color) {
        list.add(new Fx(Tex.STREAK, p, length, length, 0.2f, 0, 0, color, true, 0, 0.18f, !faceRight));
    }

    public void step(float dt) {
        Iterator<Fx> it = list.iterator();
        while (it.hasNext()) {
            Fx f = it.next();
            f.age += dt;
            if (f.age >= f.life) it.remove();
        }
    }

    public void clear() {
        list.clear();
    }

    /** Draw everything (camera-relative) with additive blending. */
    public void render(PoseStack ps, MultiBufferSource buffers, Camera camera) {
        render(ps, buffers, camera, list);
    }

    public void render(PoseStack ps, MultiBufferSource buffers, Camera camera, List<Fx> extra) {
        Vec3 cam = camera.getPosition();
        for (Fx f : extra) {
            float t = Math.min(1f, f.age / f.life);
            float size = f.size0 + (f.size1 - f.size0) * (1 - (1 - t) * (1 - t));
            float fade = t < 0.15f ? t / 0.15f : 1f - (t - 0.15f) / 0.85f;
            fade = Math.max(0, Math.min(1, fade));
            int r = (int) (((f.color >> 16) & 255) * fade), g = (int) (((f.color >> 8) & 255) * fade), b = (int) ((f.color & 255) * fade);
            VertexConsumer vc = buffers.getBuffer(RenderType.eyes(f.tex.location));
            ps.pushPose();
            ps.translate(f.pos.x - cam.x, f.pos.y - cam.y, f.pos.z - cam.z);
            Matrix4f m;
            if (!f.plane) {
                ps.mulPose(camera.rotation());
                ps.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(f.rot + f.rotSpeed * f.age));
                m = ps.last().pose();
                float h = size / 2;
                quad(vc, ps, m, -h, -h, h, h, r, g, b, false);
            } else {
                // build the quad in the fight plane: u = arena axis, v = up
                float w = size / 2, h = size * f.aspect / 2;
                float ca = (float) Math.cos(Math.toRadians(f.planeAngle)), sa = (float) Math.sin(Math.toRadians(f.planeAngle));
                Vec3 u = planeU.scale(f.mirror ? -1 : 1);
                float ux = (float) u.x, uz = (float) u.z;
                // corners (x along u, y along up), rotated by planeAngle
                float[][] c = {{-w, -h}, {w, -h}, {w, h}, {-w, h}};
                m = ps.last().pose();
                float[] uv = f.mirror ? new float[]{1, 1, 0, 1, 0, 0, 1, 0} : new float[]{0, 1, 1, 1, 1, 0, 0, 0};
                for (int pass = 0; pass < 2; pass++) { // double-sided
                    for (int i = 0; i < 4; i++) {
                        int idx = pass == 0 ? i : 3 - i;
                        float lx = c[idx][0] * ca - c[idx][1] * sa;
                        float ly = c[idx][0] * sa + c[idx][1] * ca;
                        vc.addVertex(m, ux * lx, ly, uz * lx).setColor(r, g, b, 255)
                                .setUv(uv[idx * 2], uv[idx * 2 + 1]).setOverlay(OverlayTexture.NO_OVERLAY)
                                .setLight(0xF000F0).setNormal(ps.last(), 0, 1, 0);
                    }
                }
            }
            ps.popPose();
        }
    }

    private static void quad(VertexConsumer vc, PoseStack ps, Matrix4f m, float x0, float y0, float x1, float y1,
                             int r, int g, int b, boolean flipU) {
        vc.addVertex(m, x0, y0, 0).setColor(r, g, b, 255).setUv(0, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(ps.last(), 0, 0, 1);
        vc.addVertex(m, x1, y0, 0).setColor(r, g, b, 255).setUv(1, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(ps.last(), 0, 0, 1);
        vc.addVertex(m, x1, y1, 0).setColor(r, g, b, 255).setUv(1, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(ps.last(), 0, 0, 1);
        vc.addVertex(m, x0, y1, 0).setColor(r, g, b, 255).setUv(0, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(ps.last(), 0, 0, 1);
    }

    /** One-frame effects (auras) that are rebuilt every frame. */
    public static Fx transientGlow(Vec3 p, float size, int color) {
        Fx f = new Fx(Tex.GLOW, p, size, size, 1f, 0, 0, color, false, 0, 1, false);
        f.age = 0.3f;
        return f;
    }
}
