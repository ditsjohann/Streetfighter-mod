package com.streetfighter.client.world;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Random;

/**
 * Cinematic side-view camera for in-world fights. The values are read by {@code CameraMixin} every frame.
 * It flies out of the player's eyes into a side view, follows and zooms with the fighters, punches in on
 * supers, shakes on big hits and flies back into the player's head when the fight ends.
 */
public final class FightCamera {
    public static volatile boolean active;
    public static double x, y, z;
    public static float yaw, pitch, roll;

    private static final float TRANSITION_SECONDS = 1.1f;
    private final Random random = new Random();
    private final Arena arena;
    private final int side;

    private Vec3 pos, target;
    private Vec3 posVel = Vec3.ZERO, targetVel = Vec3.ZERO;
    /** Directional kick (a short shove along the hit direction that springs back). */
    private Vec3 kick = Vec3.ZERO, kickVel = Vec3.ZERO;
    private float noiseT;
    private Vec3 fromEye;
    private float fromYaw, fromPitch;
    private float transition; // 0..1 into the fight
    private boolean leaving;
    private float leaveProgress;
    private Vec3 leaveFromPos;
    private float leaveFromYaw, leaveFromPitch;

    public FightCamera(Arena arena, Entity player) {
        this.arena = arena;
        this.fromEye = player.getEyePosition();
        this.fromYaw = player.getYRot();
        this.fromPitch = player.getXRot();
        // Prefer the side that keeps the player on the left of the screen, unless it's blocked by terrain.
        double clearPlus = clearance(+1), clearMinus = clearance(-1);
        this.side = clearPlus >= 4.5 || clearPlus >= clearMinus ? 1 : -1;
    }

    private double clearance(int s) {
        Vec3 from = arena.toWorldFlat(arena.centerX, 80);
        Vec3 to = from.add(arena.normal.scale(s * 9.0)).add(0, 1.2, 0);
        return rayFree(from, to);
    }

