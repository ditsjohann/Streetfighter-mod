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
    public void update(Vec3 focus, double distance, float shake, float extraRoll, float dt, Entity player) {
        Vec3 dir = arena.normal.scale(side);
        Vec3 desired = focus.add(dir.scale(distance)).add(0, 0.45 + distance * 0.07, 0);
        // pull in front of walls/trees between the camera and the fighters
        double free = rayFree(focus, desired);
        double want = focus.distanceTo(desired);
        if (free < want - 0.05) {
            desired = focus.add(desired.subtract(focus).normalize().scale(Math.max(1.6, free - 0.3)));
        }
        float k = Math.min(1f, dt * 7f);
        if (pos == null) {
            pos = desired;
            target = focus;
        } else {
            pos = pos.lerp(desired, k);
            target = target.lerp(focus, Math.min(1f, dt * 9f));
        }

        Vec3 d = target.subtract(pos);
        float fightYaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90f;
        float fightPitch = (float) -(Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * Mth.RAD_TO_DEG);

        double sx = 0, sy = 0, sz = 0;
        float sroll = 0;
        if (shake > 0) {
            double a = shake * 0.012;
            sx = (random.nextDouble() * 2 - 1) * a;
            sy = (random.nextDouble() * 2 - 1) * a;
            sz = (random.nextDouble() * 2 - 1) * a;
            sroll = (random.nextFloat() * 2 - 1) * shake * 0.12f;
        }

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
