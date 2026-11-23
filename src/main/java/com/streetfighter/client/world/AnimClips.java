package com.streetfighter.client.world;

import com.streetfighter.fight.AnimPose;

import java.util.EnumMap;
import java.util.Map;

/**
 * Keyframe data for every attack animation. Each clip has a wind-up key (reached at the end of startup) and a
 * strike key (held through the active frames); the animator eases guard -> wind-up -> strike -> guard.
 * Channel values are absolute model rotations in radians (arms/legs/body/head) plus root transforms
 * (lean in degrees, forward/up in blocks, vertical squash, flip and spin in degrees). NaN = keep the guard value.
 */
public final class AnimClips {
    // channels
    public static final int RAX = 0, RAY = 1, RAZ = 2, LAX = 3, LAY = 4, LAZ = 5, RLX = 6, RLZ = 7, LLX = 8, LLZ = 9,
            BX = 10, BY = 11, HX = 12, HY = 13, LEAN = 14, FWD = 15, UP = 16, SQUASH = 17, FLIP = 18, SPIN = 19;
    public static final int N = 20;
    private static final float X = Float.NaN;

    public record Clip(float[] windup, float[] strike) {
    }

    /** Fighting stance for humanoids. */
    public static float[] humanoidGuard() {
        float[] g = new float[N];
        g[RAX] = -1.25f; g[RAY] = -0.35f; g[RAZ] = 0.1f;
        g[LAX] = -1.45f; g[LAY] = 0.45f; g[LAZ] = -0.1f;
        g[RLX] = 0.3f; g[RLZ] = 0.06f; g[LLX] = -0.3f; g[LLZ] = -0.06f;
        g[BX] = 0; g[BY] = 0.25f; g[HX] = 0.05f; g[HY] = -0.2f;
        g[LEAN] = 4; g[FWD] = 0; g[UP] = 0; g[SQUASH] = 1; g[FLIP] = 0; g[SPIN] = 0;
        return g;
    }

    /** Chicken: RAZ = wing spread (both wings), RLX/LLX = legs, HX = head/beak, LEAN = body pitch. */
    public static float[] birdGuard() {
        float[] g = new float[N];
        g[SQUASH] = 1;
        g[RAZ] = 0.15f;
        g[HX] = -0.1f;
        g[LEAN] = -4;
        return g;
    }

    public static float[] quadGuard() {
        float[] g = new float[N];
        g[SQUASH] = 1;
        return g;
    }

    /** Builds a key from (channel, value) pairs; everything else is NaN (inherit). */
    private static float[] k(float... pairs) {
        float[] out = new float[N];
        java.util.Arrays.fill(out, X);
        for (int i = 0; i + 1 < pairs.length; i += 2) out[(int) pairs[i]] = pairs[i + 1];
        return out;
    }

    private static final Map<AnimPose, Clip> HUMANOID = new EnumMap<>(AnimPose.class);
    private static final Map<AnimPose, Clip> QUAD = new EnumMap<>(AnimPose.class);
    private static final Map<AnimPose, Clip> BIRD = new EnumMap<>(AnimPose.class);

    private static void b(float[] windup, float[] strike, AnimPose... poses) {
        for (AnimPose p : poses) BIRD.put(p, new Clip(windup, strike));
    }

    private static void h(AnimPose p, float[] windup, float[] strike) {
        HUMANOID.put(p, new Clip(windup, strike));
    }

    private static void q(AnimPose p, float[] windup, float[] strike) {
        QUAD.put(p, new Clip(windup, strike));
    }