    private double rayFree(Vec3 from, Vec3 to) {
        var level = Minecraft.getInstance().level;
        if (level == null) return from.distanceTo(to);
        BlockHitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE,
                net.minecraft.world.phys.shapes.CollisionContext.empty()));
        return hit.getType() == HitResult.Type.MISS ? from.distanceTo(to) : from.distanceTo(hit.getLocation());
    }

    public int side() {
        return side;
    }

    /** Horizontal direction the camera looks along (from camera to the fight). */
    public Vec3 viewDir() {
        return arena.normal.scale(-side);
    }

    public void start() {
        active = true;
        transition = 0;
        leaving = false;
    }

    /** Begin flying back into the player's eyes. */
    public void leave(Entity player) {
        if (leaving) return;
        leaving = true;
        leaveProgress = 0;
        leaveFromPos = new Vec3(x, y, z);
        leaveFromYaw = yaw;
        leaveFromPitch = pitch;
        fromEye = player.getEyePosition();
        fromYaw = player.getYRot();
        fromPitch = player.getXRot();
    }

    public boolean finishedLeaving() {
        return leaving && leaveProgress >= 1f;
    }

    /**
     * @param focus      world point the camera centres on
     * @param distance   how far back the camera sits
     * @param shake      shake strength (engine shake units)
     * @param extraRoll  dramatic roll in degrees
     */
    /** Shoves the camera along {@code dir} (world space); it springs back within a few frames. */
    public void kick(Vec3 dir, float strength) {
        kickVel = kickVel.add(dir.scale(strength));
    }

    private Double shotHeight;
    private boolean cutNext;

    /** Cinematic override for the next update: fixed height above the focus, and optionally a hard cut (no spring). */
    public void setNextShot(double height, boolean cut) {
        shotHeight = height;
        cutNext |= cut;
    }

    public void update(Vec3 focus, double distance, float shake, float extraRoll, float dt, Entity player) {
        update(focus, distance, shake, extraRoll, 0f, dt, player);
    }

    /** Critically damped spring step: smooth, no overshoot, frame-rate independent. */
    private static Vec3[] spring(Vec3 x, Vec3 v, Vec3 goal, float omega, float dt) {
        float f = 1f + 2f * dt * omega;
        float oo = omega * omega, hoo = dt * oo, hhoo = dt * hoo;
        float det = 1f / (f + hhoo);
        Vec3 nx = x.scale(f).add(v.scale(dt)).add(goal.scale(hhoo)).scale(det);
        Vec3 nv = v.add(goal.subtract(x).scale(hoo)).scale(det);
        return new Vec3[]{nx, nv};
    }

    /**
     * @param orbitDeg swing the camera around the fight (supers, K.O.s)
     */
    public void update(Vec3 focus, double distance, float shake, float extraRoll, float orbitDeg, float dt, Entity player) {
        double oa = Math.toRadians(orbitDeg);
        Vec3 n = arena.normal.scale(side), ax = arena.axis;
        Vec3 dir = n.scale(Math.cos(oa)).add(ax.scale(Math.sin(oa)));
        double lift = shotHeight != null ? shotHeight : 0.45 + distance * 0.07;
        boolean cut = cutNext;
        shotHeight = null;
        cutNext = false;
        Vec3 desired = focus.add(dir.scale(distance)).add(0, lift, 0);
        // pull in front of walls/trees between the camera and the fighters
        double free = rayFree(focus, desired);
        double want = focus.distanceTo(desired);
        if (free < want - 0.05) {
            desired = focus.add(desired.subtract(focus).normalize().scale(Math.max(Math.min(1.6, want * 0.6), free - 0.3)));
        }
        if (pos == null || cut) {
            posVel = Vec3.ZERO;
            targetVel = Vec3.ZERO;
            pos = desired;
            target = focus;
        } else {
            Vec3[] p1 = spring(pos, posVel, desired, 6.5f, dt);
            pos = p1[0];
            posVel = p1[1];
            Vec3[] t1 = spring(target, targetVel, focus, 9f, dt);
            target = t1[0];
            targetVel = t1[1];
        }
        // directional kick: a damped spring pulling back to zero
        kickVel = kickVel.add(kick.scale(-260f * dt)).scale(Math.max(0, 1f - 18f * dt));
        kick = kick.add(kickVel.scale(dt));

        Vec3 d = target.subtract(pos);
        float fightYaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90f;
        float fightPitch = (float) -(Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * Mth.RAD_TO_DEG);

        // trauma shake: squared, so light hits barely nudge and heavy ones kick; smooth noise, not per-frame jitter
        noiseT += dt * 28f;
        float trauma = Mth.clamp(shake / 11f, 0f, 1f);
        float amp = trauma * trauma;
        double sx = (Math.sin(noiseT * 1.13) + Math.sin(noiseT * 2.71 + 1.3) * 0.5) * amp * 0.11 + kick.x;
        double sy = (Math.sin(noiseT * 1.57 + 2.1) + Math.sin(noiseT * 3.07) * 0.5) * amp * 0.09 + kick.y;
        double sz = (Math.sin(noiseT * 0.97 + 4.2) + Math.sin(noiseT * 2.33 + 0.7) * 0.5) * amp * 0.11 + kick.z;
        float sroll = (float) (Math.sin(noiseT * 1.41 + 0.4) * amp * 2.2);

        if (leaving) {
            leaveProgress = Math.min(1f, leaveProgress + dt / TRANSITION_SECONDS);
            float e = ease(leaveProgress);
            Vec3 eye = player.getEyePosition();
            Vec3 p = leaveFromPos.lerp(eye, e);
            x = p.x;
            y = p.y;
            z = p.z;
            yaw = lerpAngle(leaveFromYaw, player.getYRot(), e);
            pitch = Mth.lerp(e, leaveFromPitch, player.getXRot());
            roll = 0;
            return;
        }
        transition = Math.min(1f, transition + dt / TRANSITION_SECONDS);
        float e = ease(transition);
        Vec3 p = fromEye.lerp(pos, e);
        x = p.x + sx;
        y = p.y + sy;
        z = p.z + sz;
        yaw = lerpAngle(fromYaw, fightYaw, e);
        pitch = Mth.lerp(e, fromPitch, fightPitch);
        roll = (sroll + extraRoll) * e;
    }

    private static float ease(float t) {
        return t * t * (3 - 2 * t);
    }

    private static float lerpAngle(float a, float b, float t) {
        return a + Mth.wrapDegrees(b - a) * t;
    }

    public static void reset() {
        active = false;
        roll = 0;
    }
}