    static {
        // ---------------- punches
        h(AnimPose.JAB, k(RAX, -1.0f, RAY, 0.2f, BY, 0.4f),
                k(RAX, -1.62f, RAY, -0.25f, RAZ, 0, BY, -0.2f, LEAN, 10, FWD, 0.08f));
        h(AnimPose.STRAIGHT, k(RAX, -0.7f, RAY, 0.45f, BY, 0.55f, LEAN, 0),
                k(RAX, -1.66f, RAY, -0.3f, RAZ, 0, BY, -0.6f, LEAN, 14, FWD, 0.16f, RLX, -0.1f, LLX, 0.55f));
        h(AnimPose.HOOK, k(RAX, -1.3f, RAY, 0.95f, RAZ, 0.7f, BY, 0.75f, LEAN, 2),
                k(RAX, -1.5f, RAY, -0.95f, RAZ, -0.35f, BY, -0.95f, LEAN, 12, FWD, 0.12f));
        h(AnimPose.OVERHEAD, k(RAX, -3.0f, RAY, 0, LAX, -2.8f, LAY, 0, HX, -0.45f, LEAN, -12, UP, 0.05f),
                k(RAX, -0.5f, LAX, -0.7f, RAY, -0.1f, LAY, 0.1f, HX, 0.45f, LEAN, 24, FWD, 0.16f));
        h(AnimPose.LOW_JAB, k(RAX, -0.9f, RAY, 0.2f),
                k(RAX, -1.5f, RAY, -0.2f, RAZ, 0, LEAN, 8, FWD, 0.06f));
        h(AnimPose.BODY_BLOW, k(RAX, -0.4f, RAY, 0.45f, BY, 0.5f),
                k(RAX, -1.3f, RAY, -0.4f, BY, -0.45f, LEAN, 10, FWD, 0.1f));
        h(AnimPose.ANTI_AIR, k(RAX, -0.3f, RAY, 0.3f, SQUASH, 0.84f, LEAN, 6),
                k(RAX, -3.05f, RAY, 0, RAZ, -0.2f, LAX, -0.6f, LAZ, -0.4f, HX, -0.5f, LEAN, -6, RLX, -0.3f, UP, 0.06f));
        h(AnimPose.UPPERCUT, k(RAX, -0.2f, RAY, 0.45f, SQUASH, 0.8f, LEAN, 10, LAX, -1.2f),
                k(RAX, -3.1f, RAY, 0, RAZ, -0.15f, LAX, -0.4f, LAZ, -0.6f, RLX, -0.95f, LLX, 0.4f, HX, -0.5f, LEAN, -8));
        h(AnimPose.HEADBUTT, k(HX, -0.65f, LEAN, -12, RAX, -0.8f, LAX, -0.8f),
                k(HX, 0.6f, LEAN, 24, FWD, 0.18f, RAX, 0.45f, LAX, 0.45f, RAY, 0, LAY, 0));
        h(AnimPose.PALM, k(RAX, -1.0f, RAY, 0.65f, BY, 0.5f),
                k(RAX, -1.55f, RAY, -0.1f, RAZ, 0, LAX, -1.45f, LAY, 0.2f, BY, -0.4f, LEAN, 12, FWD, 0.12f));
        // ---------------- kicks
        h(AnimPose.SHIN_KICK, k(RLX, -0.35f, LEAN, -2),
                k(RLX, -1.05f, RLZ, 0.12f, LEAN, -7, RAX, -0.7f, RAZ, 0.4f));
        h(AnimPose.FRONT_KICK, k(RLX, -0.95f, LEAN, -4, LLX, 0.1f),
                k(RLX, -1.55f, RLZ, 0, LEAN, -15, RAX, -0.45f, RAZ, 0.7f, LAX, -0.45f, LAZ, -0.7f, FWD, 0.07f));
        h(AnimPose.ROUNDHOUSE, k(RLX, -0.6f, RLZ, 0.55f, BY, 0.65f, SPIN, 25),
                k(RLX, -1.5f, RLZ, 1.15f, BY, -0.5f, LEAN, -18, SPIN, -35, RAX, -0.3f, RAZ, 0.85f, LAX, -0.3f, LAZ, -0.85f));
        h(AnimPose.LOW_KICK, k(RLX, -0.4f), k(RLX, -1.35f, LEAN, -6, FWD, 0.05f));
        h(AnimPose.POKE_KICK, k(RLX, -0.5f), k(RLX, -1.55f, RLZ, 0.1f, LEAN, -10, FWD, 0.12f));
        h(AnimPose.SWEEP, k(SQUASH, 0.74f, SPIN, 30, RLX, -0.5f),
                k(RLX, -1.57f, RLZ, 1.3f, SQUASH, 0.6f, SPIN, -70, LEAN, -10, RAZ, 0.9f, LAZ, -0.9f, RAX, -0.2f, LAX, -0.2f));
        h(AnimPose.AXE_KICK, k(RLX, -2.6f, LEAN, -12), k(RLX, -0.6f, LEAN, 18, FWD, 0.1f));
        h(AnimPose.SPIN_KICK, k(RLX, -0.45f, SPIN, 40),
                k(RLX, -1.57f, RLZ, 0.25f, LLX, 0.3f, RAX, -0.2f, RAZ, 1.4f, LAX, -0.2f, LAZ, -1.4f, LEAN, -6));
        // ---------------- air
        h(AnimPose.AIR_PUNCH, k(RAX, -1.0f, RLX, -1.0f, LLX, -0.4f),
                k(RAX, -1.95f, RAY, -0.2f, RLX, -1.0f, LLX, -0.4f, LEAN, 12));
        h(AnimPose.AIR_SLAM, k(RAX, -3.0f, LAX, -2.8f, RLX, -0.9f, LLX, -0.3f, LEAN, -10),
                k(RAX, -0.6f, LAX, -0.6f, RAY, 0, LAY, 0, LEAN, 22, RLX, -0.6f, LLX, -0.2f));
        h(AnimPose.AIR_KICK, k(RLX, -1.0f, LLX, -0.5f),
                k(RLX, -1.35f, LLX, -0.2f, LEAN, -6, RAZ, 0.6f, LAZ, -0.6f));
        h(AnimPose.FLYING_KICK, k(RLX, -1.1f, LLX, -0.6f, LEAN, -4),
                k(RLX, -1.7f, RLZ, 0.2f, LLX, 0.6f, LEAN, -22, RAZ, 0.95f, LAZ, -0.95f, RAX, -0.3f, LAX, -0.3f));
        h(AnimPose.DIVE_KICK, k(RLX, -0.8f, LEAN, 10), k(RLX, -0.6f, LLX, 0.4f, LEAN, 36));
        // ---------------- specials
        h(AnimPose.THROW, k(RAX, -2.85f, RAZ, 0.45f, RAY, 0.2f, BY, 0.65f, LEAN, -9),
                k(RAX, -1.3f, RAZ, 0, RAY, -0.2f, BY, -0.55f, LEAN, 12, FWD, 0.08f));
        h(AnimPose.SHOOT, k(RAX, -1.57f, RAY, -0.2f, RAZ, 0, LAX, -1.57f, LAY, 0.65f, LAZ, 0, HX, 0),
                k(RAX, -1.57f, RAY, -0.15f, LAX, -1.57f, LAY, 0.45f, LEAN, -5, FWD, -0.04f));
        h(AnimPose.SHOOT_UP, k(RAX, -2.3f, LAX, -2.3f, LAY, 0.55f, HX, -0.45f, LEAN, -6),
                k(RAX, -2.45f, LAX, -2.45f, LAY, 0.5f, HX, -0.55f, LEAN, -12));
        h(AnimPose.LUNGE, k(LEAN, -10, SQUASH, 0.9f, RAX, -0.9f, LAX, -0.9f),
                k(RAX, -1.6f, LAX, -1.5f, RAY, -0.15f, LAY, 0.15f, LEAN, 26, FWD, 0.2f, RLX, -0.8f, LLX, 0.7f));
        h(AnimPose.CHARGE, k(LEAN, -8, HX, -0.3f),
                k(LEAN, 30, HX, 0.5f, RAX, 0.35f, LAX, 0.35f, RAY, 0, LAY, 0, RLX, -0.9f, LLX, 0.8f, FWD, 0.1f));
        h(AnimPose.SLIDE, k(SQUASH, 0.85f, LEAN, -10),
                k(LEAN, -45, SQUASH, 0.75f, RLX, -1.4f, LLX, -0.2f, RAX, -0.3f, LAX, 0.4f, FWD, 0.1f));
        h(AnimPose.BACKFLIP, k(SQUASH, 0.85f), k(RLX, -1.2f, LLX, -1.0f, RAX, -2.4f, LAX, -2.4f));
        h(AnimPose.GRAB, k(RAX, -1.3f, LAX, -1.3f, RAY, 0.35f, LAY, -0.35f, LEAN, -4),
                k(RAX, -1.5f, LAX, -1.5f, RAY, -0.45f, LAY, 0.45f, LEAN, 18, FWD, 0.2f));
        h(AnimPose.THROW_FWD, k(RAX, -1.5f, LAX, -1.5f, RAY, -0.4f, LAY, 0.4f, LEAN, 14),
                k(RAX, -2.9f, LAX, -2.9f, RAY, 0, LAY, 0, LEAN, -16, HX, -0.4f));
        h(AnimPose.THROW_BACK, k(RAX, -1.5f, LAX, -1.5f, LEAN, 10),
                k(RAX, -2.3f, LAX, -2.3f, LEAN, -20, SPIN, 180, HX, -0.4f));
        h(AnimPose.SLAM, k(RAX, -3.0f, LAX, -2.8f, HX, -0.45f, LEAN, -12),
                k(RAX, -0.5f, LAX, -0.7f, HX, 0.45f, LEAN, 24, FWD, 0.15f));
        h(AnimPose.STOMP, k(RLX, -1.65f, LEAN, -8, UP, 0.06f, RAX, -0.6f, RAZ, 0.5f),
                k(RLX, 0.35f, LEAN, 10, SQUASH, 0.88f, RAX, -0.8f, RAZ, 0.3f));
        h(AnimPose.TAUNT, k(RAX, -2.8f, RAZ, -0.3f, LAX, -2.8f, LAZ, 0.3f, HX, -0.4f),
                k(RAX, -2.9f, RAZ, -0.35f, LAX, -2.9f, LAZ, 0.35f, HX, -0.45f, UP, 0.05f));
        h(AnimPose.RUSH, k(LEAN, -6, RAX, -0.9f, LAX, -0.9f),
                k(RAX, -1.6f, LAX, -1.6f, LEAN, 14, FWD, 0.1f, BY, 0));
        h(AnimPose.DRIVE_IMPACT, k(RAX, 0.6f, RAY, 0.4f, BY, 0.95f, LEAN, -14, SQUASH, 0.9f, LAX, -1.6f),
                k(RAX, -1.66f, RAY, -0.4f, RAZ, 0, BY, -0.95f, LEAN, 24, FWD, 0.25f, RLX, -0.2f, LLX, 0.7f));
        h(AnimPose.SUMMON, k(RAX, -2.0f, LAX, -2.0f, HX, -0.3f, SQUASH, 0.92f),
                k(RAX, -3.0f, LAX, -3.0f, RAZ, -0.45f, LAZ, 0.45f, HX, -0.6f, LEAN, -10, UP, 0.08f));
        h(AnimPose.BELLY_FLOP, k(SQUASH, 0.85f, RAX, -2.5f, LAX, -2.5f),
                k(LEAN, 70, RAX, -2.8f, LAX, -2.8f, RLX, 0.4f, LLX, 0.4f));
        h(AnimPose.DASH, k(LEAN, 18, RAX, 0.6f, LAX, -1.4f, RLX, -0.9f, LLX, 0.7f, SQUASH, 0.92f),
                k(LEAN, 24, RAX, 0.9f, LAX, -1.5f, RLX, -1.1f, LLX, 0.9f, FWD, 0.05f));
        h(AnimPose.BACKDASH, k(LEAN, -10, SQUASH, 0.88f, RAX, -1.6f, LAX, -1.6f),
                k(LEAN, -16, RAX, -0.9f, LAX, -0.9f, RAZ, 0.5f, LAZ, -0.5f, RLX, -0.6f, LLX, 0.4f, UP, 0.04f));
        h(AnimPose.PECK, k(HX, -0.65f, LEAN, -12, RAX, -0.8f, LAX, -0.8f),
                k(HX, 0.6f, LEAN, 24, FWD, 0.18f, RAX, 0.45f, LAX, 0.45f, RAY, 0, LAY, 0));
        h(AnimPose.FLAP, k(RAX, -0.3f, RAZ, 1.6f, LAX, -0.3f, LAZ, -1.6f, LEAN, -6, UP, 0.04f),
                k(RAX, -1.4f, RAZ, 0.1f, LAX, -1.4f, LAZ, -0.1f, LEAN, 14, FWD, 0.1f));
        h(AnimPose.DRINK, k(RAX, -2.0f, RAY, -0.5f, RAZ, 0.3f, HX, -0.2f, LAX, -0.9f),
                k(RAX, -2.5f, RAY, -0.7f, RAZ, 0.45f, HX, -0.75f, LEAN, -8, LAX, -0.6f, UP, 0.03f));
        h(AnimPose.SCREAM, k(HX, 0.35f, LEAN, 8, SQUASH, 0.92f, RAX, -0.6f, LAX, -0.6f),
                k(HX, -0.7f, LEAN, -14, RAX, -0.8f, RAZ, 1.0f, LAX, -0.8f, LAZ, -1.0f, UP, 0.05f));

        // ---------------- four-legged bodies: RAX = front legs, RLX = hind legs, HX = head
        float[] snoutWind = k(HX, -0.45f, LEAN, -6);
        float[] snoutHit = k(HX, 0.6f, LEAN, 10, FWD, 0.12f);
        for (AnimPose p : new AnimPose[]{AnimPose.JAB, AnimPose.STRAIGHT, AnimPose.HEADBUTT, AnimPose.PALM,
                AnimPose.LOW_JAB, AnimPose.BODY_BLOW, AnimPose.GRAB, AnimPose.THROW_FWD, AnimPose.AIR_PUNCH, AnimPose.RUSH}) {
            q(p, snoutWind, snoutHit);
        }
        float[] rearUp = k(RAX, -1.25f, LEAN, -20, HX, -0.4f, UP, 0.04f);
        float[] slamDown = k(RAX, 0.45f, RLX, -0.3f, LEAN, 9, SQUASH, 0.88f, HX, 0.35f);
        for (AnimPose p : new AnimPose[]{AnimPose.HOOK, AnimPose.OVERHEAD, AnimPose.STOMP, AnimPose.SLAM,
                AnimPose.AIR_SLAM, AnimPose.DRIVE_IMPACT, AnimPose.THROW}) {
            q(p, rearUp, slamDown);
        }
        float[] kickWind = k(RLX, 0.55f, LEAN, -4);
        float[] kickHit = k(RLX, -1.15f, LEAN, 14, HX, 0.3f, RAX, 0.2f);
        for (AnimPose p : new AnimPose[]{AnimPose.SHIN_KICK, AnimPose.FRONT_KICK, AnimPose.ROUNDHOUSE, AnimPose.LOW_KICK,
                AnimPose.POKE_KICK, AnimPose.AXE_KICK, AnimPose.SPIN_KICK, AnimPose.AIR_KICK, AnimPose.FLYING_KICK,
                AnimPose.DIVE_KICK, AnimPose.THROW_BACK}) {
            q(p, kickWind, kickHit);
        }
        q(AnimPose.SWEEP, k(SQUASH, 0.8f), k(SQUASH, 0.7f, RAX, -1.2f, RLX, 1.2f, FWD, 0.1f, SPIN, -40));
        q(AnimPose.SLIDE, k(SQUASH, 0.85f), k(SQUASH, 0.7f, RAX, -1.3f, RLX, 1.3f, FWD, 0.12f, HX, 0.3f));
        q(AnimPose.CHARGE, k(LEAN, -8, HX, -0.35f, SQUASH, 0.92f), k(HX, 0.75f, LEAN, 8, FWD, 0.08f));
        q(AnimPose.LUNGE, k(LEAN, -8, HX, -0.35f), k(HX, 0.7f, LEAN, 10, FWD, 0.14f));
        q(AnimPose.ANTI_AIR, k(SQUASH, 0.84f, HX, 0.45f), k(LEAN, -38, HX, -0.85f, RAX, -1.0f, UP, 0.05f));
        q(AnimPose.UPPERCUT, k(SQUASH, 0.84f, HX, 0.45f), k(LEAN, -38, HX, -0.85f, RAX, -1.0f, UP, 0.05f));
        q(AnimPose.BELLY_FLOP, k(SQUASH, 0.85f, RAX, -0.8f, RLX, 0.8f), k(RAX, -1.35f, RLX, 1.35f, LEAN, 6, HX, 0.4f));
        q(AnimPose.TAUNT, k(HX, -0.6f, LEAN, -16), k(HX, -0.8f, LEAN, -24, UP, 0.06f));
        q(AnimPose.SUMMON, k(HX, -0.6f, LEAN, -16), k(HX, -0.8f, LEAN, -26, RAX, -1.2f, UP, 0.06f));
        q(AnimPose.SHOOT, snoutWind, snoutHit);
        q(AnimPose.SHOOT_UP, k(HX, -0.5f), k(HX, -0.8f, LEAN, -12));
        q(AnimPose.BACKFLIP, k(SQUASH, 0.85f), k(RAX, -0.8f, RLX, 0.8f));
        q(AnimPose.PECK, snoutWind, snoutHit);
        q(AnimPose.DASH, k(LEAN, 8, RAX, -0.9f, RLX, 0.9f, HX, 0.25f), k(LEAN, 10, RAX, -1.1f, RLX, 1.1f, HX, 0.3f));
        q(AnimPose.BACKDASH, k(LEAN, -12, RAX, -0.8f, HX, -0.3f), k(LEAN, -18, RAX, -1.0f, RLX, 0.6f, HX, -0.4f, UP, 0.04f));
        q(AnimPose.FLAP, rearUp, slamDown);
        q(AnimPose.DRINK, k(HX, -0.6f, LEAN, -10), k(HX, -0.9f, LEAN, -16));
        q(AnimPose.SCREAM, k(HX, 0.4f, LEAN, 6, SQUASH, 0.9f), k(HX, -0.75f, LEAN, -20, RAX, -0.5f, UP, 0.05f));

        // ---------------- chicken
        float[] peckWind = k(HX, -0.75f, LEAN, -16, RAZ, 0.3f);
        float[] peckHit = k(HX, 1.0f, LEAN, 30, FWD, 0.14f, RAZ, 0.5f);
        b(peckWind, peckHit, AnimPose.JAB, AnimPose.STRAIGHT, AnimPose.PECK, AnimPose.LOW_JAB, AnimPose.BODY_BLOW,
                AnimPose.HEADBUTT, AnimPose.PALM, AnimPose.AIR_PUNCH, AnimPose.GRAB, AnimPose.THROW_FWD, AnimPose.RUSH,
                AnimPose.SHOOT, AnimPose.CHARGE, AnimPose.LUNGE);
        float[] wingWind = k(RAZ, 1.5f, LEAN, -10, UP, 0.05f, HX, -0.3f);
        float[] wingHit = k(RAZ, -0.3f, LEAN, 18, FWD, 0.12f, HX, 0.4f);
        b(wingWind, wingHit, AnimPose.FLAP, AnimPose.HOOK, AnimPose.OVERHEAD, AnimPose.SLAM, AnimPose.AIR_SLAM,
                AnimPose.THROW, AnimPose.DRIVE_IMPACT, AnimPose.SUMMON);
        float[] talonWind = k(RLX, 0.7f, LLX, -0.2f, LEAN, -10, RAZ, 0.8f);
        float[] talonHit = k(RLX, -1.4f, LLX, 0.3f, LEAN, -22, FWD, 0.1f, RAZ, 1.2f, HX, -0.3f);
        b(talonWind, talonHit, AnimPose.SHIN_KICK, AnimPose.FRONT_KICK, AnimPose.ROUNDHOUSE, AnimPose.LOW_KICK,
                AnimPose.POKE_KICK, AnimPose.AXE_KICK, AnimPose.SPIN_KICK, AnimPose.AIR_KICK, AnimPose.FLYING_KICK,
                AnimPose.THROW_BACK, AnimPose.STOMP);
        b(k(SQUASH, 0.8f, RAZ, 0.6f), k(SQUASH, 0.7f, RLX, -1.5f, LLX, 0.5f, LEAN, -30, FWD, 0.12f, RAZ, 1.3f),
                AnimPose.SWEEP, AnimPose.SLIDE);
        b(k(RAZ, 1.4f, LEAN, -20), k(RAZ, 0.4f, LEAN, 40, HX, 0.6f, RLX, -1.2f, LLX, -1.2f), AnimPose.DIVE_KICK,
                AnimPose.BELLY_FLOP);
        b(k(SQUASH, 0.78f, HX, 0.4f, RAZ, 0.2f), k(LEAN, -40, HX, -1.0f, RAZ, 1.6f, UP, 0.1f, RLX, 0.6f, LLX, 0.6f),
                AnimPose.ANTI_AIR, AnimPose.UPPERCUT);
        b(k(HX, 0.3f, LEAN, 6, RAZ, 0.5f), k(HX, -0.9f, LEAN, -22, RAZ, 1.6f, UP, 0.06f), AnimPose.SCREAM,
                AnimPose.TAUNT, AnimPose.SHOOT_UP, AnimPose.DRINK);
        b(k(SQUASH, 0.85f), k(RAZ, 1.6f, RLX, -0.8f, LLX, -0.8f), AnimPose.BACKFLIP);
        b(k(LEAN, 20, RAZ, 0.6f, HX, 0.3f), k(LEAN, 26, RAZ, 0.9f, HX, 0.4f, RLX, -0.8f, LLX, 0.8f), AnimPose.DASH);
        b(k(LEAN, -12, RAZ, 1.2f), k(LEAN, -18, RAZ, 1.5f, UP, 0.05f), AnimPose.BACKDASH);
    }

    public static Clip humanoid(AnimPose p) {
        return HUMANOID.getOrDefault(p, HUMANOID.get(AnimPose.JAB));
    }

    public static Clip bird(AnimPose p) {
        return BIRD.getOrDefault(p, BIRD.get(AnimPose.PECK));
    }

    public static Clip quadruped(AnimPose p) {
        return QUAD.getOrDefault(p, QUAD.get(AnimPose.JAB));
    }

    private AnimClips() {
    }
}
